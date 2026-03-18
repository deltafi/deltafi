// ABOUTME: Action documentation types for declaring inputs, outputs, errors, and filters.
// ABOUTME: Included in plugin registration so the DeltaFi UI can display action documentation.

#pragma once

#include <string>
#include <vector>

#include <nlohmann/json.hpp>

namespace deltafi {

// Pairs a key with a human-readable description (for metadata/annotation docs).
struct KeyedDescription {
    std::string key;
    std::string description;
};

inline void to_json(nlohmann::json& j, const KeyedDescription& d) {
    j = {{"key", d.key}, {"description", d.description}};
}

// Describes an error or filter with optional conditions under which it occurs.
struct DescriptionWithConditions {
    std::string description;
    std::vector<std::string> conditions;
};

inline void to_json(nlohmann::json& j, const DescriptionWithConditions& d) {
    j = {{"description", d.description}};
    if (!d.conditions.empty()) {
        j["conditions"] = d.conditions;
    }
}

// Describes a single piece of content by name, media type, and purpose.
struct ContentSpec {
    std::string name;
    std::string media_type;
    std::string description;
};

inline void to_json(nlohmann::json& j, const ContentSpec& c) {
    j = nlohmann::json::object();
    if (!c.name.empty()) j["name"] = c.name;
    if (!c.media_type.empty()) j["mediaType"] = c.media_type;
    if (!c.description.empty()) j["description"] = c.description;
}

// Describes the content and metadata an action expects to receive.
struct InputSpec {
    std::string content_summary;
    std::vector<ContentSpec> content_specs;
    std::string metadata_summary;
    std::vector<KeyedDescription> metadata_descriptions;
};

inline void to_json(nlohmann::json& j, const InputSpec& s) {
    j = nlohmann::json::object();
    if (!s.content_summary.empty()) j["contentSummary"] = s.content_summary;
    if (!s.content_specs.empty()) j["contentSpecs"] = s.content_specs;
    if (!s.metadata_summary.empty()) j["metadataSummary"] = s.metadata_summary;
    if (!s.metadata_descriptions.empty()) j["metadataDescriptions"] = s.metadata_descriptions;
}

// Describes the content, metadata, and annotations an action produces.
struct OutputSpec {
    std::string content_summary;
    std::vector<ContentSpec> content_specs;
    std::string metadata_summary;
    std::vector<KeyedDescription> metadata_descriptions;
    bool passthrough = false;
    std::string annotations_summary;
    std::vector<KeyedDescription> annotation_descriptions;
};

inline void to_json(nlohmann::json& j, const OutputSpec& s) {
    j = nlohmann::json::object();
    if (!s.content_summary.empty()) j["contentSummary"] = s.content_summary;
    if (!s.content_specs.empty()) j["contentSpecs"] = s.content_specs;
    if (!s.metadata_summary.empty()) j["metadataSummary"] = s.metadata_summary;
    if (!s.metadata_descriptions.empty()) j["metadataDescriptions"] = s.metadata_descriptions;
    if (s.passthrough) j["passthrough"] = true;
    if (!s.annotations_summary.empty()) j["annotationsSummary"] = s.annotations_summary;
    if (!s.annotation_descriptions.empty()) j["annotationDescriptions"] = s.annotation_descriptions;
}

// Declares an action's expected inputs, outputs, error conditions, filter conditions, and notes.
struct ActionOptions {
    std::string description;
    std::optional<InputSpec> input_spec;
    std::optional<OutputSpec> output_spec;
    std::vector<DescriptionWithConditions> filters;
    std::vector<DescriptionWithConditions> errors;
    std::vector<std::string> notes;
    std::string details;
};

inline void to_json(nlohmann::json& j, const ActionOptions& o) {
    j = nlohmann::json::object();
    if (!o.description.empty()) j["description"] = o.description;
    if (o.input_spec.has_value()) j["inputSpec"] = *o.input_spec;
    if (o.output_spec.has_value()) j["outputSpec"] = *o.output_spec;
    if (!o.filters.empty()) j["filters"] = o.filters;
    if (!o.errors.empty()) j["errors"] = o.errors;
    if (!o.notes.empty()) j["notes"] = o.notes;
    if (!o.details.empty()) j["details"] = o.details;
}

// Create a list of DescriptionWithConditions from plain description strings.
inline std::vector<DescriptionWithConditions> simple_filters(
    std::initializer_list<std::string> descriptions) {
    std::vector<DescriptionWithConditions> result;
    for (const auto& d : descriptions) {
        result.push_back({d, {}});
    }
    return result;
}

inline std::vector<DescriptionWithConditions> simple_errors(
    std::initializer_list<std::string> descriptions) {
    std::vector<DescriptionWithConditions> result;
    for (const auto& d : descriptions) {
        result.push_back({d, {}});
    }
    return result;
}

} // namespace deltafi
