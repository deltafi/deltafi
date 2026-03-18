// ABOUTME: Transform action that appends text, optionally uppercases, and checks DID prefixes.
// ABOUTME: Demonstrates content manipulation, metadata, annotations, lookup queries, and parameter parsing.

#pragma once

#include <algorithm>
#include <random>
#include <string>

#include <deltafi/plugin>

#include "actions/transform_params.hpp"

class HelloWorldCppTransformAction {
public:
    using Params = HelloWorldCppTransformActionParams;

    static constexpr std::string defaultMetadataJson() {
        return R"metadata.json(
{
    "custom": "value"
}
)metadata.json";
    }

    void configure_artifacts(deltafi::ArtifactClient& client) {
        client.on_update_fetch_with_default(
            "metadata.json",
            {
                defaultMetadataJson(),
                "application/json",
                "Default extra metadata for HelloWorldCppTransformAction"
            },
            [this](const deltafi::FetchedArtifact* fetched, const std::string& err) {
                on_metadata_update(fetched, err);
            });
    }

    static deltafi::ActionOptions action_options() {
        return {
            .description = "Add some content noting that we did a really good job",
            .input_spec = deltafi::InputSpec{
                .content_summary = "Expects a single text content item to transform",
            },
            .output_spec = deltafi::OutputSpec{
                .content_summary = "Produces a single text content item with appended message",
                .metadata_descriptions = {
                    {"transformKey", "Marker indicating transform was applied"},
                    {"RGB Rows", "Color values from scheduled lookup table"},
                    {"McLaren Drivers", "Driver names from CSV lookup table"},
                },
                .annotations_summary = "Adds language annotation",
                .annotation_descriptions = {
                    {"language", "The language of the transformed content"},
                },
            },
            .filters = deltafi::simple_filters({
                "Filter due to did_filter_prefix match",
            }),
            .errors = deltafi::simple_errors({
                "Error due to did_error_prefix match",
                "No content provided",
                "No RGB results found",
            }),
        };
    }

    deltafi::TransformResultType transform(deltafi::ActionContext& context,
                                            Params& params,
                                            deltafi::TransformInput& input) {
        // Check DID prefix for filter/error conditions
        if (context.did.starts_with(params.did_filter_prefix)) {
            if (params.always_filter_on_prefix_hit || sometimes_true()) {
                return deltafi::FilterResult(context, "Filter due to did_filter_prefix match");
            }
        } else if (context.did.starts_with(params.did_error_prefix)) {
            if (params.always_error_on_prefix_hit || sometimes_true()) {
                return deltafi::ErrorResult(context, "Error due to did_error_prefix match")
                    .set_context(params.did_error_prefix + " detected");
            }
        }

        if (!input.has_content()) {
            return deltafi::ErrorResult(context, "No content provided");
        }

        auto data = input.content_at(0).load_string();
        auto transformed = data + "\nHelloWorldCppTransformAction did a great job";
        bool look_for_red = transformed.find("RED") != std::string::npos;

        if (params.convert_to_upper) {
            std::transform(transformed.begin(), transformed.end(),
                           transformed.begin(), ::toupper);
        }

        auto content_name = "content-" +
            input.get_metadata_or_default("index", "0");

        deltafi::TransformResult result(context);
        auto& new_content = result.save_content(transformed, content_name, "text/plain");
        new_content.add_tag("hello-file");

        result.add_metadata("transformKey", "transformValue");
        result.add_annotation("language", "English");
        result.log_info() << "Transformed content, size: " << new_content.size() << " bytes";

        // Add extra metadata loaded from artifact
        add_artifact_metadata(result);

        // Query lookup tables if a lookup client is available
        if (context.lookup_client) {
            if (auto e = lookup_rgb(context, look_for_red, result)) return std::move(*e);
            if (auto e = lookup_purple(context, result)) return std::move(*e);
            if (auto e = lookup_drivers(context, result)) return std::move(*e);
        }

        return result;
    }

    deltafi::SafeStringMap& extra_metadata_for_testing() { return extra_metadata_; }

private:
    deltafi::SafeStringMap extra_metadata_;

    void on_metadata_update(const deltafi::FetchedArtifact* fetched, const std::string& err) {
        if (!err.empty() || !fetched) {
            spdlog::error("Unable to read metadata.json: {}", err);
            extra_metadata_.store({});
            return;
        }
        try {
            auto j = nlohmann::json::parse(fetched->data);
            extra_metadata_.store(j.get<std::map<std::string, std::string>>());
        } catch (const std::exception& ex) {
            spdlog::error("Unable to parse metadata.json: {}", ex.what());
            extra_metadata_.store({});
        }
    }

    void add_artifact_metadata(deltafi::TransformResult& result) {
        extra_metadata_.for_each([&](const std::string& k, const std::string& v) {
            result.add_metadata(k, v);
        });
    }

    bool sometimes_true() {
        static thread_local std::mt19937 gen{std::random_device{}()};
        return std::uniform_int_distribution<>(0, 1)(gen) == 1;
    }

    // Query scheduled lookup table for RGB colors.
    std::optional<deltafi::ErrorResult> lookup_rgb(
        deltafi::ActionContext& actx, bool look_for_red,
        deltafi::TransformResult& result) {

        std::vector<std::string> colors = look_for_red
            ? std::vector<std::string>{"red"}
            : std::vector<std::string>{"red", "green", "blue"};

        deltafi::LookupOptions opts;
        opts.matching_column_values = {{"color", colors}};
        opts.result_columns = {"column_a"};

        try {
            auto lr = actx.lookup_client->lookup("cpp_scheduled_lookup_table", &opts);

            if (lr.total_count > 0) {
                std::string column_a_values;
                for (const auto& row : lr.rows) {
                    if (!column_a_values.empty()) column_a_values += ", ";
                    auto it = row.find("column_a");
                    if (it != row.end()) column_a_values += it->second;
                }
                result.add_metadata("RGB Rows", column_a_values);
                if (lr.modified.has_value()) {
                    result.add_metadata("colorTableLastModified", *lr.modified);
                }
                if (lr.created.has_value()) {
                    result.add_metadata("colorTableCreated", *lr.created);
                }
            } else {
                return deltafi::ErrorResult(actx, "No RGB results found");
            }
        } catch (const std::exception& ex) {
            return deltafi::ErrorResult(actx, "Unable to lookup RGB rows")
                .set_context(ex.what());
        }

        return std::nullopt;
    }

    // Query scheduled lookup table for purple (never exists).
    std::optional<deltafi::ErrorResult> lookup_purple(
        deltafi::ActionContext& actx,
        deltafi::TransformResult& result) {

        deltafi::LookupOptions opts;
        opts.matching_column_values = {{"color", {"purple"}}};
        opts.result_columns = {"column_a"};

        try {
            auto lr = actx.lookup_client->lookup("cpp_scheduled_lookup_table", &opts);
            result.add_metadata("purpleCount", std::to_string(lr.total_count));
            if (lr.created.has_value()) {
                result.add_metadata("purpleCreated", *lr.created);
            }
            if (lr.modified.has_value()) {
                result.add_metadata("purpleModified", *lr.modified);
            }
        } catch (const std::exception& ex) {
            return deltafi::ErrorResult(actx, "Unable to lookup purple rows")
                .set_context(ex.what());
        }

        return std::nullopt;
    }

    // Query CSV lookup table for McLaren drivers.
    std::optional<deltafi::ErrorResult> lookup_drivers(
        deltafi::ActionContext& actx,
        deltafi::TransformResult& result) {

        deltafi::LookupOptions opts;
        opts.matching_column_values = {{"team", {"McLaren"}}};

        try {
            auto lr = actx.lookup_client->lookup("cpp_csv_lookup_table", &opts);
            std::string drivers;
            for (const auto& row : lr.rows) {
                if (!drivers.empty()) drivers += ", ";
                auto first = row.find("first");
                auto last = row.find("last");
                if (first != row.end() && last != row.end()) {
                    drivers += first->second + " " + last->second;
                }
            }
            result.add_metadata("McLaren Drivers", drivers);
        } catch (const std::exception& ex) {
            return deltafi::ErrorResult(actx, "Unable to lookup McLaren drivers")
                .set_context(ex.what());
        }

        return std::nullopt;
    }
};

DELTAFI_ACTION(HelloWorldCppTransformAction,
    "HelloWorldCppTransformAction",
    "Add some content noting that we did a really good job")
