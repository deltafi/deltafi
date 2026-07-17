// ABOUTME: Tests that result publishing uses per-publisher connections, not one shared lock.
// ABOUTME: Concurrent publishers each own a connection; verified race-free under tsan.

#include <memory>
#include <string>
#include <thread>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/queue.hpp"

#include "fake_resp_server.hpp"

using deltafi::test::FakeRespServer;
using namespace std::chrono_literals;

TEST_CASE("concurrent publishers each use their own connection", "[queue][publish]") {
    FakeRespServer server;
    deltafi::ActionEventQueue queue(server.url(), "");

    constexpr int kThreads = 4;
    constexpr int kPerThread = 25;

    std::vector<std::unique_ptr<deltafi::PublishConnection>> conns;
    conns.reserve(kThreads);
    for (int i = 0; i < kThreads; ++i) {
        conns.push_back(queue.create_publish_connection());
    }

    std::vector<std::thread> threads;
    threads.reserve(kThreads);
    for (int i = 0; i < kThreads; ++i) {
        threads.emplace_back([&, i] {
            for (int j = 0; j < kPerThread; ++j) {
                queue.put(*conns[i], "dgs",
                          "payload-" + std::to_string(i) + "-" + std::to_string(j));
            }
        });
    }
    for (auto& t : threads) t.join();

    server.wait_for_command_count("ZADD", (kThreads * kPerThread) - 1, 5s);
    REQUIRE(server.count_commands("ZADD") == kThreads * kPerThread);
    // One connection per publisher plus the queue's own shared connection: proves
    // publishing does not funnel through a single shared connection.
    REQUIRE(server.connections_accepted() == kThreads + 1);
}

TEST_CASE("a publish connection reconnects after its socket is dropped", "[queue][publish]") {
    FakeRespServer server;
    deltafi::ActionEventQueue queue(server.url(), "");

    auto conn = queue.create_publish_connection();
    queue.put(*conn, "dgs", "first");
    server.wait_for_command_count("ZADD", 0, 2s);

    int before = server.connections_accepted();
    server.drop_active_socket();

    queue.put(*conn, "dgs", "second");
    REQUIRE(server.connections_accepted() > before);
    REQUIRE(server.count_commands("ZADD") == 2);
}
