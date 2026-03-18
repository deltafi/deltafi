// ABOUTME: Lookup table supplier providing color data with random values and periodic refresh.
// ABOUTME: Matches Go's ScheduledLookupTableSupplier with columns column_a, column_b, color.

#pragma once

#include <random>
#include <string>

#include <deltafi/plugin>

class ScheduledLookupTableSupplier : public deltafi::LookupTableSupplierBase {
public:
    deltafi::LookupTableDefinition table() const override {
        return {
            .name = "cpp_scheduled_lookup_table",
            .columns = {"column_a", "column_b", "color"},
            .key_columns = {"column_a"},
            .service_backed = true,
            .backing_service_active = true,
            .refresh_duration = "PT1M",
        };
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& /*variables*/,
        const std::map<std::string, std::vector<std::string>>& /*matching*/,
        const std::vector<std::string>& /*result_columns*/) override {
        spdlog::info("[scheduled-lookup-supplier] Getting rows for cpp_scheduled_lookup_table");
        return {
            {{"column_a", "A1"}, {"column_b", "B1"}, {"color", random_color()}},
            {{"column_a", "A2"}, {"column_b", "B2"}, {"color", random_color()}},
            {{"column_a", "A3"}, {"column_b", "B3"}, {"color", "red"}},
        };
    }

private:
    static std::string random_color() {
        static constexpr const char* colors[] = {
            "red", "orange", "yellow", "green", "blue", "indigo", "violet"
        };
        static thread_local std::mt19937 gen{std::random_device{}()};
        std::uniform_int_distribution<int> dist(0, 6);
        return colors[dist(gen)];
    }
};

DELTAFI_SUPPLIER(ScheduledLookupTableSupplier)
