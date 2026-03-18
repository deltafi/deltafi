// ABOUTME: Lookup table supplier providing city/state data from embedded content.
// ABOUTME: Matches Go's JsonLookupTableSupplier with columns city, state.

#pragma once

#include <deltafi/plugin>

class JsonLookupTableSupplier : public deltafi::LookupTableSupplierBase {
public:
    deltafi::LookupTableDefinition table() const override {
        return {
            .name = "cpp_json_lookup_table",
            .columns = {"city", "state"},
            .key_columns = {"city", "state"},
        };
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& /*variables*/,
        const std::map<std::string, std::vector<std::string>>& /*matching*/,
        const std::vector<std::string>& /*result_columns*/) override {
        return {
            {{"city", "Miami"}, {"state", "FL"}},
            {{"city", "Austin"}, {"state", "TX"}},
            {{"city", "Las Vegas"}, {"state", "NV"}},
        };
    }
};

DELTAFI_SUPPLIER(JsonLookupTableSupplier)
