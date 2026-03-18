// ABOUTME: Input types for action execution.
// ABOUTME: Defines TransformInput, EgressInput, IngressInput with content/metadata accessors.

#pragma once

#include <map>
#include <string>
#include <vector>

#include "deltafi/types.hpp"
#include "deltafi/errors.hpp"

namespace deltafi {

// Input data for a transform action.
struct TransformInput {
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;

    bool has_content() const { return !content.empty(); }

    ActionContent& first_content() {
        if (content.empty()) {
            throw ExpectedContentError(0, 0);
        }
        return content[0];
    }

    ActionContent& content_at(int index) {
        if (index < 0 || index >= static_cast<int>(content.size())) {
            throw ExpectedContentError(index, static_cast<int>(content.size()));
        }
        return content[index];
    }

    ActionContent* content_named(const std::string& name) {
        for (auto& c : content) {
            if (c.name == name) return &c;
        }
        return nullptr;
    }

    std::string get_metadata(const std::string& key) const {
        auto it = metadata.find(key);
        if (it == metadata.end()) throw MissingMetadataError(key);
        return it->second;
    }

    std::string get_metadata_or_default(const std::string& key,
                                         const std::string& default_val) const {
        auto it = metadata.find(key);
        return (it != metadata.end()) ? it->second : default_val;
    }
};

// Input data for an egress action.
struct EgressInput {
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;

    bool has_content() const { return !content.empty(); }

    ActionContent& first_content() {
        if (content.empty()) {
            throw ExpectedContentError(0, 0);
        }
        return content[0];
    }

    ActionContent& content_at(int index) {
        if (index < 0 || index >= static_cast<int>(content.size())) {
            throw ExpectedContentError(index, static_cast<int>(content.size()));
        }
        return content[index];
    }

    ActionContent* content_named(const std::string& name) {
        for (auto& c : content) {
            if (c.name == name) return &c;
        }
        return nullptr;
    }

    std::string get_metadata(const std::string& key) const {
        auto it = metadata.find(key);
        if (it == metadata.end()) throw MissingMetadataError(key);
        return it->second;
    }

    std::string get_metadata_or_default(const std::string& key,
                                         const std::string& default_val) const {
        auto it = metadata.find(key);
        return (it != metadata.end()) ? it->second : default_val;
    }
};

// Input data for a timed ingress action (no content or metadata).
struct IngressInput {};

} // namespace deltafi
