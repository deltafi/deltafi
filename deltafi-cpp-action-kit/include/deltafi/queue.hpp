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

// Wraps Redis/Valkey connections for DeltaFi queue operations.
class ActionEventQueue {
public:
    ActionEventQueue(const std::string& url, const std::string& password)
        : url_(url), password_(password),
          shared_conn_(internal::connect_with_retry(url, password)) {
        if (!shared_conn_->ping()) {
            throw std::runtime_error("Redis PING failed after connect");
        }
        spdlog::info("Connected to Redis at {}", url);
    }

    // Create a dedicated connection for a blocking poller thread.
    std::unique_ptr<internal::RespConnection> create_poller_connection() {
        return internal::connect_with_retry(url_, password_);
    }

    // Block until an item is available on the queue (5s server-side timeout).
    // Returns the raw JSON string, or empty string on timeout.
    std::string take(internal::RespConnection& conn,
                     const std::string& queue_name) {
        auto result = conn.bzpopmin(queue_name, 5);
        if (!result) return "";
        return result->first; // member
    }

    // Block until an item is available on any of the queues (5s timeout).
    // Returns the raw JSON string, or empty string on timeout.
    std::string take_from_any(internal::RespConnection& conn,
                              const std::vector<std::string>& queue_names) {
        auto result = conn.bzpopmin(queue_names, 5);
        if (!result) return "";
        return result->first;
    }

    // Publish data to the named queue with current timestamp as score.
    void put(const std::string& queue_name, const std::string& data) {
        double score = static_cast<double>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count());
        with_shared([&](internal::RespConnection& conn) {
            conn.zadd(queue_name, score, data);
        });
    }

    // Update the heartbeat timestamp for an action.
    void heartbeat(const std::string& action_name) {
        with_shared([&](internal::RespConnection& conn) {
            conn.hset(heartbeat_key, action_name,
                      format_time(std::chrono::system_clock::now()));
        });
    }

    // Record a long-running task execution.
    void set_long_running_task(const std::string& key,
                                const std::string& value) {
        with_shared([&](internal::RespConnection& conn) {
            conn.hset(long_running_task_key, key, value);
        });
    }

    // Remove a long-running task entry.
    void remove_long_running_task(const std::string& key) {
        with_shared([&](internal::RespConnection& conn) {
            conn.hdel(long_running_task_key, key);
        });
    }

    // Create a dedicated subscription connection.
    std::unique_ptr<internal::RespConnection> create_subscribe_connection() {
        return internal::connect_with_retry(url_, password_);
    }

    void close() {
        std::lock_guard lock(shared_mu_);
        shared_conn_->close();
    }

private:
    std::string url_;
    std::string password_;
    std::unique_ptr<internal::RespConnection> shared_conn_;
    std::mutex shared_mu_;

    // Execute op against the shared connection. If the connection is broken,
    // rebuild it via connect_with_retry and retry op once. The mutex is held
    // for the whole sequence so concurrent callers serialize on the rebuild.
    template <typename F>
    void with_shared(F&& op) {
        std::lock_guard lock(shared_mu_);
        try {
            op(*shared_conn_);
            return;
        } catch (const std::exception& ex) {
            spdlog::warn("Redis op failed, reconnecting: {}", ex.what());
        }
        shared_conn_.reset();
        shared_conn_ = internal::connect_with_retry(url_, password_);
        op(*shared_conn_);
    }
};

} // namespace deltafi
