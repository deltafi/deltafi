// ABOUTME: Tests that the test runners drive the real production dispatch path.
// ABOUTME: Covers orphan GC, param deserialization, the join merge->transform, and fault injection.

#include <cstdint>
#include <map>
#include <string>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/params.hpp"
#include "deltafi/result.hpp"
#include "deltafi/testing.hpp"

using namespace deltafi;
using namespace deltafi::testing;

namespace {

// Saves scratch content (tracked on the context) then errors — the result references nothing, so
// the scratch is an orphan the dispatch path must GC.
struct OrphanTransform {
    static TransformResultType transform(ActionContext& ctx, TransformInput& /*input*/) {
        save_content(*ctx.content_service, ctx, std::vector<uint8_t>{'x', 'y', 'z'}, "scratch.bin",
                     "application/octet-stream");
        return ErrorResult(ctx, "boom");
    }
};

struct EchoParams {
    std::string tag = "default";
    DELTAFI_PARAMS(EchoParams, FIELD(tag, "tag echoed into metadata"));
};

// Echoes its bound parameter into result metadata, so a test can prove params were deserialized.
struct ParamEcho {
    using Params = EchoParams;
    static TransformResultType transform(ActionContext& ctx, EchoParams& params,
                                         TransformInput& /*in*/) {
        TransformResult result(ctx);
        result.add_metadata("tag", params.tag);
        return result;
    }
};

// A join action: merges all inputs' content, then the transform reports how many it received.
struct JoinConcat {
    static TransformInput join(std::vector<TransformInput> inputs) {
        TransformInput merged;
        for (auto& in : inputs) {
            for (auto& c : in.content) merged.content.push_back(std::move(c));
        }
        merged.metadata["joined"] = std::to_string(inputs.size());
        return merged;
    }
    static TransformResultType transform(ActionContext& ctx, TransformInput& input) {
        TransformResult result(ctx);
        result.add_metadata("merged_count", std::to_string(input.content.size()));
        for (auto& c : input.content) result.add_content(c);
        return result;
    }
};

// Saves content unconditionally — used with fail_after_nth to exercise the error path.
struct SavingTransform {
    static TransformResultType transform(ActionContext& ctx, TransformInput& /*input*/) {
        TransformResult result(ctx);
        result.save_content(std::string("data"), "out.bin", "application/octet-stream");
        return result;
    }
};

}  // namespace

template<>
struct deltafi::action_traits<JoinConcat> {
    static constexpr const char* name = "JoinConcat";
    static constexpr const char* description = "test";
};

TEST_CASE("run_transform GCs orphan content saved by an erroring action", "[runner]") {
    auto tc = new_test_context("gc-did");
    OrphanTransform action;
    TransformInput input;

    auto event = run_transform(action, tc.action_context, input);

    require_error(event, "boom");
    CHECK(tc.store->size() == 0);        // the scratch content was orphan-collected
    CHECK(tc.store->save_count() == 1);  // it was in fact saved before being collected
}

TEST_CASE("run_transform deserializes explicit params onto the dispatch path", "[runner]") {
    auto tc = new_test_context("param-did");
    ParamEcho action;
    EchoParams params;
    params.tag = "hello";
    TransformInput input;

    auto event = run_transform(action, tc.action_context, params, input);

    const auto& transforms = require_transform(event);
    REQUIRE(transforms.size() == 1);
    CHECK(transforms[0].metadata.at("tag") == "hello");
}

TEST_CASE("run_join merges inputs then runs the transform once", "[runner]") {
    auto tc = new_test_context("join-did");
    tc.action_context.join = JoinConfiguration{};  // executor only joins when join is present

    JoinConcat action;
    std::vector<TransformInput> inputs;
    for (int i = 0; i < 3; ++i) {
        TransformInput in;
        in.content.push_back(tc.content("part.txt", "text/plain", "chunk"));
        inputs.push_back(std::move(in));
    }

    auto event = run_join(action, tc.action_context, inputs);

    const auto& transforms = require_transform(event);
    REQUIRE(transforms.size() == 1);
    CHECK(transforms[0].metadata.at("merged_count") == "3");
}

TEST_CASE("fail_after_nth makes a save throw, exercising the action error path", "[runner]") {
    auto tc = new_test_context("fault-did");
    tc.store->fail_after_nth(1);  // the very next save throws

    SavingTransform action;
    TransformInput input;

    auto event = run_transform(action, tc.action_context, input);

    require_error(event);  // the injected save failure became an ErrorResult via the fault barrier
}
