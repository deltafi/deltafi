// ABOUTME: Lookup table supplier with pull-through caching that uses action variables.
// ABOUTME: Matches Go's PullThroughLookupTableSupplier with columns column_a, column_b, value.

#pragma once

#include <random>
#include <string>

#include <deltafi/plugin>

class PullThroughLookupTableSupplier : public deltafi::LookupTableSupplierBase {
public:
    deltafi::LookupTableDefinition table() const override {
        return {
            .name = "cpp_pull_through_lookup_table",
            .columns = {"column_a", "column_b", "value"},
            .key_columns = {"column_a"},
            .service_backed = true,
            .backing_service_active = true,
            .pull_through = true,
        };
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& variables,
        const std::map<std::string, std::vector<std::string>>& /*matching*/,
        const std::vector<std::string>& /*result_columns*/) override {
        spdlog::info("[pull-through-lookup-supplier] Getting rows for cpp_pull_through_lookup_table");

        std::string did_error_prefix;
        std::string convert_to_upper;
        if (auto it = variables.find("didErrorPrefix"); it != variables.end()) {
            did_error_prefix = it->second;
        }
        if (auto it = variables.find("convertToUpper"); it != variables.end()) {
            convert_to_upper = it->second;
        }

        return {
            {{"column_a", "A1"}, {"column_b", "B1"},
             {"value", random_value() + "-" + did_error_prefix + "-" + convert_to_upper}},
            {{"column_a", "A2"}, {"column_b", "B2"}, {"value", random_value()}},
            {{"column_a", "A3"}, {"column_b", "B3"}, {"value", random_value()}},
        };
    }

private:
    static std::string random_value() {
        static thread_local std::mt19937 gen{std::random_device{}()};
        return std::to_string(std::uniform_int_distribution<>(1, 100)(gen));
    }
};

DELTAFI_SUPPLIER(PullThroughLookupTableSupplier)
