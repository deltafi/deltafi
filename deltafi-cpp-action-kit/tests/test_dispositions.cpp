// ABOUTME: Tests filter-on-egress/ingress, thrown Error/Filter dispositions, and error context.
// ABOUTME: Uses the testing runners to exercise result-to-event mapping for each action type.

#include <catch2/catch_test_macros.hpp>

#include <stdexcept>

#include <deltafi/action.hpp>
#include <deltafi/errors.hpp>
#include <deltafi/result.hpp>
#include <deltafi/testing.hpp>

using namespace deltafi;

// Minimal egress action that always filters.
struct FilteringEgress {
    static EgressResultType egress(ActionContext& ctx, EgressInput& /*in*/) {
        return FilterResult(ctx, "egress filtered");
    }
};
template<>
struct deltafi::action_traits<FilteringEgress> {
    static constexpr const char* name = "FilteringEgress";
    static constexpr const char* description = "test";
};

// Minimal timed-ingress action that always filters.
struct FilteringIngress {
    static IngressResultType ingress(ActionContext& ctx) {
        return FilterResult(ctx, "ingress filtered");
    }
};
template<>
struct deltafi::action_traits<FilteringIngress> {
    static constexpr const char* name = "FilteringIngress";
    static constexpr const char* description = "test";
};

TEST_CASE("egress action can return a filter") {
    auto tc = testing::new_test_context("did-eg-filter");
    FilteringEgress action;
    EgressInput input;
    auto event = testing::run_egress(action, tc.action_context, input);
    const auto& f = testing::require_filter(event, "egress filtered");
    CHECK(f.message == "egress filtered");
}

TEST_CASE("timed ingress action can return a filter") {
    auto tc = testing::new_test_context("did-in-filter");
    FilteringIngress action;
    auto event = testing::run_ingress(action, tc.action_context);
    const auto& f = testing::require_filter(event, "ingress filtered");
    CHECK(f.message == "ingress filtered");
}

TEST_CASE("ErrorResultException converts to an ERROR event with all fields") {
    auto tc = testing::new_test_context("did-throw-err");
    ErrorResultException ex("boom", "detail here");
    ex.add_annotation("severity", "high")
        .add_metric(Metric{.name = "errors", .value = 1, .tags = {}});

    auto event = ex.to_result(tc.action_context).to_action_event(tc.action_context.start_time);
    const auto& err = testing::require_error(event, "boom");
    CHECK(err.context == "detail here");
    CHECK(err.annotations.at("severity") == "high");
    REQUIRE(event.metrics.size() == 1);
    CHECK(event.metrics[0].name == "errors");
}

TEST_CASE("FilterResultException converts to a FILTER event with context") {
    auto tc = testing::new_test_context("did-throw-filt");
    FilterResultException ex("nope", "why");
    ex.add_annotation("reason", "rule");

    auto event = ex.to_result(tc.action_context).to_action_event(tc.action_context.start_time);
    const auto& filter = testing::require_filter(event, "nope");
    CHECK(filter.context == "why");
    CHECK(filter.annotations.at("reason") == "rule");
}

TEST_CASE("describe_exception includes type and message") {
    std::runtime_error ex("kaboom");
    auto desc = describe_exception(ex);
    CHECK(desc.find("kaboom") != std::string::npos);
    CHECK(desc.find("runtime_error") != std::string::npos);
}
