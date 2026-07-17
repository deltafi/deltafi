// ABOUTME: RESP2 protocol client for Redis/Valkey communication over TCP.
// ABOUTME: Provides RespConnection with blocking commands (BZPOPMIN, ZADD, HSET) on Asio sockets.

#pragma once

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <functional>
#include <optional>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <variant>
#include <vector>

#include <asio.hpp>
#include <spdlog/spdlog.h>

namespace deltafi::internal {

// RESP2 reply types returned by the protocol parser.
struct RespError {
    std::string message;
};

using RespReply = std::variant<std::string,               // Simple string or bulk string
                               int64_t,                   // Integer
                               RespError,                 // Error
                               std::vector<std::string>,  // Array of bulk strings
                               std::monostate             // Nil
                               >;

// Encode a RESP2 command as a bulk string array: *N\r\n$len\r\narg\r\n...
inline std::string resp_encode(const std::vector<std::string>& args) {
    std::string buf;
    buf.reserve(256);
    buf += '*';
    buf += std::to_string(args.size());
    buf += "\r\n";
    for (const auto& arg : args) {
        buf += '$';
        buf += std::to_string(arg.size());
        buf += "\r\n";
        buf += arg;
        buf += "\r\n";
    }
    return buf;
}

// Parse host:port from a URL-like string, stripping scheme prefixes.
// Defaults port to 6379 if not specified.
inline std::pair<std::string, uint16_t> parse_host_port(const std::string& addr) {
    std::string s = addr;
    if (auto pos = s.find("://"); pos != std::string::npos) {
        s = s.substr(pos + 3);
    }
    // Reverse scan for port separator
    for (auto i = static_cast<int>(s.size()) - 1; i >= 0; --i) {
        if (s[static_cast<size_t>(i)] == ':') {
            auto host = s.substr(0, static_cast<size_t>(i));
            auto port_str = s.substr(static_cast<size_t>(i) + 1);
            uint16_t port = 6379;
            try {
                port = static_cast<uint16_t>(std::stoi(port_str));
            } catch (...) {  // NOLINT(bugprone-empty-catch): use default port
            }
            return {host, port};
        }
    }
    return {s, 6379};
}

// RESP2 protocol reader that buffers TCP data and parses replies.
class RespReader {
public:
    explicit RespReader(asio::ip::tcp::socket& sock) : sock_(sock) {}

    // Read a complete RESP2 reply from the socket.
    RespReply read_reply() {
        char type = read_byte();
        switch (type) {
            case '+':
                return read_simple_string();
            case '-':
                return RespError{read_line()};
            case ':':
                return read_integer();
            case '$':
                return read_bulk_string();
            case '*':
                return read_array();
            default:
                throw std::runtime_error(std::string("unexpected RESP type: ") + type);
        }
    }

private:
    asio::ip::tcp::socket& sock_;
    std::string buf_;
    size_t pos_ = 0;

    void ensure_data(size_t need) {
        while (pos_ + need > buf_.size()) {
            std::array<char, 4096> tmp{};
            asio::error_code ec;
            auto n = sock_.read_some(asio::buffer(tmp), ec);
            if (ec) {
                throw std::runtime_error("RESP read error: " + ec.message());
            }
            buf_.append(tmp.data(), n);
        }
    }

    char read_byte() {
        ensure_data(1);
        return buf_[pos_++];
    }

    std::string read_line() {
        std::string line;
        while (true) {
            ensure_data(1);
            char c = buf_[pos_++];
            if (c == '\r') {
                ensure_data(1);
                pos_++;  // skip \n
                break;
            }
            line += c;
        }
        compact();
        return line;
    }

    std::string read_simple_string() { return read_line(); }

    int64_t read_integer() {
        auto line = read_line();
        return std::stoll(line);
    }

    RespReply read_bulk_string() {
        auto len_str = read_line();
        auto len = std::stoll(len_str);
        if (len < 0) {
            return std::monostate{};
        }
        ensure_data(static_cast<size_t>(len) + 2);  // data + \r\n
        std::string data = buf_.substr(pos_, static_cast<size_t>(len));
        pos_ += static_cast<size_t>(len) + 2;
        compact();
        return data;
    }

    RespReply read_array() {
        auto count_str = read_line();
        auto count = std::stoll(count_str);
        if (count < 0) {
            return std::monostate{};
        }
        std::vector<std::string> result;
        result.reserve(static_cast<size_t>(count));
        for (int64_t i = 0; i < count; ++i) {
            auto elem = read_reply();
            if (auto* s = std::get_if<std::string>(&elem)) {
                result.push_back(std::move(*s));
            } else if (auto* n = std::get_if<int64_t>(&elem)) {
                result.push_back(std::to_string(*n));
            } else if (std::holds_alternative<std::monostate>(elem)) {
                result.emplace_back();
            } else if (auto* e = std::get_if<RespError>(&elem)) {
                throw std::runtime_error("error in array: " + e->message);
            }
        }
        return result;
    }

    void compact() {
        if (pos_ > 4096) {
            buf_.erase(0, pos_);
            pos_ = 0;
        }
    }
};

// TCP connection to a Redis/Valkey server speaking RESP2 protocol.
// Each instance is single-threaded; callers create one per thread.
class RespConnection {
public:
    RespConnection(const std::string& host, uint16_t port, const std::string& password = "")
        : host_(host), port_(port), password_(password), socket_(io_ctx_) {
        connect();
    }

    ~RespConnection() {
        try {
            close();
        } catch (...) {  // NOLINT(bugprone-empty-catch): destructor must not throw
        }
    }

    RespConnection(const RespConnection&) = delete;
    RespConnection& operator=(const RespConnection&) = delete;
    RespConnection(RespConnection&&) = delete;
    RespConnection& operator=(RespConnection&&) = delete;

    // Send a command and return the reply that has already been parsed.
    RespReply send_command(const std::vector<std::string>& args) {
        auto encoded = resp_encode(args);
        asio::write(socket_, asio::buffer(encoded));
        // NOLINTNEXTLINE(bugprone-unchecked-optional-access) — reader_ is a constructor invariant.
        return reader_->read_reply();
    }

    // AUTH with password.
    void auth(const std::string& password) {
        auto reply = send_command({"AUTH", password});
        if (auto* e = std::get_if<RespError>(&reply)) {
            throw std::runtime_error("AUTH failed: " + e->message);
        }
    }

    // PING to verify connection.
    bool ping() {
        try {
            auto reply = send_command({"PING"});
            if (auto* s = std::get_if<std::string>(&reply)) {
                return *s == "PONG";
            }
        } catch (...) {  // NOLINT(bugprone-empty-catch): treat any failure as ping failure
        }
        return false;
    }

    // BZPOPMIN: blocking pop from sorted set with timeout in seconds.
    // Returns (member, score) or nullopt on timeout/nil.
    std::optional<std::pair<std::string, std::string>> bzpopmin(const std::string& key,
                                                                int timeout_secs) {
        auto reply = send_command({"BZPOPMIN", key, std::to_string(timeout_secs)});
        if (std::holds_alternative<std::monostate>(reply)) {
            return std::nullopt;
        }
        if (auto* arr = std::get_if<std::vector<std::string>>(&reply)) {
            if (arr->size() >= 3) {
                // [key, member, score]
                return std::pair{(*arr)[1], (*arr)[2]};
            }
        }
        return std::nullopt;
    }

    // BZPOPMIN from multiple sorted sets. Returns the member or nullopt.
    std::optional<std::pair<std::string, std::string>> bzpopmin(
        const std::vector<std::string>& keys, int timeout_secs) {
        std::vector<std::string> args = {"BZPOPMIN"};
        args.insert(args.end(), keys.begin(), keys.end());
        args.push_back(std::to_string(timeout_secs));
        auto reply = send_command(args);
        if (std::holds_alternative<std::monostate>(reply)) {
            return std::nullopt;
        }
        if (auto* arr = std::get_if<std::vector<std::string>>(&reply)) {
            if (arr->size() >= 3) {
                return std::pair{(*arr)[1], (*arr)[2]};
            }
        }
        return std::nullopt;
    }

    // ZADD: add member with score to sorted set.
    void zadd(const std::string& key, double score, const std::string& member) {
        auto reply =
            send_command({"ZADD", key, std::to_string(static_cast<int64_t>(score)), member});
        if (auto* e = std::get_if<RespError>(&reply)) {
            throw std::runtime_error("ZADD error: " + e->message);
        }
    }

    // HSET: set hash field.
    void hset(const std::string& key, const std::string& field, const std::string& value) {
        auto reply = send_command({"HSET", key, field, value});
        if (auto* e = std::get_if<RespError>(&reply)) {
            throw std::runtime_error("HSET error: " + e->message);
        }
    }

    // HDEL: delete hash field.
    void hdel(const std::string& key, const std::string& field) {
        auto reply = send_command({"HDEL", key, field});
        if (auto* e = std::get_if<RespError>(&reply)) {
            throw std::runtime_error("HDEL error: " + e->message);
        }
    }

    // SUBSCRIBE: enter subscription mode and call handler for each message.
    // Blocks until stop_requested returns true or connection fails.
    void subscribe(const std::string& channel,
                   const std::function<void(const std::string&)>& handler,
                   const std::function<bool()>& stop_requested) {
        send_command({"SUBSCRIBE", channel});
        // After SUBSCRIBE, server sends subscription confirmation then messages
        while (!stop_requested()) {
            try {
                // NOLINTNEXTLINE(bugprone-unchecked-optional-access) — reader_ invariant.
                auto reply = reader_->read_reply();
                if (auto* arr = std::get_if<std::vector<std::string>>(&reply)) {
                    if (arr->size() >= 3 && (*arr)[0] == "message") {
                        handler((*arr)[2]);
                    }
                }
            } catch (const std::exception& ex) {
                if (!stop_requested()) {
                    spdlog::error("subscribe error: {}", ex.what());
                }
                break;
            }
        }
    }

    void close() {
        asio::error_code ec;
        // NOLINTNEXTLINE(bugprone-unused-return-value): teardown; error is intentionally ignored
        socket_.shutdown(asio::ip::tcp::socket::shutdown_both, ec);
        socket_.close(ec);  // NOLINT(bugprone-unused-return-value): teardown; error ignored
    }

private:
    std::string host_;
    uint16_t port_;
    std::string password_;
    asio::io_context io_ctx_;
    asio::ip::tcp::socket socket_;
    std::optional<RespReader> reader_;

    void connect() {
        asio::ip::tcp::resolver resolver(io_ctx_);
        auto endpoints = resolver.resolve(host_, std::to_string(port_));
        asio::connect(socket_, endpoints);
        reader_.emplace(socket_);

        if (!password_.empty()) {
            auth(password_);
        }
    }
};

// Create a RespConnection from a URL string with optional password.
// Retries with exponential backoff (250ms → 5s cap) on failure.
inline std::unique_ptr<RespConnection> connect_with_retry(const std::string& url,
                                                          const std::string& password,
                                                          int max_attempts = 10) {
    auto [host, port] = parse_host_port(url);

    int delay_ms = 250;
    for (int attempt = 1; attempt <= max_attempts; ++attempt) {
        try {
            return std::make_unique<RespConnection>(host, port, password);
        } catch (const std::exception& ex) {
            if (attempt == max_attempts) {
                throw std::runtime_error("failed to connect to " + url + " after " +
                                         std::to_string(max_attempts) + " attempts: " + ex.what());
            }
            spdlog::warn("Redis connect attempt {}/{} failed: {}", attempt, max_attempts,
                         ex.what());
            std::this_thread::sleep_for(std::chrono::milliseconds(delay_ms));
            delay_ms = std::min(delay_ms * 2, 5000);
        }
    }
    // Unreachable
    throw std::runtime_error("failed to connect to " + url);
}

}  // namespace deltafi::internal
