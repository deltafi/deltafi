// ABOUTME: Tests for DELTAFI_PARAMS macro: schema generation, serialization, deserialization.
// ABOUTME: Covers primitives, nested structs, collections, coercion, and snake_to_camel.

#include <catch2/catch_test_macros.hpp>
#include <deltafi/params.hpp>

using json = nlohmann::json;

// --- Test structs ---

struct SimpleParams {
    std::string url;
    bool verbose = false;
    int max_retries = 3;

    DELTAFI_PARAMS(SimpleParams, REQUIRED(url, "Service URL"),
                   FIELD(verbose, "Enable verbose logging"),
                   FIELD(max_retries, "Maximum retry attempts"))
};

struct NestedInner {
    bool enabled = true;
    int count = 0;

    DELTAFI_PARAMS(NestedInner, FIELD(enabled, "Is enabled"), FIELD(count, "Item count"))
};

struct NestedParams {
    std::string name;
    std::optional<NestedInner> inner;
    std::vector<NestedInner> items;
    std::map<std::string, std::string> tags;

    DELTAFI_PARAMS(NestedParams, REQUIRED(name, "The name"), FIELD(inner, "Optional inner struct"),
                   FIELD(items, "List of items"), FIELD(tags, "Key-value tags"))
};

// --- Tests ---

TEST_CASE("snake_to_camel basic conversion") {
    CHECK(deltafi::params::snake_to_camel("convert_to_upper") == "convertToUpper");
    CHECK(deltafi::params::snake_to_camel("url") == "url");
    CHECK(deltafi::params::snake_to_camel("max_retries") == "maxRetries");
    CHECK(deltafi::params::snake_to_camel("did_error_prefix") == "didErrorPrefix");
    CHECK(deltafi::params::snake_to_camel("a_b_c") == "aBC");
}

TEST_CASE("schema generation for simple params") {
    auto s = SimpleParams::schema();

    CHECK(s["type"] == "object");
    CHECK(s["additionalProperties"] == false);

    auto& props = s["properties"];
    CHECK(props.contains("url"));
    CHECK(props["url"]["type"] == "string");
    CHECK(props["url"]["description"] == "Service URL");

    CHECK(props.contains("verbose"));
    CHECK(props["verbose"]["type"] == "boolean");
    CHECK(props["verbose"]["default"] == false);

    CHECK(props.contains("maxRetries"));
    CHECK(props["maxRetries"]["type"] == "integer");
    CHECK(props["maxRetries"]["default"] == 3);

    // url should be required
    CHECK(s.contains("required"));
    auto& req = s["required"];
    CHECK(req.size() == 1);
    CHECK(req[0] == "url");
}

TEST_CASE("schema generation for nested params") {
    auto s = NestedParams::schema();

    auto& props = s["properties"];

    // inner should have nested schema (object with properties)
    CHECK(props["inner"]["type"] == "object");
    CHECK(props["inner"]["properties"].contains("enabled"));
    CHECK(props["inner"]["properties"].contains("count"));

    // items should be array of nested schema
    CHECK(props["items"]["type"] == "array");
    CHECK(props["items"]["items"]["type"] == "object");

    // tags should be object with string values
    CHECK(props["tags"]["type"] == "object");
    CHECK(props["tags"]["additionalProperties"]["type"] == "string");
}

TEST_CASE("from_json with matching types") {
    json j = {{"url", "https://example.com"}, {"verbose", true}, {"maxRetries", 5}};

    auto p = j.get<SimpleParams>();
    CHECK(p.url == "https://example.com");
    CHECK(p.verbose == true);
    CHECK(p.max_retries == 5);
}

TEST_CASE("from_json with defaults") {
    json j = {{"url", "https://example.com"}};

    auto p = j.get<SimpleParams>();
    CHECK(p.url == "https://example.com");
    CHECK(p.verbose == false);
    CHECK(p.max_retries == 3);
}

TEST_CASE("from_json with string coercion") {
    json j = {{"url", "https://example.com"}, {"verbose", "true"}, {"maxRetries", "7"}};

    auto p = j.get<SimpleParams>();
    CHECK(p.verbose == true);
    CHECK(p.max_retries == 7);
}

TEST_CASE("from_json with float-to-int coercion") {
    json j = {{"url", "https://example.com"}, {"maxRetries", 5.0}};

    auto p = j.get<SimpleParams>();
    CHECK(p.max_retries == 5);
}

TEST_CASE("to_json round-trip") {
    SimpleParams p;
    p.url = "https://test.com";
    p.verbose = true;
    p.max_retries = 10;

    json j = p;
    CHECK(j["url"] == "https://test.com");
    CHECK(j["verbose"] == true);
    CHECK(j["maxRetries"] == 10);

    auto p2 = j.get<SimpleParams>();
    CHECK(p2.url == p.url);
    CHECK(p2.verbose == p.verbose);
    CHECK(p2.max_retries == p.max_retries);
}

TEST_CASE("nested struct from_json") {
    json j = {{"name", "test"},
              {"inner", {{"enabled", false}, {"count", 42}}},
              {"items", json::array({{{"enabled", true}, {"count", 1}}})},
              {"tags", {{"env", "prod"}}}};

    auto p = j.get<NestedParams>();
    CHECK(p.name == "test");
    REQUIRE(p.inner.has_value());
    // NOLINTNEXTLINE(bugprone-unchecked-optional-access) — guarded by REQUIRE above.
    CHECK(p.inner->enabled == false);
    // NOLINTNEXTLINE(bugprone-unchecked-optional-access) — guarded by REQUIRE above.
    CHECK(p.inner->count == 42);
    REQUIRE(p.items.size() == 1);
    CHECK(p.items[0].count == 1);
    CHECK(p.tags["env"] == "prod");
}

TEST_CASE("nested params no defaults in schema") {
    auto s = NestedParams::schema();
    auto& props = s["properties"];

    // Complex types should NOT have "default" in schema
    CHECK_FALSE(props["inner"].contains("default"));
    CHECK_FALSE(props["items"].contains("default"));
    CHECK_FALSE(props["tags"].contains("default"));
}
