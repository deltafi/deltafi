// ABOUTME: Unit tests for RESP2 protocol encoding and URL parsing.
// ABOUTME: Validates wire format encoding and host:port extraction without requiring a Redis server.

#include <catch2/catch_test_macros.hpp>
#include "deltafi/internal/resp_client.hpp"

using namespace deltafi::internal;

TEST_CASE("resp_encode single command", "[resp]") {
    auto encoded = resp_encode({"PING"});
    CHECK(encoded == "*1\r\n$4\r\nPING\r\n");
}

TEST_CASE("resp_encode SET command", "[resp]") {
    auto encoded = resp_encode({"SET", "key", "value"});
    CHECK(encoded == "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n");
}

TEST_CASE("resp_encode empty string argument", "[resp]") {
    auto encoded = resp_encode({"SET", "key", ""});
    CHECK(encoded == "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$0\r\n\r\n");
}

TEST_CASE("resp_encode BZPOPMIN", "[resp]") {
    auto encoded = resp_encode({"BZPOPMIN", "myqueue", "5"});
    CHECK(encoded == "*3\r\n$8\r\nBZPOPMIN\r\n$7\r\nmyqueue\r\n$1\r\n5\r\n");
}

TEST_CASE("resp_encode ZADD", "[resp]") {
    auto encoded = resp_encode({"ZADD", "dgs", "1234567890", "payload"});
    CHECK(encoded ==
          "*4\r\n$4\r\nZADD\r\n$3\r\ndgs\r\n$10\r\n1234567890\r\n$7\r\npayload\r\n");
}

TEST_CASE("resp_encode HSET", "[resp]") {
    auto encoded = resp_encode({"HSET", "heartbeat", "action1", "2024-01-01T00:00:00Z"});
    CHECK(encoded ==
          "*4\r\n$4\r\nHSET\r\n$9\r\nheartbeat\r\n$7\r\naction1\r\n$20\r\n2024-01-01T00:00:00Z\r\n");
}

TEST_CASE("parse_host_port bare host", "[resp]") {
    auto [host, port] = parse_host_port("localhost");
    CHECK(host == "localhost");
    CHECK(port == 6379);
}

TEST_CASE("parse_host_port with port", "[resp]") {
    auto [host, port] = parse_host_port("redis-server:6380");
    CHECK(host == "redis-server");
    CHECK(port == 6380);
}

TEST_CASE("parse_host_port with scheme", "[resp]") {
    auto [host, port] = parse_host_port("redis://myhost:6381");
    CHECK(host == "myhost");
    CHECK(port == 6381);
}

TEST_CASE("parse_host_port with http scheme no port", "[resp]") {
    auto [host, port] = parse_host_port("http://valkey-master");
    CHECK(host == "valkey-master");
    CHECK(port == 6379);
}

TEST_CASE("parse_host_port DeltaFi default", "[resp]") {
    auto [host, port] = parse_host_port("deltafi-valkey-master:6379");
    CHECK(host == "deltafi-valkey-master");
    CHECK(port == 6379);
}
