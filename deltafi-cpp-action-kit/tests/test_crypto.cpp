// ABOUTME: Unit tests for SHA256 and HMAC-SHA256 crypto functions.
// ABOUTME: Tests against RFC 4231 test vectors and known SHA256 values.

#include <catch2/catch_test_macros.hpp>
#include "deltafi/internal/crypto.hpp"

using namespace deltafi::internal;

TEST_CASE("SHA256 of empty string", "[crypto]") {
    CHECK(sha256_hex("") ==
          "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
}

TEST_CASE("SHA256 of Hello World", "[crypto]") {
    CHECK(sha256_hex("Hello, World!") ==
          "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f");
}

TEST_CASE("SHA256 of quick brown fox", "[crypto]") {
    CHECK(sha256_hex("The quick brown fox jumps over the lazy dog") ==
          "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592");
}

TEST_CASE("HMAC-SHA256 RFC 4231 Test Case 1", "[crypto]") {
    std::vector<uint8_t> key(20, 0x0b);
    CHECK(hmac_sha256_hex(key, "Hi There") ==
          "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
}

TEST_CASE("HMAC-SHA256 RFC 4231 Test Case 2", "[crypto]") {
    CHECK(hmac_sha256_hex("Jefe", "what do ya want for nothing?") ==
          "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
}

TEST_CASE("HMAC-SHA256 RFC 4231 Test Case 3", "[crypto]") {
    std::vector<uint8_t> key(20, 0xaa);
    std::string data(50, '\xdd');
    CHECK(hmac_sha256_hex(key, data) ==
          "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe");
}

TEST_CASE("HMAC-SHA256 with key longer than block size", "[crypto]") {
    std::vector<uint8_t> key(131, 0xaa);
    std::string data = "Test Using Larger Than Block-Size Key - Hash Key First";
    CHECK(hmac_sha256_hex(key, data) ==
          "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54");
}

TEST_CASE("to_hex basic conversion", "[crypto]") {
    CHECK(to_hex({0xde, 0xad, 0xbe, 0xef}) == "deadbeef");
}

TEST_CASE("to_hex empty", "[crypto]") {
    CHECK(to_hex({}) == "");
}

TEST_CASE("to_hex leading zeros", "[crypto]") {
    CHECK(to_hex({0x00, 0x01, 0x0a}) == "00010a");
}
