// ABOUTME: Tests registration behavior: retry policy (fail-fast on 4xx) and declared dependencies.
// ABOUTME: Exercises should_retry_registration and PluginRegistration serialization in isolation.

#include <catch2/catch_test_macros.hpp>

#include <deltafi/registration.hpp>

using namespace deltafi;

TEST_CASE("registration retries 5xx and IO but not 4xx") {
    CHECK_FALSE(should_retry_registration(400));
    CHECK_FALSE(should_retry_registration(404));
    CHECK_FALSE(should_retry_registration(409));
    CHECK(should_retry_registration(500));
    CHECK(should_retry_registration(503));
}

TEST_CASE("plugin registration serializes declared dependencies") {
    PluginRegistration reg;
    reg.dependencies = {PluginCoordinates{
        .group_id = "org.deltafi", .artifact_id = "dep-plugin", .version = "1.2.3"}};
    nlohmann::json j = reg;
    REQUIRE(j["dependencies"].size() == 1);
    CHECK(j["dependencies"][0]["artifactId"] == "dep-plugin");
}
