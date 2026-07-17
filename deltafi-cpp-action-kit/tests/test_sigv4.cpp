// ABOUTME: Unit vectors for SigV4 canonical-query encoding (the multipart-only signing path).
// ABOUTME: The query path is unexercised by single-PUT/GET, so a mistake surfaces only here.

#include <catch2/catch_test_macros.hpp>

#include "deltafi/internal/s3_client.hpp"

using namespace deltafi::internal;

TEST_CASE("uri_encode_query encodes everything but RFC-3986 unreserved chars", "[sigv4]") {
    CHECK(uri_encode_query("Az0-_.~") == "Az0-_.~");
    CHECK(uri_encode_query(" ") == "%20");
    CHECK(uri_encode_query("/") == "%2F");
    CHECK(uri_encode_query("a+b=c") == "a%2Bb%3Dc");
}

TEST_CASE("canonical_query sorts by key and encodes key/value", "[sigv4]") {
    // Keys must be sorted (partNumber < uploadId); the uploadId's reserved chars encoded.
    CHECK(canonical_query({{"uploadId", "a/b+c=d"}, {"partNumber", "10"}}) ==
          "partNumber=10&uploadId=a%2Fb%2Bc%3Dd");
    // A valueless param (?uploads) canonicalizes to "key=".
    CHECK(canonical_query({{"uploads", ""}}) == "uploads=");
    CHECK(canonical_query({}).empty());
}
