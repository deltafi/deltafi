// ABOUTME: Tests for HelloWorldCppTimedIngressAction.
// ABOUTME: Covers memo-based index tracking, conditional metadata, and join-group assignment.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/testing.hpp>
#include "actions/timed_ingress.hpp"

using namespace deltafi;
using namespace deltafi::testing;

namespace {

ActionEvent run_ingress_with_memo(HelloWorldCppTimedIngressAction& action,
                                  const std::string& memo) {
    auto tc = new_test_context("ingress-did");
    tc.action_context.flow_name = "hello-world-cpp-timed-data-source";
    tc.action_context.memo = memo;
    return run_ingress(action, tc.action_context);
}

} // namespace

TEST_CASE("TimedIngress first run", "[timed_ingress]") {
    HelloWorldCppTimedIngressAction action;
    auto tc = new_test_context("ingress-did");
    tc.action_context.flow_name = "hello-world-cpp-timed-data-source";

    auto event = run_ingress(action, tc.action_context);
    auto& ingress = require_ingress(event);

    CHECK(ingress.memo == "0");
    CHECK_FALSE(ingress.execute_immediate);
    REQUIRE(ingress.ingress_items.size() == 1);

    auto& item = ingress.ingress_items[0];
    CHECK(item.delta_file_name == "hello-world-cpp-timed-data-source-0");
    CHECK(item.metadata.at("index") == "0");

    // index 0: index%3 == 0, no hello metadata
    CHECK(item.metadata.find("hello") == item.metadata.end());

    REQUIRE(item.content.size() == 1);
    auto data = item.content[0].load_string();
    CHECK(data == "Some content, part 0");
}

TEST_CASE("TimedIngress increments memo", "[timed_ingress]") {
    HelloWorldCppTimedIngressAction action;
    auto event = run_ingress_with_memo(action, "4");
    auto& ingress = require_ingress(event);

    CHECK(ingress.memo == "5");
    CHECK(ingress.ingress_items[0].metadata.at("index") == "5");
}

TEST_CASE("TimedIngress index 1 transform metadata", "[timed_ingress]") {
    HelloWorldCppTimedIngressAction action;
    auto event = run_ingress_with_memo(action, "0"); // memo 0 -> index 1
    auto& item = require_ingress(event).ingress_items[0];

    CHECK(item.metadata.at("hello") == "transform");
    CHECK(item.annotations.at("helloMode") == "basic");
}

TEST_CASE("TimedIngress index 2 join metadata", "[timed_ingress]") {
    HelloWorldCppTimedIngressAction action;
    auto event = run_ingress_with_memo(action, "1"); // memo 1 -> index 2
    auto& item = require_ingress(event).ingress_items[0];

    CHECK(item.metadata.at("hello") == "join");
    CHECK(item.annotations.at("helloMode") == "advanced");
    CHECK(item.metadata.at("join-group") == "even");
}

TEST_CASE("TimedIngress index 5 join group odd", "[timed_ingress]") {
    HelloWorldCppTimedIngressAction action;
    auto event = run_ingress_with_memo(action, "4"); // memo 4 -> index 5
    auto& item = require_ingress(event).ingress_items[0];

    CHECK(item.metadata.at("hello") == "join");
    CHECK(item.metadata.at("join-group") == "odd");
}

TEST_CASE("TimedIngress all index mod patterns", "[timed_ingress]") {
    HelloWorldCppTimedIngressAction action;

    struct TestCase {
        std::string memo;
        int want_index;
        std::string want_hello;
        std::string want_group;
    };

    std::vector<TestCase> cases = {
        {"",  0, "",          ""},      // index%3==0: no hello
        {"0", 1, "transform", ""},      // index%3==1: transform
        {"1", 2, "join",      "even"},  // index%3==2, even
        {"2", 3, "",          ""},      // index%3==0
        {"3", 4, "transform", ""},      // index%3==1
        {"4", 5, "join",      "odd"},   // index%3==2, odd
    };

    for (const auto& tt : cases) {
        DYNAMIC_SECTION("memo=" << tt.memo) {
            auto event = run_ingress_with_memo(action, tt.memo);
            auto& item = require_ingress(event).ingress_items[0];

            int got_index = std::stoi(item.metadata.at("index"));
            CHECK(got_index == tt.want_index);
            if (tt.want_hello.empty()) {
                CHECK(item.metadata.find("hello") == item.metadata.end());
            } else {
                CHECK(item.metadata.at("hello") == tt.want_hello);
            }
            if (!tt.want_group.empty()) {
                CHECK(item.metadata.at("join-group") == tt.want_group);
            }
        }
    }
}
