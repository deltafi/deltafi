// ABOUTME: Tests the poll-loop backoff primitives: escalation, cap, jitter, cancellable sleep.
// ABOUTME: Mirrors the shared action-kit backoff contract (100ms->5s, half-to-full jitter).

#include <chrono>
#include <stop_token>
#include <thread>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/internal/backoff.hpp"

using namespace std::chrono_literals;
using deltafi::internal::backoff_sleep;
using deltafi::internal::jitter_backoff;
using deltafi::internal::next_backoff;
using deltafi::internal::PollBackoff;

TEST_CASE("next_backoff doubles, floors at base, caps at max", "[backoff]") {
    CHECK(next_backoff(100ms) == 200ms);
    CHECK(next_backoff(200ms) == 400ms);
    CHECK(next_backoff(400ms) == 800ms);
    CHECK(next_backoff(3200ms) == 5000ms);  // 6400 capped
    CHECK(next_backoff(5000ms) == 5000ms);  // stays capped
    CHECK(next_backoff(0ms) == 200ms);      // floored to base(100) then doubled
    CHECK(next_backoff(50ms) == 200ms);     // below base floored then doubled
}

TEST_CASE("jitter_backoff stays within [d/2, d] and maps zero to zero", "[backoff]") {
    CHECK(jitter_backoff(0ms) == 0ms);
    for (int i = 0; i < 1000; ++i) {
        auto j = jitter_backoff(5000ms);
        CHECK(j >= 2500ms);
        CHECK(j <= 5000ms);
    }
    for (int i = 0; i < 1000; ++i) {
        auto j = jitter_backoff(100ms);
        CHECK(j >= 50ms);
        CHECK(j <= 100ms);
    }
}

TEST_CASE("backoff_sleep returns immediately when stop already requested", "[backoff]") {
    std::stop_source src;
    src.request_stop();
    auto start = std::chrono::steady_clock::now();
    bool elapsed = backoff_sleep(src.get_token(), 5000ms);
    auto took = std::chrono::steady_clock::now() - start;
    CHECK_FALSE(elapsed);
    CHECK(took < 500ms);
}

TEST_CASE("backoff_sleep wakes early when stop is requested during the wait", "[backoff]") {
    std::stop_source src;
    std::jthread waker([&src] {
        std::this_thread::sleep_for(20ms);
        src.request_stop();
    });
    auto start = std::chrono::steady_clock::now();
    bool elapsed = backoff_sleep(src.get_token(), 5000ms);
    auto took = std::chrono::steady_clock::now() - start;
    CHECK_FALSE(elapsed);
    CHECK(took < 1000ms);
}

TEST_CASE("backoff_sleep elapses when not interrupted", "[backoff]") {
    std::stop_source src;  // never stopped
    auto start = std::chrono::steady_clock::now();
    bool elapsed = backoff_sleep(src.get_token(), 30ms);
    auto took = std::chrono::steady_clock::now() - start;
    CHECK(elapsed);
    CHECK(took >= 25ms);
}

TEST_CASE("PollBackoff escalates on consecutive errors and resets on healthy read", "[backoff]") {
    std::stop_source src;           // never stopped
    PollBackoff backoff(1ms, 8ms);  // tiny window so the test is fast
    CHECK(backoff.current() == 1ms);
    CHECK(backoff.wait_after_error(src.get_token()));
    CHECK(backoff.current() == 2ms);
    CHECK(backoff.wait_after_error(src.get_token()));
    CHECK(backoff.current() == 4ms);
    CHECK(backoff.wait_after_error(src.get_token()));
    CHECK(backoff.current() == 8ms);
    CHECK(backoff.wait_after_error(src.get_token()));
    CHECK(backoff.current() == 8ms);  // capped
    backoff.reset();
    CHECK(backoff.current() == 1ms);
}

TEST_CASE("PollBackoff wait_after_error returns false when cancelled", "[backoff]") {
    std::stop_source src;
    src.request_stop();
    PollBackoff backoff(100ms, 5000ms);
    CHECK_FALSE(backoff.wait_after_error(src.get_token()));
}
