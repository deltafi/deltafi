// ABOUTME: Split transform action that demonstrates multi-child DeltaFile creation.
// ABOUTME: Splits one input DeltaFile into multiple children based on a parameter.

#pragma once

#include <string>

#include <deltafi/plugin>

struct HelloWorldCppSplitTransformActionParams {
    int number_of_children = 2;

    DELTAFI_PARAMS(HelloWorldCppSplitTransformActionParams,
        FIELD(number_of_children, "Number of child DeltaFiles to create"))
};

class HelloWorldCppSplitTransformAction {
public:
    using Params = HelloWorldCppSplitTransformActionParams;

    deltafi::TransformResultType transform(deltafi::ActionContext& context,
                                            Params& params,
                                            deltafi::TransformInput& input) {
        ensure_artifact(context);

        if (params.number_of_children <= 0) {
            params.number_of_children = 2;
        }

        if (!input.has_content()) {
            return deltafi::ErrorResult(context, "No content provided");
        }

        auto data = input.content_at(0).load_string();
        auto transformed = data + "\nHelloWorldCppSplitTransformAction spawned me";

        auto saved_content = deltafi::save_content_string(
            *context.content_service, context, transformed, "transformed content", "text/plain");

        deltafi::TransformResults result(context);
        result.log_info("The parent saved new content");

        for (int n = 0; n < params.number_of_children; ++n) {
            auto child_name = context.delta_file_name + "-" + std::to_string(n);
            auto& child = result.add_child(child_name);

            child.add_content(saved_content);
            child.add_metadata("transformManyKey", "manyValue");
            child.add_annotation("childIndex", std::to_string(n));
            if (n % 2 == 1) {
                child.add_metadata("did", child.context().did);
            }
            child.log_info("I am child #" + std::to_string(n));
        }

        return result;
    }

private:
    bool artifact_checked_ = false;

    void ensure_artifact(deltafi::ActionContext& context) {
        if (artifact_checked_ || !context.artifact_client) return;
        artifact_checked_ = true;

        try {
            context.artifact_client->get_artifact("hello.txt", "");
        } catch (...) {
            // Artifact doesn't exist yet, publish a default
            std::string content = "hello world";
            std::vector<uint8_t> data(content.begin(), content.end());
            deltafi::PublishOptions opts;
            opts.version = "1";
            opts.description = "Sample artifact managed by the ArtifactRegistryService directly from a plugin";
            opts.make_active = true;
            opts.labels = {{"default", "true"}};
            try {
                context.artifact_client->publish_artifact(
                    "hello.txt", data, "text/plain", &opts);
                spdlog::info("Published default hello.txt artifact");
            } catch (const std::exception& ex) {
                spdlog::error("Failed to publish hello.txt artifact: {}", ex.what());
            }
        }
    }
};

DELTAFI_ACTION(HelloWorldCppSplitTransformAction,
    "HelloWorldCppSplitTransformAction",
    "Transform N children based on the numberOfChildren parameter")
