// ABOUTME: Tests the richer test-framework assertions, input builders, and mock-client wiring.
// ABOUTME: Ensures the helpers accept matches, reject mismatches, and drive actions correctly.

#include <string>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/result.hpp"
#include "deltafi/testing.hpp"

using namespace deltafi;
using namespace deltafi::testing;

namespace {

// Produces content, metadata, an annotation, a metric, and a log message for the assertions.
struct RichTransform {
    static TransformResultType transform(ActionContext& ctx, TransformInput& /*in*/) {
        TransformResult result(ctx);
        result.save_content(std::string("hello"), "out.txt", "text/plain");
        result.add_metadata("k", "v");
        result.add_annotation("a", "b");
        result.add_metric(Metric{.name = "count", .value = 1, .tags = {}});
        result.log_info("something happened");
        return result;
    }
};

// Reflects the wired lookup client's result into metadata.
struct LookupEcho {
    static TransformResultType transform(ActionContext& ctx, TransformInput& /*in*/) {
        auto results = ctx.lookup_client->lookup("t");
        TransformResult result(ctx);
        result.add_metadata("total", std::to_string(results.total_count));
        return result;
    }
};

}  // namespace

TEST_CASE("richer assertions accept matching results", "[assertions]") {
    auto tc = new_test_context("assert-did");
    RichTransform action;
    TransformInput input;

    auto event = run_transform(action, tc.action_context, input);

    const auto& te = require_single_transform(event);
    require_metadata(te, "k", "v");
    require_annotation(te, "a", "b");
    CHECK(require_content(te, "out.txt").media_type == "text/plain");
    CHECK(require_metric(event, "count").value == 1);
    CHECK(require_message(event, "something happened").message == "something happened");
}

TEST_CASE("richer assertions reject mismatches", "[assertions]") {
    auto tc = new_test_context("assert-did");
    RichTransform action;
    TransformInput input;
    auto event = run_transform(action, tc.action_context, input);
    const auto& te = require_single_transform(event);

    CHECK_THROWS(require_metadata(te, "k", "wrong"));
    CHECK_THROWS(require_metadata(te, "missing", "v"));
    CHECK_THROWS(require_annotation(te, "a", "wrong"));
    CHECK_THROWS(require_content(te, "nope.txt"));
    CHECK_THROWS(require_metric(event, "no-such-metric"));
    CHECK_THROWS(require_message(event, "not logged"));
}

TEST_CASE("new_deltafile builds a usable transform input", "[assertions]") {
    auto tc = new_test_context("builder-did");
    auto input = new_deltafile(tc, "payload", "in.txt", "text/plain", {{"source", "test"}});

    REQUIRE(input.content.size() == 1);
    CHECK(input.content[0].name == "in.txt");
    CHECK(input.content[0].load_string() == "payload");
    CHECK(input.metadata.at("source") == "test");
}

TEST_CASE("use_lookup wires a mock client reachable by the action", "[assertions]") {
    auto tc = new_test_context("wire-did");
    MockLookupClient lookup;
    LookupResults results;
    results.total_count = 7;
    lookup.set_results("t", results);
    tc.use_lookup(lookup);

    LookupEcho action;
    TransformInput input;
    auto event = run_transform(action, tc.action_context, input);

    require_metadata(require_single_transform(event), "total", "7");
}
