// ABOUTME: Joining transform action that merges multiple DeltaFiles into one.
// ABOUTME: Demonstrates the JoinAction interface with custom content/metadata aggregation.

#pragma once

#include <string>

#include <deltafi/plugin>

struct HelloWorldCppJoiningTransformActionParams {
    int index_threshold = 50;

    DELTAFI_PARAMS(HelloWorldCppJoiningTransformActionParams,
        FIELD(index_threshold, "Threshold for indexSum to trigger annotation"))
};

class HelloWorldCppJoiningTransformAction {
public:
    using Params = HelloWorldCppJoiningTransformActionParams;

    // Aggregate multiple inputs by reversing content order and summing index metadata.
    deltafi::TransformInput join(std::vector<deltafi::TransformInput> inputs) {
        std::vector<deltafi::ActionContent> reversed_content;
        std::map<std::string, std::string> all_metadata;
        int index_sum = 0;

        for (int i = static_cast<int>(inputs.size()) - 1; i >= 0; --i) {
            auto& input = inputs[i];
            reversed_content.insert(reversed_content.end(),
                std::make_move_iterator(input.content.begin()),
                std::make_move_iterator(input.content.end()));
            all_metadata.insert(input.metadata.begin(), input.metadata.end());

            auto it = input.metadata.find("index");
            if (it != input.metadata.end()) {
                try { index_sum += std::stoi(it->second); } catch (...) {}
            }
        }

        all_metadata["indexSum"] = std::to_string(index_sum);

        return deltafi::TransformInput{
            std::move(reversed_content),
            std::move(all_metadata)
        };
    }

    // Process the joined input and create a single merged content.
    deltafi::TransformResultType transform(deltafi::ActionContext& context,
                                            Params& params,
                                            deltafi::TransformInput& input) {
        if (params.index_threshold <= 0) {
            params.index_threshold = 50;
        }

        auto index_sum_str = input.get_metadata("indexSum");
        if (index_sum_str.empty()) {
            return deltafi::ErrorResult(context, "Missing required metadata: indexSum");
        }

        std::string merged;
        for (size_t i = 0; i < input.content.size(); ++i) {
            merged += input.content[i].load_string();
            merged += "\n";
        }

        auto content_name = "single-" + index_sum_str;
        deltafi::TransformResult result(context);
        auto& merged_content = result.save_content(merged, content_name, "text/plain");
        merged_content.add_tag("joined");

        for (const auto& [k, v] : input.metadata) {
            result.add_metadata(k, v);
        }

        int index_sum = 0;
        try { index_sum = std::stoi(index_sum_str); } catch (...) {}
        if (index_sum >= params.index_threshold) {
            result.add_annotation("indexThresholdMet", "true");
        }

        result.log_info() << "Joined " << input.content.size()
                         << " inputs with indexSum=" << index_sum_str
                         << " (threshold=" << params.index_threshold << ")";

        return result;
    }
};

DELTAFI_ACTION(HelloWorldCppJoiningTransformAction,
    "HelloWorldCppJoiningTransformAction",
    "Transforms a list of content in reverse order into a new, single content")
