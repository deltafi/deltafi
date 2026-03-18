// ABOUTME: Tests for HelloWorldCppEgressAction.
// ABOUTME: Covers normal egress, missing content, and error-string detection.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/testing.hpp>
#include "actions/egress.hpp"

using namespace deltafi;
using namespace deltafi::testing;

TEST_CASE("Egress normal", "[egress]") {
    auto tc = new_test_context("test-did");
    HelloWorldCppEgressAction action;

    EgressInput input;
    input.content.push_back(tc.content("test.txt", "text/plain", "hello world"));
    input.metadata = {{"key1", "val1"}, {"key2", "val2"}};

    auto event = run_egress(action, tc.action_context, input);
    require_egress(event);

    CHECK(event.messages.size() >= 3);
}

TEST_CASE("Egress no content", "[egress]") {
    auto tc = new_test_context("test-did");
    HelloWorldCppEgressAction action;

    EgressInput input;

    auto event = run_egress(action, tc.action_context, input);
    require_error(event, "No content provided");
}

TEST_CASE("Egress content contains error", "[egress]") {
    auto tc = new_test_context("test-did");
    HelloWorldCppEgressAction action;

    EgressInput input;
    input.content.push_back(tc.content("test.txt", "text/plain", "this has an error in it"));

    auto event = run_egress(action, tc.action_context, input);
    auto& err = require_error(event, "Failed to egress");
    CHECK(err.context.find("error in content") != std::string::npos);
}

TEST_CASE("Egress content without error string", "[egress]") {
    auto tc = new_test_context("test-did");
    HelloWorldCppEgressAction action;

    EgressInput input;
    input.content.push_back(tc.content("output.txt", "text/plain", "clean content with no issues"));

    auto event = run_egress(action, tc.action_context, input);
    require_egress(event);
}
