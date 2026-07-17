// ABOUTME: Domain-specific error types for action input validation.
// ABOUTME: Defines MissingMetadataError and ExpectedContentError for cleaner action code.

#pragma once

#include <exception>
#include <stdexcept>
#include <string>
#include <typeinfo>
#ifdef __GNUG__
#include <cstdlib>
#include <cxxabi.h>
#include <memory>
#endif

namespace deltafi {

// Human-readable description of an exception: "<demangled type>: <what()>".
// A full stack trace is not available under C++20 in the plugin's static/stripped
// build; the type + message is the best deterministic context to attach here.
inline std::string describe_exception(const std::exception& ex) {
    const char* raw = typeid(ex).name();
    std::string type_name = raw;
#ifdef __GNUG__
    int status = 0;
    std::unique_ptr<char, void (*)(void*)> demangled(
        abi::__cxa_demangle(raw, nullptr, nullptr, &status), std::free);
    if (status == 0 && demangled) type_name = demangled.get();
#endif
    return type_name + ": " + ex.what();
}

// Thrown when a required metadata key is not found in the input.
class MissingMetadataError : public std::runtime_error {
public:
    explicit MissingMetadataError(const std::string& key)
        : std::runtime_error("Missing metadata with key " + key), key_(key) {}

    const std::string& key() const { return key_; }

private:
    std::string key_;
};

// Thrown when content is expected at an index but not available.
class ExpectedContentError : public std::runtime_error {
public:
    ExpectedContentError(int index, int count)
        : std::runtime_error(count == 0
                                 ? std::string("Action received no content")
                                 : "Action received no content at index " + std::to_string(index) +
                                       " (have " + std::to_string(count) + ")"),
          index_(index),
          count_(count) {}

    int index() const { return index_; }
    int count() const { return count_; }

private:
    int index_;
    int count_;
};

}  // namespace deltafi
