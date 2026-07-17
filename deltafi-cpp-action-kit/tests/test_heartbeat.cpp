// ABOUTME: Tests long-running-task reporting: threshold gating and the spec key form.
// ABOUTME: Exercises is_long_running and task_key against an injected clock.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/heartbeat.hpp>

using namespace deltafi;

TEST_CASE("is_long_running gates on the threshold") {
    auto now = std::chrono::system_clock::now();
    ActionExecution young{.did = "did",
                          .action_name = "action",
                          .start_time = "",
                          .heartbeat_time = "",
                          .app_name = "app",
                          .thread_id = "t1",
                          .started_at = now - std::chrono::seconds(5)};
    ActionExecution old{.did = "did",
                        .action_name = "action",
                        .start_time = "",
                        .heartbeat_time = "",
                        .app_name = "app",
                        .thread_id = "t1",
                        .started_at = now - std::chrono::seconds(45)};
    CHECK_FALSE(is_long_running(young, now));
    CHECK(is_long_running(old, now));
}

TEST_CASE("task_key uses the clazz:action#thread:did form") {
    ActionExecution e{.did = "the-did",
                      .action_name = "MyAction",
                      .start_time = "",
                      .heartbeat_time = "",
                      .app_name = "app",
                      .thread_id = "tid7",
                      .started_at = {}};
    CHECK(task_key(e) == "MyAction:MyAction#tid7:the-did");
}
