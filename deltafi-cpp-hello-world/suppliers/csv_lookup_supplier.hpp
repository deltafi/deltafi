// ABOUTME: Lookup table supplier providing F1 driver data from embedded content.
// ABOUTME: Matches Go's CsvLookupTableSupplier with columns first, last, team.

#pragma once

#include <deltafi/plugin>

class CsvLookupTableSupplier : public deltafi::LookupTableSupplierBase {
public:
    deltafi::LookupTableDefinition table() const override {
        return {
            .name = "cpp_csv_lookup_table",
            .columns = {"first", "last", "team"},
            .key_columns = {"first", "last"},
        };
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& /*variables*/,
        const std::map<std::string, std::vector<std::string>>& /*matching*/,
        const std::vector<std::string>& /*result_columns*/) override {
        return {
            {{"last", "Verstappen"}, {"first", "Max"}, {"team", "Red Bull"}},
            {{"last", "Piastri"}, {"first", "Oscar"}, {"team", "McLaren"}},
            {{"last", "Norris"}, {"first", "Lando"}, {"team", "McLaren"}},
            {{"last", "Leclerc"}, {"first", "Charles"}, {"team", "Ferrari"}},
            {{"last", "Albon"}, {"first", "Alex"}, {"team", "Williams"}},
            {{"last", "Hadjar"}, {"first", "Isack"}, {"team", "Racing Bulls"}},
        };
    }
};

DELTAFI_SUPPLIER(CsvLookupTableSupplier)
