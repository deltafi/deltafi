// ABOUTME: Tests for the result/event layer: TRACE, error text, filter context, merges, metrics.
// ABOUTME: Exercises ResultBase and the *Result types through their ActionEvent conversion.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/action.hpp>
#include <deltafi/input.hpp>
#include <deltafi/result.hpp>
#include <deltafi/testing.hpp>

using namespace deltafi;

TEST_CASE("log_trace serializes as TRACE") {
    auto tc = testing::new_test_context("did-trace");
    TransformResult result(tc.action_context);
    result.log_trace("tracing here");

    auto event = result.to_action_event(tc.action_context.start_time);
    REQUIRE(event.messages.size() == 1);
    nlohmann::json j = event.messages[0];
    CHECK(j["severity"] == "TRACE");
    CHECK(j["message"] == "tracing here");
}

TEST_CASE("input accessor errors use canonical text") {
    TransformInput input;  // empty content, empty metadata

    try {
        input.get_metadata("missing-key");
        FAIL("expected MissingMetadataError");
    } catch (const MissingMetadataError& e) {
        CHECK(std::string(e.what()) == "Missing metadata with key missing-key");
    }

    try {
        input.first_content();
        FAIL("expected ExpectedContentError");
    } catch (const ExpectedContentError& e) {
        CHECK(std::string(e.what()) == "Action received no content");
    }
}

TEST_CASE("filter result carries context") {
    auto tc = testing::new_test_context("did-filter");
    FilterResult result(tc.action_context, "dropped by design");
    result.set_context("matched rule 7");

    auto event = result.to_action_event(tc.action_context.start_time);
    const auto& filter = testing::require_filter(event, "dropped by design");
    CHECK(filter.context == "matched rule 7");

    nlohmann::json j = event;
    CHECK(j["filter"]["context"] == "matched rule 7");
}

TEST_CASE("filter result without context omits the JSON key") {
    auto tc = testing::new_test_context("did-filter2");
    FilterResult result(tc.action_context, "dropped");
    nlohmann::json j = result.to_action_event(tc.action_context.start_time);
    CHECK_FALSE(j["filter"].contains("context"));
}

TEST_CASE("bulk add_metadata and add_annotations are later-wins") {
    auto tc = testing::new_test_context("did-merge");
    TransformResult result(tc.action_context);
    result.add_metadata("k", "first");
    result.add_metadata(std::map<std::string, std::string>{{"k", "second"}, {"other", "x"}});
    result.add_annotation("a", "first");
    result.add_annotations(std::map<std::string, std::string>{{"a", "second"}});
    result.add_metadata(std::map<std::string, std::string>{});  // empty is a no-op

    auto event = result.to_action_event(tc.action_context.start_time);
    const auto& t = testing::require_transform(event);
    REQUIRE(t.size() == 1);
    CHECK(t[0].metadata.at("k") == "second");
    CHECK(t[0].metadata.at("other") == "x");
    CHECK(t[0].annotations.at("a") == "second");
}

TEST_CASE("default_join merges metadata later-wins") {
    TransformInput a;
    a.metadata = {{"k", "left"}, {"only_a", "1"}};
    TransformInput b;
    b.metadata = {{"k", "right"}, {"only_b", "2"}};

    std::vector<TransformInput> inputs;
    inputs.push_back(std::move(a));
    inputs.push_back(std::move(b));

    auto merged = default_join(std::move(inputs));
    CHECK(merged.metadata.at("k") == "right");
    CHECK(merged.metadata.at("only_a") == "1");
    CHECK(merged.metadata.at("only_b") == "2");
}

TEST_CASE("add_metric appears on the action event") {
    auto tc = testing::new_test_context("did-metric");
    TransformResult result(tc.action_context);
    result.add_metric(
        Metric{.name = "files_processed", .value = 3, .tags = {{"stage", "transform"}}});

    auto event = result.to_action_event(tc.action_context.start_time);
    REQUIRE(event.metrics.size() == 1);
    CHECK(event.metrics[0].name == "files_processed");
    CHECK(event.metrics[0].value == 3);

    nlohmann::json j = event;
    CHECK(j["metrics"][0]["name"] == "files_processed");
    CHECK(j["metrics"][0]["tags"]["stage"] == "transform");
}

TEST_CASE("no metric omits the metrics key") {
    auto tc = testing::new_test_context("did-nometric");
    TransformResult result(tc.action_context);
    nlohmann::json j = result.to_action_event(tc.action_context.start_time);
    CHECK_FALSE(j.contains("metrics"));
}
