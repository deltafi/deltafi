// ABOUTME: Tests orphan-content GC: unreferenced saved segments are removed, including on throw.
// ABOUTME: Uses the in-memory store to verify segment presence after cleanup.

#include <stdexcept>

#include <catch2/catch_test_macros.hpp>

#include <deltafi/registry.hpp>
#include <deltafi/testing.hpp>

using namespace deltafi;

TEST_CASE("cleanup_orphans removes unreferenced saved segments") {
    auto tc = testing::new_test_context("did-orphan");
    auto& actx = tc.action_context;

    auto kept = tc.store->save(actx.did, {1, 2, 3});
    auto orphan = tc.store->save(actx.did, {4, 5, 6});
    actx.track_segment(kept);
    actx.track_segment(orphan);

    cleanup_orphans(actx, std::vector<Segment>{kept});  // only 'kept' referenced

    CHECK(tc.store->load({kept}) == std::vector<uint8_t>{1, 2, 3});
    CHECK_THROWS(tc.store->load({orphan}));
}

TEST_CASE("cleanup_orphans with nothing saved is a safe no-op") {
    auto tc = testing::new_test_context("did-empty");
    CHECK_NOTHROW(cleanup_orphans(tc.action_context, {}));
}

TEST_CASE("OrphanGuard removes all saved segments when unwinding on throw") {
    auto tc = testing::new_test_context("did-guard");
    auto& actx = tc.action_context;
    auto seg = tc.store->save(actx.did, {9, 9});
    actx.track_segment(seg);

    try {
        // referenced empty -> all saved are orphans
        OrphanGuard guard{.actx = actx, .referenced = {}};
        throw std::runtime_error("boom");
    } catch (const std::exception&) {  // NOLINT(bugprone-empty-catch): guard cleans up on unwind
    }

    CHECK_THROWS(tc.store->load({seg}));
}

TEST_CASE("OrphanGuard with a null content_service does not crash") {
    ActionContext actx;
    actx.content_service = nullptr;
    actx.track_segment(Segment{.uuid = "uuid-x", .offset = 0, .size = 3, .did = "did-y"});
    CHECK_NOTHROW([&] { OrphanGuard guard{actx, {}}; }());
}
