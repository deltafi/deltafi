// ABOUTME: Egress action that logs content information and checks for error strings.
// ABOUTME: Demonstrates basic egress pattern with content loading and metadata handling.

#pragma once

#include <string>

#include <deltafi/plugin>

class HelloWorldCppEgressAction {
public:
    deltafi::EgressResultType egress(deltafi::ActionContext& context,
                                     deltafi::EgressInput& input) {
        if (!input.has_content()) {
            return deltafi::ErrorResult(context, "No content provided");
        }

        auto& content = input.first_content();
        auto data = content.load_string();

        if (data.find("error") != std::string::npos) {
            return deltafi::ErrorResult(context, "Failed to egress")
                .set_context("error in content");
        }

        deltafi::EgressResult result(context);
        result.log_info() << "Egressed " << data.size() << " bytes to stdout";
        result.log_info() << "Content name: " << content.name
                          << ", type: " << content.media_type;

        for (const auto& [key, value] : input.metadata) {
            result.log_info("Metadata - " + key + ": " + value);
        }

        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppEgressAction,
    "HelloWorldCppEgressAction",
    "Hello pretends to egress")
