// ABOUTME: Domain-specific error types for action input validation.
// ABOUTME: Defines MissingMetadataError and ExpectedContentError for cleaner action code.

#pragma once

#include <stdexcept>
#include <string>

namespace deltafi {

// Thrown when a required metadata key is not found in the input.
class MissingMetadataError : public std::runtime_error {
public:
    explicit MissingMetadataError(const std::string& key)
        : std::runtime_error("missing required metadata key: " + key),
          key_(key) {}

    const std::string& key() const { return key_; }

private:
    std::string key_;
};

// Thrown when content is expected at an index but not available.
class ExpectedContentError : public std::runtime_error {
public:
    ExpectedContentError(int index, int count)
        : std::runtime_error(
              "content index " + std::to_string(index) +
              " out of range (have " + std::to_string(count) + ")"),
          index_(index), count_(count) {}

    int index() const { return index_; }
    int count() const { return count_; }

private:
    int index_;
    int count_;
};

} // namespace deltafi
