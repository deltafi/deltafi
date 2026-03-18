// ABOUTME: Entry point for the DeltaFi C++ Hello World reference plugin.
// ABOUTME: Includes action and supplier headers (which self-register) and runs the plugin.

#include <deltafi/plugin>

#include "actions/transform.hpp"
#include "actions/egress.hpp"
#include "actions/timed_ingress.hpp"
#include "actions/split.hpp"
#include "actions/join.hpp"

#include "suppliers/csv_lookup_supplier.hpp"
#include "suppliers/json_lookup_supplier.hpp"
#include "suppliers/pull_through_lookup_supplier.hpp"
#include "suppliers/scheduled_lookup_supplier.hpp"

namespace {

    void process_config(const deltafi::FetchedArtifact* fetched, const std::string& error) {
        if (!error.empty()) {
            spdlog::error("Failed to load config.yaml: {}", error);
            return;
        }
        spdlog::info("Loaded config.yaml (version={}):\n{}",
                fetched->manifest.version, fetched->data_string());
    }

} // namespace

// Register a callback to process config file changes
DELTAFI_WATCH_ARTIFACT(
    // Artifact name
    "config.yaml",

    // Artifact default
    deltafi::ArtifactDefault(
        "config: []",
        "application/yaml",
        "C++ hello world example config.yaml"
    ),

    // Callback handler
    process_config
)

DELTAFI_PLUGIN(
    "org.deltafi",                      // group ID
    "deltafi-cpp-hello-world",          // artifact ID
    "C++ Hello World reference plugin", // description
    "org.deltafi.cpp-hello-world"       // action domain prefix
)
