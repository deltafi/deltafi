// ABOUTME: Tests for HelloWorldCppJoiningTransformAction.
// ABOUTME: Covers join aggregation, transform with merged content, threshold annotation, and error cases.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/testing.hpp>
#include "actions/join.hpp"

using namespace deltafi;
using namespace deltafi::testing;

TEST_CASE("Join reverse order and sum index", "[join]") {
    auto tc = new_test_context("join-did");
    HelloWorldCppJoiningTransformAction action;

    std::vector<TransformInput> inputs(3);
    inputs[0].content.push_back(tc.content("part1.txt", "text/plain", "first"));
    inputs[0].metadata = {{"index", "10"}, {"key", "a"}};
    inputs[1].content.push_back(tc.content("part2.txt", "text/plain", "second"));
    inputs[1].metadata = {{"index", "20"}, {"key", "b"}};
    inputs[2].content.push_back(tc.content("part3.txt", "text/plain", "third"));
    inputs[2].metadata = {{"index", "30"}, {"key", "c"}};

    auto merged = action.join(std::move(inputs));

    // Content should be reversed: third, second, first
    REQUIRE(merged.content.size() == 3);
    CHECK(merged.content[0].name == "part3.txt");
    CHECK(merged.content[1].name == "part2.txt");
    CHECK(merged.content[2].name == "part1.txt");

    // indexSum = 10+20+30 = 60
    CHECK(merged.metadata.at("indexSum") == "60");

    // First inserted metadata wins (std::map::insert skips duplicates); reverse order inserts input[2] first
    CHECK(merged.metadata.at("key") == "c");
}

TEST_CASE("JoinTransform merges content and metadata", "[join]") {
    auto tc = new_test_context("join-did");
    HelloWorldCppJoiningTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("p1.txt", "text/plain", "AAA"));
    input.content.push_back(tc.content("p2.txt", "text/plain", "BBB"));
    input.metadata = {{"indexSum", "25"}, {"hello", "join"}};

    HelloWorldCppJoiningTransformActionParams params;
    params.index_threshold = 50;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 1);
    CHECK(te[0].content[0].name == "single-25");
    CHECK(te[0].content[0].has_tag("joined"));

    auto data = te[0].content[0].load_string();
    CHECK(data.find("AAA") != std::string::npos);
    CHECK(data.find("BBB") != std::string::npos);

    CHECK(te[0].metadata.at("indexSum") == "25");
    CHECK(te[0].metadata.at("hello") == "join");

    // indexSum 25 < threshold 50 -> no annotation
    CHECK(te[0].annotations.find("indexThresholdMet") == te[0].annotations.end());
}

TEST_CASE("JoinTransform threshold met", "[join]") {
    auto tc = new_test_context("join-did");
    HelloWorldCppJoiningTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("p.txt", "text/plain", "data"));
    input.metadata = {{"indexSum", "100"}};

    HelloWorldCppJoiningTransformActionParams params;
    params.index_threshold = 50;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    CHECK(te[0].annotations.at("indexThresholdMet") == "true");
}

TEST_CASE("JoinTransform threshold exactly met", "[join]") {
    auto tc = new_test_context("join-did");
    HelloWorldCppJoiningTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("p.txt", "text/plain", "data"));
    input.metadata = {{"indexSum", "50"}};

    HelloWorldCppJoiningTransformActionParams params;
    params.index_threshold = 50;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    CHECK(te[0].annotations.at("indexThresholdMet") == "true");
}

TEST_CASE("JoinTransform default threshold", "[join]") {
    auto tc = new_test_context("join-did");
    HelloWorldCppJoiningTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("p.txt", "text/plain", "data"));
    input.metadata = {{"indexSum", "49"}};

    auto event = run_transform(action, tc.action_context, input);
    auto& te = require_transform(event);

    CHECK(te[0].annotations.find("indexThresholdMet") == te[0].annotations.end());
}

TEST_CASE("JoinTransform end to end", "[join]") {
    auto tc = new_test_context("join-did");
    HelloWorldCppJoiningTransformAction action;

    std::vector<TransformInput> inputs(2);
    inputs[0].content.push_back(tc.content("p1.txt", "text/plain", "alpha"));
    inputs[0].metadata = {{"index", "30"}};
    inputs[1].content.push_back(tc.content("p2.txt", "text/plain", "beta"));
    inputs[1].metadata = {{"index", "25"}};

    // Join phase
    auto merged = action.join(std::move(inputs));
    bind_content_service(merged.content, tc.store.get());

    // Transform phase
    HelloWorldCppJoiningTransformActionParams params;
    params.index_threshold = 50;

    auto result = action.transform(tc.action_context, params, merged);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    CHECK(te[0].annotations.at("indexThresholdMet") == "true");

    // Content should be reversed: beta then alpha
    auto data = te[0].content[0].load_string();
    auto beta_idx = data.find("beta");
    auto alpha_idx = data.find("alpha");
    REQUIRE(beta_idx != std::string::npos);
    REQUIRE(alpha_idx != std::string::npos);
    CHECK(beta_idx < alpha_idx);
}
