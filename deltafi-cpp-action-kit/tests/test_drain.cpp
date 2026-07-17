// ABOUTME: Tests lifecycle helpers: the health-probe RAII guard and the bounded drain.
// ABOUTME: Verifies the readiness marker is removed on all exit paths.

#include <filesystem>

#include <catch2/catch_test_macros.hpp>

#include <deltafi/plugin.hpp>

using namespace deltafi;

TEST_CASE("HealthProbe removes its marker file on destruction") {
    auto path = std::filesystem::temp_directory_path() / "deltafi-probe-test";
    std::filesystem::remove(path);
    {
        HealthProbe probe(path);
        CHECK(std::filesystem::exists(path));
    }
    CHECK_FALSE(std::filesystem::exists(path));
}

TEST_CASE("wait_for_pool returns at the deadline for a slow task, not after it") {
    BS::thread_pool<> pool(1);
    pool.detach_task([] { std::this_thread::sleep_for(std::chrono::seconds(2)); });
    auto start = std::chrono::steady_clock::now();
    bool done = wait_for_pool(pool, start + std::chrono::milliseconds(200));
    auto elapsed = std::chrono::steady_clock::now() - start;
    CHECK_FALSE(done);
    CHECK(elapsed < std::chrono::milliseconds(700));
    pool.wait();
}

TEST_CASE("wait_for_pool returns true when tasks finish before the deadline") {
    BS::thread_pool<> pool(1);
    pool.detach_task([] {});
    CHECK(wait_for_pool(pool, std::chrono::steady_clock::now() + std::chrono::seconds(5)));
}
