// ABOUTME: Plugin registration with DeltaFi core via HTTP POST.
// ABOUTME: Builds the registration payload and handles retry logic.

#pragma once

#include <array>
#include <chrono>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "httplib.h"

#include "deltafi/internal/tls.hpp"
#include "deltafi/lookup.hpp"
#include "deltafi/options.hpp"
#include "deltafi/types.hpp"

namespace deltafi {

#ifndef DELTAFI_ACTION_KIT_VERSION
#define DELTAFI_ACTION_KIT_VERSION "0.0.0-dev"
#endif
inline constexpr const char* action_kit_version = DELTAFI_ACTION_KIT_VERSION;

// Describes a single action for registration.
struct ActionDescriptor {
    std::string name;
    std::string description;
    std::string type;
    bool supports_join = false;
    nlohmann::json schema = nlohmann::json::object();
    std::optional<ActionOptions> options;
};

inline void to_json(nlohmann::json& j, const ActionDescriptor& d) {
    j = nlohmann::json{{"name", d.name},
                       {"description", d.description},
                       {"type", d.type},
                       {"supportsJoin", d.supports_join},
                       {"schema", d.schema}};
    if (d.options.has_value()) {
        j["actionOptions"] = *d.options;
    }
}

// Full registration payload sent to core.
struct PluginRegistration {
    std::string image;
    std::string image_pull_secret;
    PluginCoordinates plugin_coordinates;
    std::string display_name;
    std::string description;
    std::string action_kit_version;
    std::vector<PluginCoordinates> dependencies;
    std::vector<ActionDescriptor> actions;
    std::vector<Variable> variables;
    std::vector<nlohmann::json> flow_plans;
    std::vector<nlohmann::json> integration_tests;
    std::vector<LookupTableDefinition> lookup_tables;
};

inline void to_json(nlohmann::json& j, const PluginRegistration& r) {
    j = nlohmann::json{{"image", r.image},
                       {"pluginCoordinates", r.plugin_coordinates},
                       {"displayName", r.display_name},
                       {"description", r.description},
                       {"actionKitVersion", r.action_kit_version},
                       {"dependencies", r.dependencies},
                       {"actions", r.actions},
                       {"variables", r.variables},
                       {"flowPlans", r.flow_plans},
                       {"integrationTests", r.integration_tests},
                       {"lookupTables", r.lookup_tables}};
    if (!r.image_pull_secret.empty()) {
        j["imagePullSecret"] = r.image_pull_secret;
    }
}

// Retry policy for plugin registration: fail fast on 4xx (malformed request),
// retry 5xx and IO errors.
inline bool should_retry_registration(int status) {
    return status < 400 || status >= 500;
}

// Send registration payload to core with retry (4 attempts: 500ms, 2s, 5s backoffs), failing
// fast on 4xx.
inline void post_registration(const std::string& core_url, const PluginRegistration& reg) {
    auto body = nlohmann::json(reg).dump();

    // Parse host/port from core_url
    std::string scheme = "http";
    std::string host_port = core_url;
    if (host_port.starts_with("https://")) {
        scheme = "https";
        host_port = host_port.substr(8);
    } else if (host_port.starts_with("http://")) {
        host_port = host_port.substr(7);
    }

    auto client = internal::make_httplib_client(scheme + "://" + host_port);
    client->set_connection_timeout(std::chrono::seconds(10));
    client->set_read_timeout(std::chrono::seconds(30));

    std::array<int, 3> backoffs_ms = {500, 2000, 5000};
    std::string last_error;

    for (int attempt = 0; attempt < 4; ++attempt) {
        if (attempt > 0) {
            spdlog::info("Registration attempt {}/4 after {}ms...", attempt + 1,
                         backoffs_ms[attempt - 1]);
            std::this_thread::sleep_for(std::chrono::milliseconds(backoffs_ms[attempt - 1]));
        }

        auto res = client->Post("/plugins", body, "application/json");
        if (!res) {
            last_error = "connection failed";
            spdlog::error("Registration error: {}", last_error);
            continue;
        }

        if (res->status >= 200 && res->status < 300) {
            spdlog::info("Plugin registered successfully (HTTP {})", res->status);
            return;
        }

        last_error = "HTTP " + std::to_string(res->status) + ": " + res->body;
        spdlog::error("Registration error: {}", last_error);
        if (!should_retry_registration(res->status)) {
            throw std::runtime_error("registration failed (client error): " + last_error);
        }
    }

    throw std::runtime_error("failed to register plugin: " + last_error);
}

}  // namespace deltafi
