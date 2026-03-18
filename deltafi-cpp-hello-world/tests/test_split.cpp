// ABOUTME: Tests for HelloWorldCppSplitTransformAction.
// ABOUTME: Covers child creation, artifact check, no content, and child metadata/annotation.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/testing.hpp>
#include "actions/split.hpp"

using namespace deltafi;
using namespace deltafi::testing;

TEST_CASE("Split default two children", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "original data"));

    auto event = run_transform(action, tc.action_context, input);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 2);

    CHECK(te[0].name == "parent-0");
    CHECK(te[1].name == "parent-1");

    // Both children share the same transformed content
    auto data0 = te[0].content[0].load_string();
    CHECK(data0.find("original data") != std::string::npos);
    CHECK(data0.find("HelloWorldCppSplitTransformAction spawned me") != std::string::npos);

    auto data1 = te[1].content[0].load_string();
    CHECK(data0 == data1);

    // Both children have metadata
    CHECK(te[0].metadata.at("transformManyKey") == "manyValue");
    CHECK(te[1].metadata.at("transformManyKey") == "manyValue");

    // Annotations: childIndex
    CHECK(te[0].annotations.at("childIndex") == "0");
    CHECK(te[1].annotations.at("childIndex") == "1");
}

TEST_CASE("Split three children", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    HelloWorldCppSplitTransformActionParams params;
    params.number_of_children = 3;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 3);
    CHECK(te[0].name == "parent-0");
    CHECK(te[1].name == "parent-1");
    CHECK(te[2].name == "parent-2");
}

TEST_CASE("Split one child", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    HelloWorldCppSplitTransformActionParams params;
    params.number_of_children = 1;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 1);
    CHECK(te[0].name == "parent-0");
    CHECK(te[0].annotations.at("childIndex") == "0");
}

TEST_CASE("Split no content", "[split]") {
    auto tc = new_test_context("split-did");
    HelloWorldCppSplitTransformAction action;

    TransformInput input;

    auto event = run_transform(action, tc.action_context, input);
    require_error(event, "No content provided");
}

TEST_CASE("Split odd children get did metadata", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    HelloWorldCppSplitTransformActionParams params;
    params.number_of_children = 4;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 4);

    // Even indices (0, 2) should NOT have "did" metadata
    CHECK(te[0].metadata.find("did") == te[0].metadata.end());
    CHECK(te[2].metadata.find("did") == te[2].metadata.end());

    // Odd indices (1, 3) should have "did" metadata set to their child DID
    CHECK(te[1].metadata.count("did") == 1);
    CHECK(te[3].metadata.count("did") == 1);
    CHECK(!te[1].metadata.at("did").empty());
    CHECK(!te[3].metadata.at("did").empty());
}

TEST_CASE("Split children have unique DIDs", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    HelloWorldCppSplitTransformActionParams params;
    params.number_of_children = 3;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 3);

    // Each child should have a unique DID
    CHECK(te[0].did != te[1].did);
    CHECK(te[0].did != te[2].did);
    CHECK(te[1].did != te[2].did);

    // Child DIDs should differ from parent DID
    CHECK(te[0].did != "split-did");
    CHECK(te[1].did != "split-did");
    CHECK(te[2].did != "split-did");
}

TEST_CASE("Split artifact not found uploads default", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    MockArtifactClient artifacts;
    tc.action_context.artifact_client = &artifacts;

    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    auto event = run_transform(action, tc.action_context, input);
    require_transform(event);

    // Artifact was not pre-stored, so ensure_artifact should have published it
    CHECK(artifacts.publish_count() == 1);
}

TEST_CASE("Split artifact already exists skips upload", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    MockArtifactClient artifacts;
    FetchedArtifact existing;
    existing.data = std::vector<uint8_t>{'h', 'i'};
    artifacts.set_artifact("hello.txt", "", existing);
    tc.action_context.artifact_client = &artifacts;

    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    auto event = run_transform(action, tc.action_context, input);
    require_transform(event);

    // Artifact was already present, so no publish should have occurred
    CHECK(artifacts.publish_count() == 0);
}

TEST_CASE("Split artifact check only once", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    MockArtifactClient artifacts;
    tc.action_context.artifact_client = &artifacts;

    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    // First call: artifact not found, publishes default
    auto event1 = run_transform(action, tc.action_context, input);
    require_transform(event1);
    CHECK(artifacts.publish_count() == 1);

    // Second call: artifact_checked_ flag is true, no additional publish
    auto event2 = run_transform(action, tc.action_context, input);
    require_transform(event2);
    CHECK(artifacts.publish_count() == 1);
}

TEST_CASE("Split zero children defaults to two", "[split]") {
    auto tc = new_test_context("split-did");
    tc.action_context.delta_file_name = "parent";
    HelloWorldCppSplitTransformAction action;

    TransformInput input;
    input.content.push_back(tc.content("input.txt", "text/plain", "data"));

    HelloWorldCppSplitTransformActionParams params;
    params.number_of_children = 0;

    auto result = action.transform(tc.action_context, params, input);
    auto event = result_event(result, tc.action_context);
    auto& te = require_transform(event);

    REQUIRE(te.size() == 2);
}
