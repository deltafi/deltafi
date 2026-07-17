// ABOUTME: Plugin orchestrator that manages the full action kit lifecycle.
// ABOUTME: Handles configuration, registration, worker startup, heartbeat, and graceful shutdown.

#pragma once

#include <algorithm>
#include <array>
#include <chrono>
#include <csignal>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <map>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "deltafi/artifact.hpp"
#include "deltafi/content.hpp"
#include "deltafi/heartbeat.hpp"
#include "deltafi/lookup.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/registration.hpp"
#include "deltafi/registry.hpp"
#include "deltafi/types.hpp"
#include "deltafi/worker.hpp"

namespace deltafi {

inline constexpr const char* health_probe_file = "/tmp/running";

// RAII marker file for container readiness/liveness probes. Created on
// construction, removed on destruction (any exit path, including exceptions).
struct HealthProbe {
    std::filesystem::path path;
    explicit HealthProbe(std::filesystem::path p) : path(std::move(p)) {
        std::ofstream(path) << "running";
    }
    ~HealthProbe() {
        std::error_code ec;
        std::filesystem::remove(path, ec);
    }
    HealthProbe(const HealthProbe&) = delete;
    HealthProbe& operator=(const HealthProbe&) = delete;
    HealthProbe(HealthProbe&&) = delete;
    HealthProbe& operator=(HealthProbe&&) = delete;
};

// Plugin configuration from environment variables with optional overrides.
struct PluginConfig {
    std::string core_url;
    std::string valkey_url;
    std::string valkey_password;
    std::string minio_url;
    std::string minio_access_key;
    std::string minio_secret_key;
    std::string minio_bucket;
    bool minio_use_ssl = false;
    std::size_t minio_part_size = 0;  // S3 multipart part size in bytes; 0 = kit default
    std::string image;
    std::string image_pull_secret;
    PluginCoordinates coordinates;
    std::vector<PluginCoordinates> dependencies;
    std::string display_name;
    std::string description;
    std::string domain;
};

// Global signal flag for graceful shutdown.
namespace detail {
inline volatile std::sig_atomic_t shutdown_requested = 0;
inline void signal_handler([[maybe_unused]] int signum) {
    shutdown_requested = 1;
}

// Artifact watch registrations used by Plugin::run().
using ArtifactWatchRegistrar = std::function<void(ArtifactClient&)>;
inline std::vector<ArtifactWatchRegistrar>& pending_artifact_watches() {
    static std::vector<ArtifactWatchRegistrar> v;
    return v;
}
}  // namespace detail

using ArtifactFetchHandler = std::function<void(const FetchedArtifact*, const std::string&)>;

// Register a plugin-scoped artifact watch with auto-fetch on version change.
inline bool watch_artifact(std::string name, ArtifactFetchHandler handler) {
    detail::pending_artifact_watches().emplace_back(
        [n = std::move(name), h = std::move(handler)](ArtifactClient& client) {
            client.on_update_fetch(n, h);
        });
    return true;
}

// Register a plugin-scoped artifact watch with auto-fetch and a default to publish if missing.
inline bool watch_artifact(std::string name, ArtifactDefault default_artifact,
                           ArtifactFetchHandler handler) {
    detail::pending_artifact_watches().emplace_back(
        [n = std::move(name), d = std::move(default_artifact), h = std::move(handler)](
            ArtifactClient& client) { client.on_update_fetch_with_default(n, d, h); });
    return true;
}

// Register a shared artifact watch with auto-fetch on version change.
inline bool watch_shared_artifact(std::string name, ArtifactFetchHandler handler) {
    detail::pending_artifact_watches().emplace_back(
        [n = std::move(name), h = std::move(handler)](ArtifactClient& client) {
            client.on_shared_update_fetch(n, h);
        });
    return true;
}

// Main entry point for a DeltaFi action kit plugin.
class Plugin {
public:
    explicit Plugin(PluginConfig config) : config_(std::move(config)) {
        // Fill defaults from environment
        if (config_.core_url.empty())
            config_.core_url = env_or_default("CORE_URL", "http://deltafi-core-service");
        if (config_.valkey_url.empty())
            config_.valkey_url = env_or_default("VALKEY_URL", "deltafi-valkey-master:6379");
        if (config_.valkey_password.empty())
            config_.valkey_password = env_or_default("VALKEY_PASSWORD", "");
        if (config_.minio_url.empty())
            config_.minio_url = env_or_default("MINIO_URL", "deltafi-minio:9000");
        if (config_.minio_access_key.empty())
            config_.minio_access_key = env_or_default("MINIO_ACCESSKEY", "deltafi");
        if (config_.minio_secret_key.empty())
            config_.minio_secret_key = env_or_default("MINIO_SECRETKEY", "password");
        if (config_.minio_bucket.empty())
            config_.minio_bucket = env_or_default("STORAGE_BUCKET_NAME", "storage");
        if (env_or_default("MINIO_USE_SSL", "") == "true") config_.minio_use_ssl = true;
        if (config_.minio_part_size == 0) {
            auto ps = parse_byte_size(env_or_default("S3_PART_SIZE", ""));
            // Enforce the S3 multipart minimum so a too-small part size can't break large uploads.
            if (ps > 0)
                config_.minio_part_size =
                    ps < internal::S3Client::min_part_size ? internal::S3Client::min_part_size : ps;
        }
        if (config_.image.empty()) config_.image = env_or_default("IMAGE", "");
        if (config_.image_pull_secret.empty())
            config_.image_pull_secret = env_or_default("IMAGE_PULL_SECRET", "");
        if (config_.coordinates.group_id.empty())
            config_.coordinates.group_id = env_or_default("PROJECT_GROUP", "");
        if (config_.coordinates.artifact_id.empty())
            config_.coordinates.artifact_id = env_or_default("PROJECT_NAME", "");
        if (config_.coordinates.version.empty())
            config_.coordinates.version = env_or_default("PROJECT_VERSION", "");
        if (config_.display_name.empty())
            config_.display_name = env_or_default("PROJECT_NAME", config_.coordinates.artifact_id);
    }

    Registry& registry() { return registry_; }

    void set_worker_count(const std::string& action_name, int count) {
        worker_counts_[action_name] = count;
    }

    // Declare a plugin dependency (group / artifact / version) for registration.
    void add_dependency(const std::string& group_id, const std::string& artifact_id,
                        const std::string& version) {
        config_.dependencies.push_back(PluginCoordinates{
            .group_id = group_id, .artifact_id = artifact_id, .version = version});
    }

    // Register a lookup table supplier.
    void register_supplier(std::shared_ptr<LookupTableSupplierBase> supplier) {
        auto def = supplier->table();
        suppliers_[def.name] = std::move(supplier);
    }

    // Load flow JSON files from a directory (excluding variables.json).
    void load_flows(const std::string& dir) {
        namespace fs = std::filesystem;
        if (!fs::exists(dir)) return;

        for (const auto& entry : fs::directory_iterator(dir)) {
            if (entry.path().extension() != ".json") continue;
            if (entry.path().filename() == "variables.json") continue;

            std::ifstream file(entry.path());
            auto data = nlohmann::json::parse(file);
            FlowPlan fp;
            from_json(data, fp);
            flows_.push_back(std::move(fp));
        }
    }

    // Load variables from variables.json in the given directory.
    void load_variables(const std::string& dir) {
        namespace fs = std::filesystem;
        auto path = fs::path(dir) / "variables.json";
        if (!fs::exists(path)) return;

        std::ifstream file(path);
        auto data = nlohmann::json::parse(file);
        variables_ = data.get<std::vector<Variable>>();
    }

    // Load integration test JSON files from a directory.
    void load_integration_tests(const std::string& dir) {
        namespace fs = std::filesystem;
        if (!fs::exists(dir)) return;

        for (const auto& entry : fs::directory_iterator(dir)) {
            if (entry.path().extension() != ".json") continue;
            std::ifstream file(entry.path());
            integration_tests_.push_back(nlohmann::json::parse(file));
        }
    }

    // Run the full plugin lifecycle.
    int run() {
        // Install signal handlers
        std::signal(SIGTERM, detail::signal_handler);
        std::signal(SIGINT, detail::signal_handler);

        try {
            // Connect to Redis/Valkey
            spdlog::info("Connecting to Redis at {}...", config_.valkey_url);
            auto queue =
                std::make_unique<ActionEventQueue>(config_.valkey_url, config_.valkey_password);

            // Create content service
            spdlog::info("Connecting to MinIO at {}...", config_.minio_url);
            auto content_service = std::make_unique<ContentService>(
                config_.minio_url, config_.minio_access_key, config_.minio_secret_key,
                config_.minio_bucket, config_.minio_use_ssl, config_.minio_part_size);
            spdlog::info("Connected to MinIO");

            // Create lookup client
            auto lookup_client = std::make_unique<LookupClient>(config_.core_url);

            // Start supplier runner before registration (core may query immediately)
            std::unique_ptr<LookupTableSupplierRunner> supplier_runner;
            if (!suppliers_.empty()) {
                supplier_runner = std::make_unique<LookupTableSupplierRunner>(*queue, suppliers_);
                supplier_runner->start();
                spdlog::info("Started lookup table supplier runner for {} table(s)",
                             suppliers_.size());
            }

            // Build and post registration
            auto reg = build_registration();
            spdlog::info("Registering plugin {}:{}:{} with {} actions, {} flows, {} variables...",
                         config_.coordinates.group_id, config_.coordinates.artifact_id,
                         config_.coordinates.version, reg.actions.size(), reg.flow_plans.size(),
                         reg.variables.size());
            post_registration(config_.core_url, reg);

            // Upload initial supplier data after registration
            if (!suppliers_.empty()) {
                upload_supplier_data(*lookup_client, suppliers_);
            }

            // Create artifact client
            auto artifact_owner =
                config_.coordinates.group_id + "." + config_.coordinates.artifact_id;
            auto artifact_client =
                std::make_unique<ArtifactClient>(config_.core_url, artifact_owner);

            // Call ConfigureArtifacts on any actions that support it
            registry_.configure_artifacts(*artifact_client);

            // Apply global artifact watches registered via pending_artifact_watches
            for (auto& watch : detail::pending_artifact_watches()) {
                watch(*artifact_client);
            }

            // Fire startup callbacks and start version change listener
            artifact_client->fire_startup_callbacks();
            artifact_client->start_artifact_listener(*queue);
            spdlog::info("Artifact listener started");

            // Resolve app name
            std::string app_name = env_or_default("APP_NAME", "");
            if (app_name.empty()) {
                std::array<char, 256> hostname{};
                if (gethostname(hostname.data(), hostname.size()) == 0) {
                    app_name = hostname.data();
                }
            }

            // Collect action names
            std::vector<std::string> action_names;
            for (const auto& a : registry_.actions()) {
                action_names.push_back(a.name);
            }

            // Start workers
            auto tracker = std::make_shared<InFlightTracker>();
            std::vector<std::unique_ptr<ActionWorker>> workers;

            for (const auto& ra : registry_.actions()) {
                int count = worker_count(ra.name);
                auto worker = std::make_unique<ActionWorker>(
                    ra.name, ra.executor, *queue, *content_service, *tracker, app_name, count,
                    lookup_client.get(), artifact_client.get());
                worker->start();
                spdlog::info("Started {} worker(s) for {} ({})", count, ra.name, ra.action_type);
                workers.push_back(std::move(worker));
            }

            // Start heartbeat
            Heartbeat heartbeat(*queue, action_names, *tracker, app_name);
            heartbeat.start();
            spdlog::info("Heartbeat started");

            // Health probe (RAII: removed on any exit path, including exceptions)
            HealthProbe probe(health_probe_file);

            spdlog::info("Plugin running, waiting for work...");

            // Block until shutdown signal
            while (!detail::shutdown_requested) {
                std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }

            spdlog::info("Shutdown signal received, draining workers...");

            // Request stop on all workers
            for (auto& w : workers) w->request_stop();
            heartbeat.request_stop();
            if (supplier_runner) supplier_runner->request_stop();
            artifact_client->request_stop();

            // Drain in-flight work against a hard deadline shared across all
            // workers, abandoning any stragglers still running past it.
            auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(30);
            for (auto& w : workers) {
                if (!w->drain(deadline)) {
                    spdlog::warn("drain deadline elapsed; abandoning in-flight action tasks");
                }
            }
            heartbeat.join();
            if (supplier_runner) supplier_runner->join();
            artifact_client->join();

            spdlog::info("All workers drained");

            queue->close();
            return 0;

        } catch (const std::exception& ex) {
            spdlog::error("Plugin error: {}", ex.what());
            return 1;
        }
    }

private:
    PluginConfig config_;
    Registry registry_;
    std::vector<FlowPlan> flows_;
    std::vector<Variable> variables_;
    std::vector<nlohmann::json> integration_tests_;
    std::map<std::string, int> worker_counts_;
    std::map<std::string, std::shared_ptr<LookupTableSupplierBase>> suppliers_;

    int worker_count(const std::string& action_name) const {
        // Check explicit setting
        auto it = worker_counts_.find(action_name);
        if (it != worker_counts_.end() && it->second > 0) {
            return it->second;
        }

        // Check per-action env var: ACTIONS_ACTIONTHREADS_{NAME}
        std::string env_name = action_name;
        std::ranges::replace(env_name, '.', '_');
        std::ranges::replace(env_name, '-', '_');
        std::ranges::transform(env_name, env_name.begin(), ::toupper);
        std::string per_action_env = "ACTIONS_ACTIONTHREADS_" + env_name;
        auto val = env_or_default(per_action_env, "");
        if (!val.empty()) {
            try {
                return std::stoi(val);
            } catch (...) {  // NOLINT(bugprone-empty-catch): fall through on parse failure
            }
        }

        // Check global env var
        val = env_or_default("ACTION_THREADS", "");
        if (!val.empty()) {
            try {
                return std::stoi(val);
            } catch (...) {  // NOLINT(bugprone-empty-catch): fall through on parse failure
            }
        }

        return 1;
    }

    PluginRegistration build_registration() const {
        PluginRegistration reg;
        reg.image = config_.image;
        reg.image_pull_secret = config_.image_pull_secret;
        reg.plugin_coordinates = config_.coordinates;
        reg.display_name = config_.display_name;
        reg.description = config_.description;
        reg.action_kit_version = deltafi::action_kit_version;
        reg.dependencies = config_.dependencies;
        reg.variables = variables_;

        for (const auto& ra : registry_.actions()) {
            reg.actions.push_back(ActionDescriptor{.name = ra.name,
                                                   .description = ra.description,
                                                   .type = ra.action_type,
                                                   .supports_join = ra.supports_join,
                                                   .schema = ra.schema,
                                                   .options = ra.options});
        }

        for (const auto& fp : flows_) {
            reg.flow_plans.push_back(fp.raw);
        }

        reg.integration_tests = integration_tests_;

        for (const auto& [name, supplier] : suppliers_) {
            reg.lookup_tables.push_back(supplier->table());
        }

        return reg;
    }
};

namespace detail {

// Pending action registrations, populated by DELTAFI_ACTION macros at static init time.
using ActionRegistrar = std::function<void(Registry&, const std::string&)>;
inline std::vector<ActionRegistrar>& pending_actions() {
    static std::vector<ActionRegistrar> v;
    return v;
}

// Pending supplier registrations, populated by DELTAFI_SUPPLIER macros at static init time.
using SupplierRegistrar = std::function<std::shared_ptr<LookupTableSupplierBase>()>;
inline std::vector<SupplierRegistrar>& pending_suppliers() {
    static std::vector<SupplierRegistrar> v;
    return v;
}

}  // namespace detail

}  // namespace deltafi

// Token-paste helpers for unique variable names.
#define DELTAFI_CONCAT_(a, b) a##b
#define DELTAFI_CONCAT(a, b) DELTAFI_CONCAT_(a, b)

// Register a plugin-scoped artifact watch. Args match deltafi::watch_artifact() overloads.
#define DELTAFI_WATCH_ARTIFACT(...)                                                       \
    namespace {                                                                           \
    static const bool DELTAFI_CONCAT(_deltafi_watch_,                                     \
                                     __COUNTER__) = deltafi::watch_artifact(__VA_ARGS__); \
    }

// Register a shared artifact watch. Args match deltafi::watch_shared_artifact().
#define DELTAFI_WATCH_SHARED_ARTIFACT(...)                                                       \
    namespace {                                                                                  \
    static const bool DELTAFI_CONCAT(_deltafi_swatch_,                                           \
                                     __COUNTER__) = deltafi::watch_shared_artifact(__VA_ARGS__); \
    }

// Self-register an action type with name and description.
// Place after the class definition in each action header.
#define DELTAFI_ACTION(ActionClass, action_name_, action_desc_)    \
    template<>                                                     \
    struct deltafi::action_traits<ActionClass> {                   \
        static constexpr auto name = action_name_;                 \
        static constexpr auto description = action_desc_;          \
    };                                                             \
    namespace {                                                    \
    static const bool _deltafi_reg_##ActionClass =                 \
        (deltafi::detail::pending_actions().emplace_back(          \
             [](deltafi::Registry& r, const std::string& domain) { \
                 r.register_action<ActionClass>(domain);           \
             }),                                                   \
         true);                                                    \
    }

// Self-register a lookup table supplier. Place after the class definition in each supplier header.
#define DELTAFI_SUPPLIER(SupplierClass)                                  \
    namespace {                                                          \
    static const bool _deltafi_reg_##SupplierClass =                     \
        (deltafi::detail::pending_suppliers().emplace_back(              \
             []() -> std::shared_ptr<deltafi::LookupTableSupplierBase> { \
                 return std::make_shared<SupplierClass>();               \
             }),                                                         \
         true);                                                          \
    }

// Default plugin version when not set by the build system (-DVERSION=...).
#ifndef VERSION
#define VERSION "0.0.0-dev"
#endif

// Macro to generate main() for a DeltaFi C++ plugin.
// Actions and suppliers are auto-registered via DELTAFI_ACTION and DELTAFI_SUPPLIER macros.
#define DELTAFI_PLUGIN(group, artifact, desc, _domain_)                   \
    int main() {                                                          \
        try {                                                             \
            deltafi::PluginConfig config;                                 \
            config.coordinates = {group, artifact, VERSION};              \
            config.description = desc;                                    \
            config.domain = _domain_;                                     \
            deltafi::Plugin plugin(std::move(config));                    \
            for (auto& reg : deltafi::detail::pending_actions()) {        \
                reg(plugin.registry(), _domain_);                         \
            }                                                             \
            for (auto& reg : deltafi::detail::pending_suppliers()) {      \
                plugin.register_supplier(reg());                          \
            }                                                             \
            plugin.load_flows("flows");                                   \
            plugin.load_variables("flows");                               \
            plugin.load_integration_tests("integration");                 \
            return plugin.run();                                          \
        } catch (const std::exception& ex) {                              \
            std::fputs("plugin startup failed: ", stderr);                \
            std::fputs(ex.what(), stderr);                                \
            std::fputs("\n", stderr);                                     \
            return 1;                                                     \
        } catch (...) {                                                   \
            std::fputs("plugin startup failed: unknown error\n", stderr); \
            return 1;                                                     \
        }                                                                 \
    }
