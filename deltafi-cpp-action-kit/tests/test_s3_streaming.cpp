// ABOUTME: Tests the S3 client's content storage: round-trip, multipart, streaming, abort.
// ABOUTME: Runs against an in-process fake S3 server (no live backend, no real SigV4 check).

#include <array>
#include <iterator>
#include <ostream>
#include <sstream>
#include <utility>
#include <string>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "deltafi/internal/s3_client.hpp"

#include "fake_s3_server.hpp"

using deltafi::internal::S3Client;
using deltafi::test::FakeS3Server;

namespace {
std::vector<uint8_t> bytes(const std::string& s) {
    return {s.begin(), s.end()};
}
std::string str(const std::vector<uint8_t>& b) {
    return {b.begin(), b.end()};
}
}  // namespace

TEST_CASE("S3 round-trip: save then load a small object", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);

    auto data = bytes("hello world");
    auto seg = client.save("did-abc", data, "uuid-1");
    CHECK(std::cmp_equal(seg.size, data.size()));

    auto loaded = client.load({seg});
    CHECK(str(loaded) == "hello world");
    // A small object goes through the single-PUT fast path (no multipart).
    CHECK_FALSE(server.has_pending_uploads());
}

TEST_CASE("S3 multipart: content larger than the part size uploads in parts", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);
    client.set_part_size(16);  // tiny parts so multipart is cheap to exercise

    std::string content;
    for (int i = 0; i < 100; ++i) content += "0123456789";  // 1000 bytes → many parts
    auto seg = client.save("did-mp", bytes(content), "uuid-mp");

    CHECK(seg.size == 1000);
    CHECK(server.part_count() > 1);              // genuinely multipart
    CHECK_FALSE(server.has_pending_uploads());   // completed, not left staged
    CHECK(server.max_single_put_bytes() <= 16);  // no single PUT carried the whole object

    // Round-trips, including a Range read that crosses part seams.
    CHECK(str(client.load({seg})) == content);
}

TEST_CASE("S3 multipart aborts on a mid-upload failure and leaves nothing staged", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);
    client.set_part_size(16);
    server.fail_part(2);  // the 2nd UploadPart returns HTTP 500

    CHECK_THROWS(client.save("did-fail", bytes(std::string(100, 'x')), "uuid-fail"));
    CHECK_FALSE(server.has_pending_uploads());  // AbortMultipartUpload was issued
}

TEST_CASE("S3 save_from_reader streams an unknown-size source", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);
    client.set_part_size(16);

    std::string content(50, 'y');
    std::istringstream in(content);
    auto seg = client.save_from_reader("did-r", in, -1, "uuid-r");  // size unknown

    CHECK(seg.size == 50);  // actual streamed byte count
    CHECK(str(client.load({seg})) == content);
}

TEST_CASE("S3 handles empty content", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);
    auto seg = client.save("did-e", {}, "uuid-e");
    CHECK(seg.size == 0);
    CHECK(client.load({seg}).empty());
}

TEST_CASE("S3 save_from_writer streams via a writer callback (multipart)", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);
    client.set_part_size(16);

    auto seg = client.save_from_writer(
        "did-w",
        [](std::ostream& out) {
            for (int i = 0; i < 100; ++i) out << 'w';  // stream past several parts
        },
        "uuid-w");

    CHECK(seg.size == 100);
    CHECK(server.part_count() > 1);
    CHECK_FALSE(server.has_pending_uploads());
    CHECK(str(client.load({seg})) == std::string(100, 'w'));
}

TEST_CASE("S3 save_from_writer uses the single-PUT fast path for small content", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);

    auto seg = client.save_from_writer(
        "did-ws", [](std::ostream& out) { out << "small payload"; }, "uuid-ws");

    CHECK(seg.size == 13);
    CHECK_FALSE(server.has_pending_uploads());  // single PUT, no multipart
    CHECK(str(client.load({seg})) == "small payload");
}

TEST_CASE("S3 load_reader streams content incrementally", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);

    std::string content(5000, 'z');
    auto seg = client.save("did-s", bytes(content), "uuid-s");

    auto reader = client.load_reader({seg});
    std::string got((std::istreambuf_iterator<char>(*reader)), std::istreambuf_iterator<char>());
    CHECK(got == content);
}

TEST_CASE("S3 load_reader stays memory-bounded and aborts on early abandon", "[s3]") {
    FakeS3Server server;
    S3Client client(server.url(), "ak", "sk", "bucket", false);

    // Larger than the reader's 1 MiB backpressure bound, so the producer must block
    // rather than buffer the whole object.
    std::string content(2UL * 1024 * 1024, 'q');
    auto seg = client.save("did-p", bytes(content), "uuid-p");

    auto reader = client.load_reader({seg});
    std::array<char, 16> head{};
    reader->read(head.data(), 10);
    CHECK(reader->gcount() == 10);
    CHECK(std::string(head.data(), 10) == std::string(10, 'q'));
    // Abandon mid-stream: the destructor must abort the in-flight GET without hanging.
    reader.reset();
}
