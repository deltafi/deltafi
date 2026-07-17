// ABOUTME: Heartbeat thread for reporting action liveness to DeltaFi core.
// ABOUTME: Sends periodic heartbeats and tracks long-running task executions.

#pragma once

#include <chrono>
#include <map>
#include <mutex>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "deltafi/log.hpp"
#include "deltafi/queue.hpp"

namespace deltafi {

inline constexpr auto heartbeat_interval = std::chrono::seconds(10);
inline constexpr auto long_running_task_threshold = std::chrono::seconds(30);

// Tracks an in-flight action execution for long-running task reporting.
struct ActionExecution {
    std::string did;
    std::string action_name;
    std::string start_time;
    std::string heartbeat_time;
    std::string app_name;
    std::string thread_id;
    std::chrono::system_clock::time_point started_at;  // for age gating; not serialized
};

inline void to_json(nlohmann::json& j, const ActionExecution& e) {
    j = nlohmann::json{{"did", e.did},
                       {"actionName", e.action_name},
                       {"startTime", e.start_time},
                       {"heartbeatTime", e.heartbeat_time},
                       {"appName", e.app_name}};
}

// Long-running-task hash key: clazz:action#thread:did. C++ has a single action
// identity, so the action name fills both the clazz and action segments.
inline std::string task_key(const ActionExecution& e) {
    return e.action_name + ":" + e.action_name + "#" + e.thread_id + ":" + e.did;
}

// A task is "long-running" once it has been in flight for the threshold.
inline bool is_long_running(const ActionExecution& exec,
                            std::chrono::system_clock::time_point now) {
    return (now - exec.started_at) >= long_running_task_threshold;
}

// Thread-safe tracker for currently executing actions.
class InFlightTracker {
public:
    void start(const std::string& did, const std::string& action_name,
               const std::string& app_name) {
        std::scoped_lock lock(mu_);
        auto now_tp = std::chrono::system_clock::now();
        auto now = format_time(now_tp);
        ActionExecution exec{.did = did,
                             .action_name = action_name,
                             .start_time = now,
                             .heartbeat_time = now,
                             .app_name = app_name,
                             .thread_id = current_thread_id(),
                             .started_at = now_tp};
        tasks_[task_key(exec)] = exec;
    }

    void stop(const std::string& did, const std::string& action_name) {
        std::scoped_lock lock(mu_);
        // start()/stop() for one execution run on the same worker thread, so the
        // thread id reconstructs the same key.
        ActionExecution probe{
            .did = did, .action_name = action_name, .thread_id = current_thread_id()};
        tasks_.erase(task_key(probe));
    }

    std::vector<ActionExecution> snapshot() {
        std::scoped_lock lock(mu_);
        std::vector<ActionExecution> result;
        result.reserve(tasks_.size());
        auto now = format_time(std::chrono::system_clock::now());
        for (auto& [key, exec] : tasks_) {
            auto copy = exec;
            copy.heartbeat_time = now;
            result.push_back(std::move(copy));
        }
        return result;
    }

private:
    static std::string current_thread_id() {
        std::ostringstream ss;
        ss << std::this_thread::get_id();
        return ss.str();
    }

    std::mutex mu_;
    std::map<std::string, ActionExecution> tasks_;
};

// Heartbeat thread that periodically reports action liveness.
class Heartbeat {
public:
    Heartbeat(ActionEventQueue& queue, std::vector<std::string> action_names,
              InFlightTracker& tracker, const std::string& app_name)
        : queue_(queue),
          action_names_(std::move(action_names)),
          tracker_(tracker),
          app_name_(app_name) {}

    void start() {
        thread_ = std::jthread([this](const std::stop_token& stop) { run(stop); });
    }

    void request_stop() { thread_.request_stop(); }

    void join() {
        if (thread_.joinable()) thread_.join();
    }

private:
    ActionEventQueue& queue_;
    std::vector<std::string> action_names_;
    InFlightTracker& tracker_;
    std::string app_name_;
    std::jthread thread_;
    std::set<std::string> reported_keys_;

    void run(const std::stop_token& stop) {
        // Sleep in small increments (to respond to stop quickly) totalling one
        // heartbeat_interval.
        constexpr int steps = static_cast<int>(heartbeat_interval / std::chrono::milliseconds(100));
        while (!stop.stop_requested()) {
            for (int i = 0; i < steps && !stop.stop_requested(); ++i) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            if (stop.stop_requested()) return;

            // Send heartbeat for each action
            for (const auto& name : action_names_) {
                try {
                    queue_.heartbeat(name);
                } catch (const std::exception& ex) {
                    spdlog::error("heartbeat error for {}: {}", name, ex.what());
                }
            }

            // Report long-running tasks
            report_long_running_tasks();
        }
    }

    void report_long_running_tasks() {
        std::set<std::string> current_keys;
        auto now = std::chrono::system_clock::now();

        for (const auto& exec : tracker_.snapshot()) {
            // Only report tasks that have been in flight past the threshold.
            if (!is_long_running(exec, now)) continue;
            auto key = task_key(exec);
            current_keys.insert(key);

            try {
                nlohmann::json data = exec;
                queue_.set_long_running_task(key, data.dump());
                reported_keys_.insert(key);
            } catch (const std::exception& ex) {
                spdlog::error("failed to report long-running task {}: {}", key, ex.what());
            }
        }

        // Remove completed tasks that were previously reported
        for (auto it = reported_keys_.begin(); it != reported_keys_.end();) {
            if (!current_keys.contains(*it)) {
                try {
                    queue_.remove_long_running_task(*it);
                } catch (const std::exception& ex) {
                    spdlog::error("failed to remove long-running task {}: {}", *it, ex.what());
                }
                it = reported_keys_.erase(it);
            } else {
                ++it;
            }
        }
    }
};

}  // namespace deltafi
