// ABOUTME: In-process scripted RESP2 server for tests: canned replies + severable sockets.
// ABOUTME: Shared by queue reconnect and publish-pool tests to drive ActionEventQueue.

#pragma once

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

#include <asio.hpp>

namespace deltafi::test {

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
        acceptor_.non_blocking(true);
        port_ = acceptor_.local_endpoint().port();
        thread_ = std::thread([this] { run(); });
    }

    ~FakeRespServer() {
        stop_.store(true);
        drop_all_sockets();
        // The accept thread owns the acceptor and each client thread owns its
        // socket; joining lets them close their own resources. The test thread
        // never touches a socket or the acceptor directly.
        if (thread_.joinable()) thread_.join();
        for (auto& t : client_threads_) {
            if (t.joinable()) t.join();
        }
    }

    std::string url() const { return "127.0.0.1:" + std::to_string(port_); }

    // Signal the most recently accepted connection to drop. Its client thread
    // notices the flag, closes its own socket, and exits; the client under test
    // then sees the disconnect and reconnects. The test thread only sets the
    // flag — it never touches the socket.
    void drop_active_socket() {
        std::lock_guard lock(mu_);
        if (active_.drop) active_.drop->store(true);
    }

    void drop_all_sockets() {
        std::lock_guard lock(mu_);
        for (auto& c : conns_) c.drop->store(true);
    }

    // Block until a fresh client has connected after a drop.
    void wait_for_new_connection(int previous_count, std::chrono::milliseconds timeout) {
        std::unique_lock lock(mu_);
        cv_.wait_for(lock, timeout, [&] { return connections_accepted_ > previous_count; });
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
    // A connection: the client socket (touched only by its own client thread)
    // plus a drop flag the test thread sets to force a disconnect. Because the
    // test thread never touches the socket, each socket is owned by exactly one
    // thread (Asio forbids concurrent operations on a single socket).
    struct Conn {
        std::shared_ptr<asio::ip::tcp::socket> sock;
        std::shared_ptr<std::atomic<bool>> drop = std::make_shared<std::atomic<bool>>(false);
    };

    // Create a socket on its own private io_context, tying the context's
    // lifetime to the socket, so no two client threads share reactor state.
    static std::shared_ptr<asio::ip::tcp::socket> make_private_socket() {
        auto io = std::make_shared<asio::io_context>();
        return std::shared_ptr<asio::ip::tcp::socket>(new asio::ip::tcp::socket(*io),
                                                      [io](asio::ip::tcp::socket* s) { delete s; });
    }

    void run() {
        while (!stop_.load()) {
            auto sock = make_private_socket();
            asio::error_code ec;
            acceptor_.accept(*sock, ec);
            if (ec == asio::error::would_block) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                continue;
            }
            if (ec || stop_.load()) break;
            sock->non_blocking(true);
            Conn conn{sock, std::make_shared<std::atomic<bool>>(false)};
            {
                std::lock_guard lock(mu_);
                active_ = conn;
                conns_.push_back(conn);
                connections_accepted_++;
                cv_.notify_all();
            }
            client_threads_.emplace_back([this, conn] { handle_client(conn); });
        }
        // The accept thread owns the acceptor, so it closes it here.
        asio::error_code ec;
        acceptor_.close(ec);
    }

    void handle_client(Conn conn) {
        auto& sock = *conn.sock;
        std::string buf;
        while (!stop_.load() && !conn.drop->load()) {
            auto cmd = read_command(sock, buf, *conn.drop);
            if (cmd.empty()) break;
            {
                std::lock_guard lock(mu_);
                commands_.push_back(cmd);
                cv_.notify_all();
            }
            if (!write_all(sock, reply_for(cmd), *conn.drop)) break;
        }
        // The client thread owns the socket, so it closes it here.
        asio::error_code ec;
        sock.close(ec);
    }

    // Read from a non-blocking socket, yielding until data arrives. Returns the
    // byte count, or nullopt on stop/drop/error/EOF.
    std::optional<size_t> poll_read(asio::ip::tcp::socket& sock, char* tmp, size_t cap,
                                    std::atomic<bool>& drop) {
        while (!stop_.load() && !drop.load()) {
            asio::error_code ec;
            auto n = sock.read_some(asio::buffer(tmp, cap), ec);
            if (!ec && n > 0) return n;
            if (ec == asio::error::would_block) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                continue;
            }
            return std::nullopt;
        }
        return std::nullopt;
    }

    // Write all bytes to a non-blocking socket. Returns false on stop/drop/error.
    bool write_all(asio::ip::tcp::socket& sock, const std::string& data, std::atomic<bool>& drop) {
        size_t off = 0;
        while (off < data.size() && !stop_.load() && !drop.load()) {
            asio::error_code ec;
            auto n = sock.write_some(asio::buffer(data.data() + off, data.size() - off), ec);
            if (!ec) {
                off += n;
                continue;
            }
            if (ec == asio::error::would_block) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
                continue;
            }
            return false;
        }
        return off == data.size();
    }

    // Read one RESP2 array of bulk strings. Returns empty vector on EOF/drop.
    std::vector<std::string> read_command(asio::ip::tcp::socket& sock, std::string& buf,
                                          std::atomic<bool>& drop) {
        auto read_line = [&]() -> std::optional<std::string> {
            while (true) {
                auto pos = buf.find("\r\n");
                if (pos != std::string::npos) {
                    auto line = buf.substr(0, pos);
                    buf.erase(0, pos + 2);
                    return line;
                }
                char tmp[1024];
                auto n = poll_read(sock, tmp, sizeof(tmp), drop);
                if (!n) return std::nullopt;
                buf.append(tmp, *n);
            }
        };
        auto header = read_line();
        if (!header || header->empty() || (*header)[0] != '*') return {};
        int count = std::stoi(header->substr(1));
        std::vector<std::string> args;
        args.reserve(count);
        for (int i = 0; i < count; ++i) {
            auto len_line = read_line();
            if (!len_line || len_line->empty() || (*len_line)[0] != '$') return {};
            int len = std::stoi(len_line->substr(1));
            while (static_cast<int>(buf.size()) < len + 2) {
                char tmp[1024];
                auto n = poll_read(sock, tmp, sizeof(tmp), drop);
                if (!n) return {};
                buf.append(tmp, *n);
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
        if (name == "BZPOPMIN") return "$-1\r\n";  // timeout
        return "+OK\r\n";
    }

    asio::io_context io_;
    asio::ip::tcp::acceptor acceptor_;
    uint16_t port_ = 0;
    std::thread thread_;
    std::atomic<bool> stop_{false};

    mutable std::mutex mu_;
    std::condition_variable cv_;
    Conn active_;
    std::vector<Conn> conns_;
    std::vector<std::thread> client_threads_;
    int connections_accepted_ = 0;
    std::vector<std::vector<std::string>> commands_;
};

}  // namespace deltafi::test
