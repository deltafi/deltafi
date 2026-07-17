// ABOUTME: Capped exponential backoff with jitter for the queue poll loops.
// ABOUTME: Keeps a downed Valkey from causing a tight reconnect spin; escalates 100ms -> 5s.

#pragma once

#include <chrono>
#include <condition_variable>
#include <mutex>
#include <random>
#include <stop_token>

namespace deltafi::internal {

// Bounds for the poll-loop retry delay, shared with the Java/Go/Python kits: the delay
// doubles from base to max on consecutive read errors and resets to base after a healthy read.
inline constexpr std::chrono::milliseconds poll_backoff_base{100};
inline constexpr std::chrono::milliseconds poll_backoff_max{5000};

// Double current, floored at base and capped at max.
inline std::chrono::milliseconds next_backoff(std::chrono::milliseconds current,
                                              std::chrono::milliseconds base = poll_backoff_base,
                                              std::chrono::milliseconds max = poll_backoff_max) {
    auto delay = current < base ? base : current;
    delay *= 2;
    return delay > max ? max : delay;
}

// Return a duration in [d/2, d] so poll loops across threads do not retry in lockstep
// (thundering herd). Returns d for d <= 0.
inline std::chrono::milliseconds jitter_backoff(std::chrono::milliseconds d) {
    if (d <= std::chrono::milliseconds(0)) return d;
    auto half = d / 2;
    thread_local std::mt19937_64 rng(std::random_device{}());
    std::uniform_int_distribution<std::chrono::milliseconds::rep> dist(0, half.count());
    return d - std::chrono::milliseconds(dist(rng));
}

// Block for d or until stop is requested. Returns true if the delay elapsed, false if the
// stop was requested (the caller should stop). A non-positive d returns immediately.
inline bool backoff_sleep(const std::stop_token& stop, std::chrono::milliseconds d) {
    if (stop.stop_requested()) return false;
    if (d <= std::chrono::milliseconds(0)) return true;
    std::mutex mu;
    std::condition_variable_any cv;
    std::unique_lock lock(mu);
    bool stopped = cv.wait_for(lock, stop, d, [&stop] { return stop.stop_requested(); });
    return !stopped;
}

// Stateful backoff for a single poll loop: reset() on a healthy read, wait_after_error() after
// a read error (waits the jittered current delay, then escalates). Not thread-safe; each loop
// owns its own instance.
class PollBackoff {
public:
    explicit PollBackoff(std::chrono::milliseconds base = poll_backoff_base,
                         std::chrono::milliseconds max = poll_backoff_max)
        : base_(base), max_(max), current_(base) {}

    // Reset to the base delay after a healthy read (an event, or an idle timeout with no work).
    void reset() { current_ = base_; }

    // Wait the jittered current delay, then escalate. Returns false if the stop was requested
    // during the wait (the caller should stop), true otherwise.
    bool wait_after_error(const std::stop_token& stop) {
        if (!backoff_sleep(stop, jitter_backoff(current_))) return false;
        current_ = next_backoff(current_, base_, max_);
        return true;
    }

    // The delay that the next error will wait (before jitter). Exposed for logging and tests.
    std::chrono::milliseconds current() const { return current_; }

private:
    std::chrono::milliseconds base_;
    std::chrono::milliseconds max_;
    std::chrono::milliseconds current_;
};

}  // namespace deltafi::internal
