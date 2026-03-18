// ABOUTME: Tests for HelloWorldCppTransformAction.
// ABOUTME: Covers content transformation, DID prefix filtering/error, lookups, artifact metadata, and uppercase.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/testing.hpp>
#include "actions/transform.hpp"

using namespace deltafi;
using namespace deltafi::testing;

namespace {

// Set up a test context with mock lookups matching the transform's expected tables.
struct TransformFixture {
    TestContext tc;
    MockLookupClient lookup;
    HelloWorldCppTransformAction action;

    TransformFixture(const std::string& did = "abc-did")
        : tc(new_test_context(did)) {

        LookupResults rgb;
        rgb.total_count = 3;
        rgb.created = "2026-01-01T00:00:00Z";
        rgb.modified = "2026-01-01T00:00:00Z";
        rgb.rows = {
            {{"column_a", "row1"}, {"color", "red"}},
            {{"column_a", "row2"}, {"color", "green"}},
            {{"column_a", "row3"}, {"color", "blue"}},
        };
        lookup.set_results("cpp_scheduled_lookup_table", rgb);

        LookupResults drivers;
        drivers.total_count = 2;
        drivers.rows = {
            {{"first", "Oscar"}, {"last", "Piastri"}, {"team", "McLaren"}},
            {{"first", "Lando"}, {"last", "Norris"}, {"team", "McLaren"}},
        };
        lookup.set_results("cpp_csv_lookup_table", drivers);

        tc.action_context.lookup_client = &lookup;
    }

    TransformInput make_input(const std::string& data,
                              const std::map<std::string, std::string>& metadata = {}) {
        TransformInput input;
        input.content.push_back(tc.content("input.txt", "text/plain", data));
        input.metadata = metadata;
        return input;
    }
};

} // namespace

TEST_CASE("Transform normal", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("hello world");

    auto event = run_transform(f.action, f.tc.action_context, input);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 1);
    REQUIRE(te[0].content.size() == 1);

    auto data = te[0].content[0].load_string();
    CHECK(data.find("hello world") != std::string::npos);
    CHECK(data.find("HelloWorldCppTransformAction did a great job") != std::string::npos);

    CHECK(te[0].content[0].has_tag("hello-file"));
    CHECK(te[0].metadata.at("transformKey") == "transformValue");
    CHECK(te[0].annotations.at("language") == "English");

    CHECK(!te[0].metadata.at("RGB Rows").empty());
    CHECK(te[0].metadata.at("McLaren Drivers").find("Piastri") != std::string::npos);
    CHECK(te[0].metadata.at("McLaren Drivers").find("Norris") != std::string::npos);
    // MockLookupClient returns the same results for all queries to the same table,
    // so purpleCount reflects the pre-configured RGB rows (3), not an actual purple filter
    CHECK(te[0].metadata.at("purpleCount") == "3");
}

TEST_CASE("Transform content name from metadata", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("data", {{"index", "7"}});

    auto event = run_transform(f.action, f.tc.action_context, input);
    auto& te = require_transform(event);

    CHECK(te[0].content[0].name == "content-7");
}

TEST_CASE("Transform content name default", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("data");

    auto event = run_transform(f.action, f.tc.action_context, input);
    auto& te = require_transform(event);

    CHECK(te[0].content[0].name == "content-0");
}

TEST_CASE("Transform filter on prefix", "[transform]") {
    TransformFixture f("2abc-did");
    auto input = f.make_input("hello");

    auto event = run_transform(f.action, f.tc.action_context, input);
    require_filter(event, "Filter due to did_filter_prefix match");
}

TEST_CASE("Transform error on prefix", "[transform]") {
    TransformFixture f("44abc-did");
    auto input = f.make_input("hello");

    auto event = run_transform(f.action, f.tc.action_context, input);
    auto& err = require_error(event, "Error due to did_error_prefix match");

    CHECK(err.context.find("44") != std::string::npos);
}

TEST_CASE("Transform custom prefixes", "[transform]") {
    TransformFixture f("xx-did");
    auto input = f.make_input("hello");

    HelloWorldCppTransformActionParams params;
    params.did_filter_prefix = "xx";
    params.did_error_prefix = "yy";

    auto result = f.action.transform(f.tc.action_context, params, input);
    auto event = result_event(result, f.tc.action_context);
    require_filter(event, "Filter due to did_filter_prefix match");
}

TEST_CASE("Transform custom error prefix", "[transform]") {
    TransformFixture f("yy-did");
    auto input = f.make_input("hello");

    HelloWorldCppTransformActionParams params;
    params.did_filter_prefix = "xx";
    params.did_error_prefix = "yy";

    auto result = f.action.transform(f.tc.action_context, params, input);
    auto event = result_event(result, f.tc.action_context);
    require_error(event, "Error due to did_error_prefix match");
}

TEST_CASE("Transform convert to upper", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("hello world");

    HelloWorldCppTransformActionParams params;
    params.convert_to_upper = true;

    auto result = f.action.transform(f.tc.action_context, params, input);
    auto event = result_event(result, f.tc.action_context);
    auto& te = require_transform(event);

    auto data = te[0].content[0].load_string();
    CHECK(data.find("HELLO WORLD") != std::string::npos);
    CHECK(data.find("HELLOWORLDCPPTRANSFORMACTION DID A GREAT JOB") != std::string::npos);
}

TEST_CASE("Transform no content", "[transform]") {
    TransformFixture f;
    TransformInput input;

    auto event = run_transform(f.action, f.tc.action_context, input);
    require_error(event, "No content provided");
}

TEST_CASE("Transform lookup error", "[transform]") {
    TransformFixture f;
    f.lookup.set_error("cpp_scheduled_lookup_table", "deadline exceeded");
    auto input = f.make_input("data");

    auto event = run_transform(f.action, f.tc.action_context, input);

    CHECK(event.type == "ERROR");
    CHECK((event.error->cause.find("lookup") != std::string::npos ||
           event.error->cause.find("RGB") != std::string::npos));
}

TEST_CASE("Transform no RGB results", "[transform]") {
    TransformFixture f;
    LookupResults empty_rgb;
    empty_rgb.total_count = 0;
    f.lookup.set_results("cpp_scheduled_lookup_table", empty_rgb);
    auto input = f.make_input("data");

    auto event = run_transform(f.action, f.tc.action_context, input);
    require_error(event, "No RGB results found");
}

TEST_CASE("Transform artifact metadata", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("hello");

    f.action.extra_metadata_for_testing().store({
        {"custom", "value"},
        {"another", "meta"},
    });

    auto event = run_transform(f.action, f.tc.action_context, input);
    auto& te = require_transform(event);

    CHECK(te[0].metadata.at("custom") == "value");
    CHECK(te[0].metadata.at("another") == "meta");
}

TEST_CASE("Transform no artifact metadata", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("hello");

    auto event = run_transform(f.action, f.tc.action_context, input);
    require_transform(event);
}

TEST_CASE("Transform filter prefix takes priority", "[transform]") {
    TransformFixture f("2-did");
    auto input = f.make_input("hello");

    auto event = run_transform(f.action, f.tc.action_context, input);
    require_filter(event, "Filter due to did_filter_prefix match");
}

TEST_CASE("Transform neither prefix matches", "[transform]") {
    TransformFixture f;
    auto input = f.make_input("hello");

    auto event = run_transform(f.action, f.tc.action_context, input);
    require_transform(event);
}
