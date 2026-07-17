// ABOUTME: Tests that ActionEventQueue rebuilds its Redis connection after a drop.
// ABOUTME: Uses an in-process RESP server that scripts replies and can sever sockets.

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/content.hpp"
#include "deltafi/heartbeat.hpp"
#include "deltafi/lookup.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/testing.hpp"
#include "deltafi/worker.hpp"

#include "fake_resp_server.hpp"

using deltafi::test::FakeRespServer;

namespace {

// Minimal supplier that returns no rows; used only to drive the runner's poll loop.
class NoopSupplier : public deltafi::LookupTableSupplierBase {
public:
    explicit NoopSupplier(std::string name) : name_(std::move(name)) {}

    deltafi::LookupTableDefinition table() const override {
        deltafi::LookupTableDefinition def;
        def.name = name_;
        return def;
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& /*variables*/,
        const std::map<std::string, std::vector<std::string>>& /*matching_column_values*/,
        const std::vector<std::string>& /*result_columns*/) override {
        return {};
    }

private:
    std::string name_;
};

}  // namespace

using namespace std::chrono_literals;

TEST_CASE("ActionEventQueue.put reconnects after the server drops the socket",
          "[queue][reconnect]") {
    FakeRespServer server;

    deltafi::ActionEventQueue queue(server.url(), "");
    REQUIRE(server.connections_accepted() == 1);

    // Baseline: first put succeeds.
    queue.put("dgs", "first-payload");
    REQUIRE(server.commands_received().size() >= 2);  // PING + ZADD

    // Server severs the existing connection.
    int before = server.connections_accepted();
    server.drop_active_socket();

    // Second put must rebuild the connection and succeed.
    queue.put("dgs", "second-payload");

    server.wait_for_new_connection(before, 2s);
    REQUIRE(server.connections_accepted() == before + 1);

    bool saw_second_zadd = false;
    for (const auto& cmd : server.commands_received()) {
        if (cmd.size() >= 4 && cmd[0] == "ZADD" && cmd[3] == "second-payload") {
            saw_second_zadd = true;
        }
    }
    REQUIRE(saw_second_zadd);
}

TEST_CASE("ActionWorker poller reconnects after its socket is dropped", "[worker][reconnect]") {
    FakeRespServer server;
    deltafi::ActionEventQueue queue(server.url(), "");
    deltafi::testing::InMemoryContentStore content;
    deltafi::InFlightTracker tracker;
    auto executor = [](deltafi::ActionContext&, deltafi::QueueEvent&) {
        return deltafi::ActionEvent{};
    };

    deltafi::ActionWorker worker("TestAction", executor, queue, content, tracker, "test-app",
                                 /*worker_count=*/1);
    worker.start();

    // Wait until the poller has actually started issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", 0, 2s);
    int bzpops_before = server.count_commands("BZPOPMIN");
    REQUIRE(bzpops_before >= 1);

    // Drop the poller's connection mid-flight.
    server.drop_active_socket();

    // Poller must reconnect and resume issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", bzpops_before, 5s);
    int bzpops_after = server.count_commands("BZPOPMIN");

    worker.request_stop();
    worker.drain(std::chrono::steady_clock::now() + 5s);

    REQUIRE(bzpops_after > bzpops_before);
}

TEST_CASE("LookupTableSupplierRunner reconnects after its socket is dropped",
          "[lookup][reconnect]") {
    FakeRespServer server;
    deltafi::ActionEventQueue queue(server.url(), "");

    std::map<std::string, std::shared_ptr<deltafi::LookupTableSupplierBase>> suppliers;
    suppliers["my-table"] = std::make_shared<NoopSupplier>("my-table");

    deltafi::LookupTableSupplierRunner runner(queue, suppliers);
    runner.start();

    // Wait until the runner has actually started issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", 0, 2s);
    int bzpops_before = server.count_commands("BZPOPMIN");
    REQUIRE(bzpops_before >= 1);

    // Drop the runner's connection mid-flight.
    server.drop_active_socket();

    // Runner must reconnect and resume issuing BZPOPMIN.
    server.wait_for_command_count("BZPOPMIN", bzpops_before, 5s);
    int bzpops_after = server.count_commands("BZPOPMIN");

    runner.request_stop();
    runner.join();

    REQUIRE(bzpops_after > bzpops_before);
}
