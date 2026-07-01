// ABOUTME: Tests that ActionEventQueue rebuilds its Redis connection after a drop.
// ABOUTME: Uses an in-process RESP server that scripts replies and can sever sockets.

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <asio.hpp>
#include <catch2/catch_test_macros.hpp>

#include "deltafi/content.hpp"
#include "deltafi/heartbeat.hpp"
#include "deltafi/lookup.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/testing.hpp"
#include "deltafi/worker.hpp"

namespace {

// Minimal scripted RESP2 server for tests. Accepts one client at a time on
// 127.0.0.1:ephemeral, parses inbound arrays, and emits canned replies for
// PING, AUTH, ZADD, BZPOPMIN. Supports forcing the active socket closed.
class FakeRespServer {
public:
    FakeRespServer() : acceptor_(io_) {
        asio::ip::tcp::endpoint ep(asio::ip::make_address("127.0.0.1"), 0);
        acceptor_.open(ep.protocol());
        acceptor_.set_option(asio::socket_base::reuse_address(true));
        acceptor_.bind(ep);
        acceptor_.listen();
        port_ = acceptor_.local_endpoint().port();
        thread_ = std::thread([this] { run(); });
    }

    ~FakeRespServer() {
        stop_.store(true);
        asio::error_code ec;
        acceptor_.close(ec);
        drop_all_sockets();
        if (thread_.joinable()) thread_.join();
        for (auto& t : client_threads_) {
            if (t.joinable()) t.join();
        }
    }

    std::string url() const { return "127.0.0.1:" + std::to_string(port_); }

    // Force-close the most recently accepted client socket. The next client
    // command on it will fail; the server's accept loop will pick up a new
    // connection if the client reconnects.
    void drop_active_socket() {
        std::lock_guard lock(mu_);
        if (active_socket_ && active_socket_->is_open()) {
            asio::error_code ec;
            active_socket_->shutdown(asio::ip::tcp::socket::shutdown_both, ec);
            active_socket_->close(ec);
        }
    }

    void drop_all_sockets() {
        std::lock_guard lock(mu_);
        for (auto& s : all_sockets_) {
            if (s && s->is_open()) {
                asio::error_code ec;
                s->shutdown(asio::ip::tcp::socket::shutdown_both, ec);
                s->close(ec);
            }
        }
    }

    // Block until a fresh client has connected after a drop.
    void wait_for_new_connection(int previous_count,
                                 std::chrono::milliseconds timeout) {
        std::unique_lock lock(mu_);
        cv_.wait_for(lock, timeout,
                     [&] { return connections_accepted_ > previous_count; });
    }

    int connections_accepted() const {
        std::lock_guard lock(mu_);
        return connections_accepted_;
    }

    std::vector<std::vector<std::string>> commands_received() const {
        std::lock_guard lock(mu_);
        return commands_;
    }

    // Count commands with the given name received so far.
    int count_commands(const std::string& name) const {
        std::lock_guard lock(mu_);
        int n = 0;
        for (const auto& c : commands_) {
            if (!c.empty() && c[0] == name) ++n;
        }
        return n;
    }

    // Block until the count of commands with the given name exceeds threshold.
    void wait_for_command_count(const std::string& name, int threshold,
                                std::chrono::milliseconds timeout) {
        std::unique_lock lock(mu_);
        cv_.wait_for(lock, timeout, [&] {
            int n = 0;
            for (const auto& c : commands_) {
                if (!c.empty() && c[0] == name) ++n;
            }
            return n > threshold;
        });
    }

private:
    void run() {
        while (!stop_.load()) {
            asio::error_code ec;
            auto sock = std::make_shared<asio::ip::tcp::socket>(io_);
            acceptor_.accept(*sock, ec);
            if (ec || stop_.load()) return;
            {
                std::lock_guard lock(mu_);
                active_socket_ = sock;
                all_sockets_.push_back(sock);
                connections_accepted_++;
                cv_.notify_all();
            }
            client_threads_.emplace_back([this, sock] {
                handle_client(*sock);
            });
        }
    }

    void handle_client(asio::ip::tcp::socket& sock) {
        std::string buf;
        while (sock.is_open() && !stop_.load()) {
            auto cmd = read_command(sock, buf);
            if (cmd.empty()) return;
            {
                std::lock_guard lock(mu_);
                commands_.push_back(cmd);
                cv_.notify_all();
            }
            auto reply = reply_for(cmd);
            asio::error_code ec;
            asio::write(sock, asio::buffer(reply), ec);
            if (ec) return;
        }
    }

    // Read one RESP2 array of bulk strings. Returns empty vector on EOF.
    std::vector<std::string> read_command(asio::ip::tcp::socket& sock,
                                          std::string& buf) {
        auto read_line = [&]() -> std::optional<std::string> {
            while (true) {
                auto pos = buf.find("\r\n");
                if (pos != std::string::npos) {
                    auto line = buf.substr(0, pos);
                    buf.erase(0, pos + 2);
                    return line;
                }
                char tmp[1024];
                asio::error_code ec;
                auto n = sock.read_some(asio::buffer(tmp), ec);
                if (ec || n == 0) return std::nullopt;
                buf.append(tmp, n);
            }
        };
        auto header = read_line();
        if (!header || header->empty() || (*header)[0] != '*') return {};
        int count = std::stoi(header->substr(1));
        std::vector<std::string> args;
        args.reserve(count);
        for (int i = 0; i < count; ++i) {
            auto len_line = read_line();
            if (!len_line || len_line->empty() || (*len_line)[0] != '$')
                return {};
            int len = std::stoi(len_line->substr(1));
            while (static_cast<int>(buf.size()) < len + 2) {
                char tmp[1024];
                asio::error_code ec;
                auto n = sock.read_some(asio::buffer(tmp), ec);
                if (ec || n == 0) return {};
                buf.append(tmp, n);
            }
            args.push_back(buf.substr(0, len));
            buf.erase(0, len + 2);
        }
        return args;
    }

    std::string reply_for(const std::vector<std::string>& cmd) {
        if (cmd.empty()) return "+OK\r\n";
        const auto& name = cmd[0];
        if (name == "PING") return "+PONG\r\n";
        if (name == "AUTH") return "+OK\r\n";
        if (name == "ZADD") return ":1\r\n";
        if (name == "HSET") return ":1\r\n";
        if (name == "HDEL") return ":1\r\n";
        if (name == "BZPOPMIN") return "$-1\r\n"; // timeout
        return "+OK\r\n";
    }

    asio::io_context io_;
    asio::ip::tcp::acceptor acceptor_;
    uint16_t port_ = 0;
    std::thread thread_;
    std::atomic<bool> stop_{false};

    mutable std::mutex mu_;
    std::condition_variable cv_;
    std::shared_ptr<asio::ip::tcp::socket> active_socket_;
    std::vector<std::shared_ptr<asio::ip::tcp::socket>> all_sockets_;
    std::vector<std::thread> client_threads_;
    int connections_accepted_ = 0;
    std::vector<std::vector<std::string>> commands_;
};

// Minimal supplier that returns no rows; used only to drive the runner's poll loop.
class NoopSupplier : public deltafi::LookupTableSupplierBase {
public:
    explicit NoopSupplier(std::string name) : name_(std::move(name)) {}

    deltafi::LookupTableDefinition table() const override {
        deltafi::LookupTableDefinition def;
        def.name = name_;
        return def;
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>&,
        const std::map<std::string, std::vector<std::string>>&,
        const std::vector<std::string>&) override {
        return {};
    }

private:
    std::string name_;
};

} // namespace

using namespace std::chrono_literals;

TEST_CASE("ActionEventQueue.put reconnects after the server drops the socket",
          "[queue][reconnect]") {
    FakeRespServer server;

    deltafi::ActionEventQueue queue(server.url(), "");
    REQUIRE(server.connections_accepted() == 1);

    // Baseline: first put succeeds.
    queue.put("dgs", "first-payload");
    REQUIRE(server.commands_received().size() >= 2); // PING + ZADD

    // Server severs the existing connection.
    int before = server.connections_accepted();
    server.drop_active_socket();

    // Second put must rebuild the connection and succeed.
    queue.put("dgs", "second-payload");

    server.wait_for_new_connection(before, 2s);
    REQUIRE(server.connections_accepted() == before + 1);

    bool saw_second_zadd = false;
    for (const auto& cmd : server.commands_received()) {
        if (cmd.size() >= 4 && cmd[0] == "ZADD" && cmd[3] == "second-payload") {
            saw_second_zadd = true;
        }
    }
    REQUIRE(saw_second_zadd);
}

TEST_CASE("ActionWorker poller reconnects after its socket is dropped",
          "[worker][reconnect]") {
    FakeRespServer server;
    deltafi::ActionEventQueue queue(server.url(), "");
    deltafi::testing::InMemoryContentStore content;
    deltafi::InFlightTracker tracker;
    auto executor = [](deltafi::ActionContext&, deltafi::QueueEvent&) {
        return deltafi::ActionEvent{};
    };

    deltafi::ActionWorker worker("TestAction", executor, queue, content,
                                  tracker, "test-app", /*worker_count=*/1);
    worker.start();

    // Wait until the poller has actually started issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", 0, 2s);
    int bzpops_before = server.count_commands("BZPOPMIN");
    REQUIRE(bzpops_before >= 1);

    // Drop the poller's connection mid-flight.
    server.drop_active_socket();

    // Poller must reconnect and resume issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", bzpops_before, 5s);
    int bzpops_after = server.count_commands("BZPOPMIN");

    worker.request_stop();
    worker.join();

    REQUIRE(bzpops_after > bzpops_before);
}

TEST_CASE("LookupTableSupplierRunner reconnects after its socket is dropped",
          "[lookup][reconnect]") {
    FakeRespServer server;
    deltafi::ActionEventQueue queue(server.url(), "");

    std::map<std::string, std::shared_ptr<deltafi::LookupTableSupplierBase>> suppliers;
    suppliers["my-table"] = std::make_shared<NoopSupplier>("my-table");

    deltafi::LookupTableSupplierRunner runner(queue, suppliers);
    runner.start();

    // Wait until the runner has actually started issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", 0, 2s);
    int bzpops_before = server.count_commands("BZPOPMIN");
    REQUIRE(bzpops_before >= 1);

    // Drop the runner's connection mid-flight.
    server.drop_active_socket();

    // Runner must reconnect and resume issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", bzpops_before, 5s);
    int bzpops_after = server.count_commands("BZPOPMIN");

    runner.request_stop();
    runner.join();

    REQUIRE(bzpops_after > bzpops_before);
}
