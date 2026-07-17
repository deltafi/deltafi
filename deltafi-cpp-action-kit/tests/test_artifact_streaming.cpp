// ABOUTME: Tests the artifact client's streaming fetch and reader-based publish.
// ABOUTME: Runs against an in-process fake core artifact server (no live backend).

#include <array>
#include <chrono>
#include <cstddef>
#include <istream>
#include <iterator>
#include <mutex>
#include <sstream>
#include <streambuf>
#include <string>
#include <thread>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/artifact.hpp"

#include "fake_artifact_server.hpp"

using deltafi::ArtifactClient;
using deltafi::test::FakeArtifactServer;

namespace {
std::string drain(std::istream& in) {
    return {std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>()};
}

// An istream that synthesizes `total` copies of `fill` from a small fixed buffer, so
// the whole payload never exists in memory at once — proving publish streams its source.
class GeneratingStreamBuf : public std::streambuf {
public:
    GeneratingStreamBuf(char fill, size_t total) : total_(total) { buffer_.fill(fill); }

private:
    int_type underflow() override {
        if (produced_ >= total_) return traits_type::eof();
        size_t n = std::min(buffer_.size(), total_ - produced_);
        produced_ += n;
        setg(buffer_.data(), buffer_.data(), buffer_.data() + n);
        return traits_type::to_int_type(*gptr());
    }

    std::array<char, 8192> buffer_{};
    size_t total_;
    size_t produced_ = 0;
};
}  // namespace

TEST_CASE("stream_artifact yields the manifest then the body incrementally", "[artifact]") {
    FakeArtifactServer server;
    std::string body(200000, 'a');
    server.set_artifact(body, "v7");

    ArtifactClient client(server.url(), "owner1");
    auto stream = client.stream_artifact("model.bin");

    CHECK(stream.manifest.version == "v7");
    REQUIRE(stream.reader != nullptr);
    CHECK(drain(*stream.reader) == body);
}

TEST_CASE("stream_artifact throws when the artifact is missing", "[artifact]") {
    FakeArtifactServer server;  // no set_artifact -> 404
    ArtifactClient client(server.url(), "owner1");
    CHECK_THROWS(client.stream_artifact("missing.bin"));
}

TEST_CASE("get_artifact resolves by scope with plugin-first fallback", "[artifact]") {
    FakeArtifactServer server;
    server.set_artifact_for("shared", "shared-bytes", "s1");  // only the shared namespace has it

    ArtifactClient client(server.url(), "owner1");

    SECTION("PLUGIN_FIRST falls back to shared when the plugin namespace lacks it") {
        auto fetched = client.get_artifact("a.bin", "", deltafi::ArtifactScope::PLUGIN_FIRST);
        CHECK(fetched.data_string() == "shared-bytes");
    }
    SECTION("PLUGIN_ONLY does not fall back") {
        CHECK_THROWS(client.get_artifact("a.bin", "", deltafi::ArtifactScope::PLUGIN_ONLY));
    }
    SECTION("SHARED_ONLY resolves directly from shared") {
        auto fetched = client.get_artifact("a.bin", "", deltafi::ArtifactScope::SHARED_ONLY);
        CHECK(fetched.data_string() == "shared-bytes");
    }
    SECTION("get_shared_artifact resolves from shared") {
        auto fetched = client.get_shared_artifact("a.bin");
        CHECK(fetched.data_string() == "shared-bytes");
    }
}

TEST_CASE("stream_artifact resolves by scope with plugin-first fallback", "[artifact]") {
    FakeArtifactServer server;
    server.set_artifact_for("shared", "shared-bytes", "s1");

    ArtifactClient client(server.url(), "owner1");
    auto stream = client.stream_artifact("a.bin");  // PLUGIN_FIRST falls back to shared

    CHECK(stream.manifest.version == "s1");
    REQUIRE(stream.reader != nullptr);
    CHECK(drain(*stream.reader) == "shared-bytes");
}

TEST_CASE("get_artifact PLUGIN_FIRST prefers the plugin namespace when present", "[artifact]") {
    FakeArtifactServer server;
    server.set_artifact_for("owner1", "plugin-bytes", "p1");
    server.set_artifact_for("shared", "shared-bytes", "s1");

    ArtifactClient client(server.url(), "owner1");
    auto fetched = client.get_artifact("a.bin");  // default scope PLUGIN_FIRST
    CHECK(fetched.data_string() == "plugin-bytes");
}

TEST_CASE("poll-drift re-fetches and fires callbacks when a cached version drifts", "[artifact]") {
    using namespace std::chrono_literals;
    FakeArtifactServer server;
    server.set_artifact_for("owner1", "body-v1", "v1");
    server.set_listing("owner1", "cfg.json", "active", "v1");

    ArtifactClient client(server.url(), "owner1", 25ms);  // fast poll interval

    std::mutex mu;
    std::string last_version;
    client.on_update_fetch("cfg.json", [&](const deltafi::FetchedArtifact* a, const std::string&) {
        if (a != nullptr) {
            std::scoped_lock lock(mu);
            last_version = a->manifest.version;
        }
    });
    client.fire_startup_callbacks();

    auto version = [&] {
        std::scoped_lock lock(mu);
        return last_version;
    };
    CHECK(version() == "v1");

    // Core advances to v2, but the pub/sub notification was missed.
    server.set_artifact_for("owner1", "body-v2", "v2");
    server.set_listing("owner1", "cfg.json", "active", "v2");

    for (int i = 0; i < 200 && version() != "v2"; ++i) {
        std::this_thread::sleep_for(10ms);
    }
    CHECK(version() == "v2");
}

TEST_CASE("default seed tolerates a 409 and uses the existing artifact", "[artifact]") {
    FakeArtifactServer server;
    server.set_publish_conflict(true);  // the seed POST loses the race: 409, but it now exists

    ArtifactClient client(server.url(), "owner1");

    bool got_artifact = false;
    std::string captured_err = "unset";
    std::string captured_data;
    client.on_update_fetch_with_default(
        "cfg.json",
        deltafi::ArtifactDefault(std::string("default-data"), "application/json", "desc"),
        [&](const deltafi::FetchedArtifact* a, const std::string& err) {
            captured_err = err;
            got_artifact = (a != nullptr);
            if (a != nullptr) captured_data = a->data_string();
        });
    client.fire_startup_callbacks();

    CHECK(got_artifact);  // 409 tolerated: the concurrently-seeded artifact is used
    CHECK(captured_err.empty());
    CHECK(captured_data == "default-data");
}

TEST_CASE("ArtifactManifest::is_default reflects the default=true label", "[artifact]") {
    deltafi::ArtifactManifest m;
    CHECK_FALSE(m.is_default());
    m.labels["default"] = "true";
    CHECK(m.is_default());
    m.labels["default"] = "false";
    CHECK_FALSE(m.is_default());
}

TEST_CASE("publish_from_reader streams the payload as multipart", "[artifact]") {
    FakeArtifactServer server;
    ArtifactClient client(server.url(), "owner1");

    std::string content(500000, 'p');  // larger than the streaming chunk
    std::istringstream in(content);
    deltafi::PublishOptions opts;
    opts.version = "v9";

    auto resp = client.publish_from_reader("big.bin", in, "application/octet-stream", &opts);

    CHECK(server.published() == content);
    CHECK(server.published_version() == "v9");
}

TEST_CASE("publish_from_reader consumes a source with no full backing buffer", "[artifact]") {
    FakeArtifactServer server;
    ArtifactClient client(server.url(), "owner1");

    const size_t total = 4UL * 1024 * 1024;  // 4 MiB synthesized 8 KiB at a time
    GeneratingStreamBuf gen('z', total);
    std::istream reader(&gen);

    client.publish_from_reader("generated.bin", reader, "application/octet-stream");

    CHECK(server.published().size() == total);
    CHECK(server.published() == std::string(total, 'z'));
}

TEST_CASE("publish_artifact streams a buffered payload through the same path", "[artifact]") {
    FakeArtifactServer server;
    ArtifactClient client(server.url(), "owner1");

    std::string content = "hello artifact";
    std::vector<uint8_t> data(content.begin(), content.end());
    client.publish_artifact("small.bin", data, "text/plain");

    CHECK(server.published() == content);
}
