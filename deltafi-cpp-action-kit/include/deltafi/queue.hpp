// ABOUTME: Redis/Valkey queue operations for DeltaFi action communication.
// ABOUTME: Implements BZPOPMIN-based work polling and ZADD-based result publishing.

#pragma once

#include <chrono>
#include <memory>
#include <mutex>
#include <string>

#include <spdlog/spdlog.h>

#include "deltafi/internal/resp_client.hpp"
#include "deltafi/log.hpp"

namespace deltafi {

inline constexpr const char* heartbeat_key = "org.deltafi.action-queue.heartbeat";
inline constexpr const char* long_running_task_key = "org.deltafi.action-queue.long-running-tasks";

// Milliseconds-since-epoch score used to time-order queue entries.
inline double queue_score() {
    return static_cast<double>(std::chrono::duration_cast<std::chrono::milliseconds>(
                                   std::chrono::system_clock::now().time_since_epoch())
                                   .count());
}

// A Redis/Valkey connection guarded by its own mutex, rebuilt on failure. Each
// publisher owns one so result publishing does not serialize on a single
// process-wide lock.
class PublishConnection {
public:
    PublishConnection(std::string url, std::string password)
        : url_(std::move(url)),
          password_(std::move(password)),
          conn_(internal::connect_with_retry(url_, password_)) {}

    // Run op against the connection under the mutex. On failure, rebuild the
    // connection once and retry, so a dropped socket is transparent to callers.
    template<typename F>
    void with(
        F&& op) {  // NOLINT(cppcoreguidelines-missing-std-forward): op is invoked more than once
        std::scoped_lock lock(mu_);
        try {
            op(*conn_);
            return;
        } catch (const std::exception& ex) {
            spdlog::warn("Redis op failed, reconnecting: {}", ex.what());
        }
        conn_.reset();
        conn_ = internal::connect_with_retry(url_, password_);
        op(*conn_);
    }

    void close() {
        std::scoped_lock lock(mu_);
        conn_->close();
    }

private:
    std::string url_;
    std::string password_;
    std::unique_ptr<internal::RespConnection> conn_;
    std::mutex mu_;
};

// Wraps Redis/Valkey connections for DeltaFi queue operations.
class ActionEventQueue {
public:
    ActionEventQueue(const std::string& url, const std::string& password)
        : url_(url), password_(password), shared_(url, password) {
        bool ok = false;
        shared_.with([&](internal::RespConnection& conn) { ok = conn.ping(); });
        if (!ok) {
            throw std::runtime_error("Redis PING failed after connect");
        }
        spdlog::info("Connected to Redis at {}", url);
    }

    // Create a dedicated connection for a blocking poller thread.
    std::unique_ptr<internal::RespConnection> create_poller_connection() {
        return internal::connect_with_retry(url_, password_);
    }

    // Block until an item is available on the queue (5s server-side timeout).
    // Returns the raw JSON string, or empty string on timeout. Part of the queue
    // interface; kept a member though the current impl uses only the given conn.
    // NOLINTNEXTLINE(readability-convert-member-functions-to-static)
    std::string take(internal::RespConnection& conn, const std::string& queue_name) {
        auto result = conn.bzpopmin(queue_name, 5);
        if (!result) return "";
        return result->first;  // member
    }

    // Block until an item is available on any of the queues (5s timeout).
    // Returns the raw JSON string, or empty string on timeout.
    // NOLINTNEXTLINE(readability-convert-member-functions-to-static)
    std::string take_from_any(internal::RespConnection& conn,
                              const std::vector<std::string>& queue_names) {
        auto result = conn.bzpopmin(queue_names, 5);
        if (!result) return "";
        return result->first;
    }

    // Create a publish connection for a worker to publish results on its own,
    // avoiding contention on the shared connection.
    std::unique_ptr<PublishConnection> create_publish_connection() {
        return std::make_unique<PublishConnection>(url_, password_);
    }

    // Publish data to the named queue on the caller's own connection.
    // NOLINTNEXTLINE(readability-convert-member-functions-to-static)
    void put(PublishConnection& conn, const std::string& queue_name, const std::string& data) {
        conn.with([&](internal::RespConnection& c) { c.zadd(queue_name, queue_score(), data); });
    }

    // Publish data to the named queue on the shared connection.
    void put(const std::string& queue_name, const std::string& data) {
        shared_.with(
            [&](internal::RespConnection& conn) { conn.zadd(queue_name, queue_score(), data); });
    }

    // Update the heartbeat timestamp for an action.
    void heartbeat(const std::string& action_name) {
        shared_.with([&](internal::RespConnection& conn) {
            conn.hset(heartbeat_key, action_name, format_time(std::chrono::system_clock::now()));
        });
    }

    // Record a long-running task execution.
    void set_long_running_task(const std::string& key, const std::string& value) {
        shared_.with(
            [&](internal::RespConnection& conn) { conn.hset(long_running_task_key, key, value); });
    }

    // Remove a long-running task entry.
    void remove_long_running_task(const std::string& key) {
        shared_.with(
            [&](internal::RespConnection& conn) { conn.hdel(long_running_task_key, key); });
    }

    // Create a dedicated subscription connection.
    std::unique_ptr<internal::RespConnection> create_subscribe_connection() {
        return internal::connect_with_retry(url_, password_);
    }

    void close() { shared_.close(); }

private:
    std::string url_;
    std::string password_;
    PublishConnection shared_;
};

}  // namespace deltafi
