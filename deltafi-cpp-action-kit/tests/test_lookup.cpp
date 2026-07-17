// ABOUTME: Tests the lookup client against an in-process fake core GraphQL server.
// ABOUTME: Covers ttlSeconds parsing on lookup results.

#include <chrono>
#include <thread>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include <nlohmann/json.hpp>

#include "deltafi/lookup.hpp"

#include "fake_core_server.hpp"

using deltafi::LookupClient;
using deltafi::test::FakeCoreServer;
using namespace std::chrono_literals;

namespace {
nlohmann::json lookup_response(int total_count) {
    return {
        {"data", {{"lookup", {{"totalCount", total_count}, {"rows", nlohmann::json::array()}}}}}};
}

// A GraphQL mutation Result envelope for operation `op`.
nlohmann::json result_response(const std::string& op, bool success = true) {
    return {
        {"data",
         {{op, {{"success", success}, {"info", {"ok"}}, {"errors", nlohmann::json::array()}}}}}};
}
}  // namespace

TEST_CASE("lookup parses ttlSeconds from the response", "[lookup]") {
    FakeCoreServer server;
    server.set_response(
        {{"data",
          {{"lookup",
            {{"totalCount", 2}, {"ttlSeconds", 42}, {"rows", nlohmann::json::array()}}}}}});

    LookupClient client(server.url());
    auto results = client.lookup("my_table");

    REQUIRE(results.ttl_seconds.has_value());
    CHECK(results.ttl_seconds.value_or(-1) == 42);
}

TEST_CASE("lookup ttl_seconds is empty when the response omits it", "[lookup]") {
    FakeCoreServer server;
    server.set_response(
        {{"data", {{"lookup", {{"totalCount", 0}, {"rows", nlohmann::json::array()}}}}}});

    LookupClient client(server.url());
    auto results = client.lookup("my_table");

    CHECK_FALSE(results.ttl_seconds.has_value());
}

TEST_CASE("lookup coalesces concurrent misses into a single query", "[lookup]") {
    FakeCoreServer server;
    server.set_response(lookup_response(1));
    server.set_response_delay(100ms);  // hold the leader's response so followers overlap

    LookupClient client(server.url());
    {
        std::vector<std::jthread> threads;
        threads.reserve(8);
        for (int i = 0; i < 8; ++i) {
            threads.emplace_back([&client] { client.lookup("t"); });
        }
    }  // join all

    CHECK(server.request_count() == 1);

    client.lookup("t");  // served from cache, no new request
    CHECK(server.request_count() == 1);
}

TEST_CASE("evict_expired removes stale cache entries", "[lookup]") {
    FakeCoreServer server;
    server.set_response(lookup_response(0));

    LookupClient client(server.url());
    client.set_cache_ttl(0);  // entries expire immediately
    client.lookup("t");
    CHECK(client.cache_size() == 1);

    client.evict_expired();
    CHECK(client.cache_size() == 0);
}

TEST_CASE("the background evictor purges expired entries", "[lookup]") {
    FakeCoreServer server;
    server.set_response(lookup_response(0));

    LookupClient client(server.url(), 20ms);  // fast evictor interval
    client.set_cache_ttl(0);
    client.lookup("t");

    for (int i = 0; i < 200 && client.cache_size() != 0; ++i) {
        std::this_thread::sleep_for(10ms);
    }
    CHECK(client.cache_size() == 0);
}

TEST_CASE("get_lookup_tables issues the query and parses tables", "[lookup]") {
    FakeCoreServer server;
    server.set_response({{"data",
                          {{"getLookupTables",
                            {{{"name", "colors"},
                              {"columns", {"name", "hex"}},
                              {"keyColumns", {"name"}},
                              {"serviceBacked", false},
                              {"backingServiceActive", false},
                              {"pullThrough", false},
                              {"refreshDuration", "PT1H"},
                              {"lastRefresh", "2026-01-01T00:00:00Z"},
                              {"totalRows", 3}}}}}}});

    LookupClient client(server.url());
    auto tables = client.get_lookup_tables();

    CHECK(server.request(0)["query"].get<std::string>().find("getLookupTables") !=
          std::string::npos);
    REQUIRE(tables.size() == 1);
    CHECK(tables[0].name == "colors");
    CHECK(tables[0].columns == std::vector<std::string>{"name", "hex"});
    CHECK(tables[0].key_columns == std::vector<std::string>{"name"});
    CHECK(tables[0].refresh_duration == "PT1H");
    REQUIRE(tables[0].total_rows.has_value());
    CHECK(tables[0].total_rows.value_or(-1) == 3);
}

TEST_CASE("create_lookup_table sends LookupTableInput and returns a Result", "[lookup]") {
    FakeCoreServer server;
    server.set_response(result_response("createLookupTable"));

    LookupClient client(server.url());
    deltafi::LookupTableDefinition def;
    def.name = "colors";
    def.columns = {"name", "hex"};
    def.key_columns = {"name"};
    def.refresh_duration = "PT1H";
    auto result = client.create_lookup_table(def);

    auto req = server.request(0);
    CHECK(req["query"].get<std::string>().find("createLookupTable") != std::string::npos);
    auto input = req["variables"]["lookupTableInput"];
    CHECK(input["name"] == "colors");
    CHECK(input["keyColumns"] == nlohmann::json::array({"name"}));
    CHECK_FALSE(input.contains("variables"));  // LookupTableInput has no variables field
    CHECK(result.success);
    CHECK(result.info == std::vector<std::string>{"ok"});
}

TEST_CASE("upsert_rows sends rows as [[ColumnValueInput]] and returns a Result", "[lookup]") {
    FakeCoreServer server;
    server.set_response(result_response("upsertLookupTableRows"));

    LookupClient client(server.url());
    std::vector<std::map<std::string, std::string>> rows = {{{"name", "red"}, {"hex", "#f00"}}};
    auto result = client.upsert_rows("colors", rows);

    auto req = server.request(0);
    CHECK(req["query"].get<std::string>().find("upsertLookupTableRows") != std::string::npos);
    CHECK(req["variables"]["lookupTableName"] == "colors");
    auto sent_rows = req["variables"]["rows"];
    REQUIRE(sent_rows.is_array());
    REQUIRE(sent_rows.size() == 1);
    // each row is a list of {column, value} objects
    for (const auto& cv : sent_rows[0]) {
        CHECK(cv.contains("column"));
        CHECK(cv.contains("value"));
    }
    CHECK(result.success);
}

TEST_CASE("remove_rows issues the removeLookupTableRows mutation", "[lookup]") {
    FakeCoreServer server;
    server.set_response(result_response("removeLookupTableRows"));

    LookupClient client(server.url());
    std::vector<std::map<std::string, std::string>> rows = {{{"name", "red"}}};
    auto result = client.remove_rows("colors", rows);

    CHECK(server.request(0)["query"].get<std::string>().find("removeLookupTableRows") !=
          std::string::npos);
    CHECK(result.success);
}

TEST_CASE("set_backing_service_active sends the flag", "[lookup]") {
    FakeCoreServer server;
    server.set_response(result_response("setLookupTableBackingServiceActive"));

    LookupClient client(server.url());
    auto result = client.set_backing_service_active("colors", true);

    auto req = server.request(0);
    CHECK(req["query"].get<std::string>().find("setLookupTableBackingServiceActive") !=
          std::string::npos);
    CHECK(req["variables"]["lookupTableName"] == "colors");
    CHECK(req["variables"]["active"] == true);
    CHECK(result.success);
}

TEST_CASE("upsert_rows batches large row sets into 10000-row requests", "[lookup]") {
    FakeCoreServer server;
    server.set_response(result_response("upsertLookupTableRows"));

    LookupClient client(server.url());
    std::vector<std::map<std::string, std::string>> rows(10001, {{"name", "x"}});
    auto result = client.upsert_rows("colors", rows);

    CHECK(server.request_count() == 2);  // 10000 + 1
    CHECK(result.success);
}
