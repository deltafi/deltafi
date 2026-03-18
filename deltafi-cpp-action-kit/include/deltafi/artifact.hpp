// ABOUTME: Artifact registry client for publishing and retrieving artifacts via core's REST API.
// ABOUTME: Supports plugin-scoped and shared artifacts, caching, version change callbacks via Valkey pub/sub.

#pragma once

#include <chrono>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "httplib.h"

#include "deltafi/internal/resp_client.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/types.hpp"

namespace deltafi {

inline constexpr const char* artifacts_path = "/api/v2/artifacts";
inline constexpr const char* shared_owner = "shared";
inline constexpr const char* active_alias = "active";
inline constexpr const char* version_changed_channel = "deltafi:artifact:version-changed";
inline constexpr const char* plugin_prefix = "plugins/";

// Version change event received via Valkey pub/sub.
struct VersionChangedEvent {
    std::string prefix;
    std::string artifact_name;
    std::string alias;
    std::string previous_version;
    std::string new_version;
    double timestamp = 0;

    bool is_shared() const { return prefix == shared_owner; }

    std::string owner() const {
        if (is_shared()) return shared_owner;
        if (prefix.starts_with(plugin_prefix)) {
            return prefix.substr(std::string(plugin_prefix).size());
        }
        return prefix;
    }
};

inline void from_json(const nlohmann::json& j, VersionChangedEvent& e) {
    j.at("prefix").get_to(e.prefix);
    j.at("artifactName").get_to(e.artifact_name);
    if (j.contains("alias")) j.at("alias").get_to(e.alias);
    if (j.contains("previousVersion")) j.at("previousVersion").get_to(e.previous_version);
    if (j.contains("newVersion")) j.at("newVersion").get_to(e.new_version);
    if (j.contains("timestamp")) j.at("timestamp").get_to(e.timestamp);
}

// Options for publishing an artifact.
struct PublishOptions {
    std::string version;
    std::string description;
    std::optional<bool> make_active;
    std::string alias;
    std::map<std::string, std::string> labels;
};

// Server response after publishing an artifact.
struct PublishResponse {
    std::optional<std::string> plugin_name;
    std::string artifact_name;
    std::string version;
    bool is_active = false;
    std::string sha256;
};

inline void from_json(const nlohmann::json& j, PublishResponse& r) {
    if (j.contains("pluginName") && !j["pluginName"].is_null()) {
        r.plugin_name = j["pluginName"].get<std::string>();
    }
    if (j.contains("artifactName")) j.at("artifactName").get_to(r.artifact_name);
    if (j.contains("version")) j.at("version").get_to(r.version);
    r.is_active = j.value("isActive", false);
    if (j.contains("sha256")) j.at("sha256").get_to(r.sha256);
}

// Metadata about a downloaded artifact, extracted from response headers.
struct ArtifactManifest {
    std::string version;
    std::string description;
    std::string content_type;
    std::string sha256;
    std::map<std::string, std::string> labels;
    std::string source_prefix;
};

// A fully downloaded artifact with its metadata.
struct FetchedArtifact {
    std::vector<uint8_t> data;
    ArtifactManifest manifest;

    std::string data_string() const {
        return std::string(data.begin(), data.end());
    }
};

// Default data to publish when an artifact is not found.
struct ArtifactDefault {
    std::vector<uint8_t> data;
    std::string content_type;
    std::string description;

    ArtifactDefault() = default;
    ArtifactDefault(std::vector<uint8_t> d, std::string ct, std::string desc)
        : data(std::move(d)), content_type(std::move(ct)), description(std::move(desc)) {}
    ArtifactDefault(const std::string& s, std::string ct, std::string desc)
        : data(s.begin(), s.end()), content_type(std::move(ct)), description(std::move(desc)) {}
};

// Abstract interface for artifact operations from actions.
class ArtifactClientBase {
public:
    virtual ~ArtifactClientBase() = default;

    virtual PublishResponse publish_artifact(
        const std::string& name, const std::vector<uint8_t>& data,
        const std::string& content_type,
        const PublishOptions* opts = nullptr) = 0;

    virtual PublishResponse publish_shared_artifact(
        const std::string& name, const std::vector<uint8_t>& data,
        const std::string& content_type,
        const PublishOptions* opts = nullptr) = 0;

    virtual FetchedArtifact get_artifact(
        const std::string& name, const std::string& alias = "") = 0;

    virtual FetchedArtifact get_shared_artifact(
        const std::string& name, const std::string& alias = "") = 0;
};

// HTTP-based artifact client with caching and version change callbacks.
class ArtifactClient : public ArtifactClientBase {
public:
    ArtifactClient(const std::string& core_url, const std::string& owner)
        : core_url_(core_url), owner_(owner) {
        cache_ttl_secs_ = parse_ttl_env("ARTIFACT_CACHE_TTL", 10);
        failure_cache_ttl_secs_ = parse_ttl_env("ARTIFACT_FAILURE_CACHE_TTL", 1);
        client_ = std::make_unique<httplib::Client>(core_url);
        client_->set_connection_timeout(std::chrono::seconds(10));
        client_->set_read_timeout(std::chrono::minutes(5));
    }

    PublishResponse publish_artifact(
        const std::string& name, const std::vector<uint8_t>& data,
        const std::string& content_type,
        const PublishOptions* opts = nullptr) override {
        return publish(owner_, name, data, content_type, opts);
    }

    PublishResponse publish_shared_artifact(
        const std::string& name, const std::vector<uint8_t>& data,
        const std::string& content_type,
        const PublishOptions* opts = nullptr) override {
        return publish(shared_owner, name, data, content_type, opts);
    }

    FetchedArtifact get_artifact(
        const std::string& name, const std::string& alias = "") override {
        return get(owner_, name, alias);
    }

    FetchedArtifact get_shared_artifact(
        const std::string& name, const std::string& alias = "") override {
        return get(shared_owner, name, alias);
    }

    // Register a callback that auto-fetches the artifact on version change or startup.
    void on_update_fetch(const std::string& artifact_name,
                         std::function<void(const FetchedArtifact*, const std::string&)> handler,
                         const std::string& alias = "") {
        on_update_fetch_impl(owner_, artifact_name, alias, nullptr, std::move(handler));
    }

    // Like on_update_fetch, but publishes a default if the artifact is not found.
    void on_update_fetch_with_default(
        const std::string& artifact_name,
        ArtifactDefault default_artifact,
        std::function<void(const FetchedArtifact*, const std::string&)> handler,
        const std::string& alias = "") {
        auto def = std::make_shared<ArtifactDefault>(std::move(default_artifact));
        on_update_fetch_impl(owner_, artifact_name, alias, def.get(), std::move(handler));
        // Keep the default alive as long as the callback exists
        defaults_.push_back(std::move(def));
    }

    // Register a callback for shared artifact version changes with auto-fetch.
    void on_shared_update_fetch(
        const std::string& artifact_name,
        std::function<void(const FetchedArtifact*, const std::string&)> handler,
        const std::string& alias = "") {
        on_update_fetch_impl(shared_owner, artifact_name, alias, nullptr, std::move(handler));
    }

    // Register a raw version change callback for the plugin's namespace.
    void on_update(const std::string& artifact_name,
                   std::function<void(const VersionChangedEvent&)> handler,
                   const std::string& alias = "") {
        register_callback(owner_, artifact_name, alias, std::move(handler));
    }

    // Register a raw version change callback for shared artifacts.
    void on_shared_update(const std::string& artifact_name,
                          std::function<void(const VersionChangedEvent&)> handler,
                          const std::string& alias = "") {
        register_callback(shared_owner, artifact_name, alias, std::move(handler));
    }

    // Fire callbacks that haven't been startup-fired yet. Safe to call multiple times.
    void fire_startup_callbacks() {
        std::vector<ArtifactCallback> unfired;
        {
            std::lock_guard lock(callback_mu_);
            if (startup_fired_idx_ >= static_cast<int>(callbacks_.size())) return;
            unfired.assign(callbacks_.begin() + startup_fired_idx_, callbacks_.end());
            startup_fired_idx_ = static_cast<int>(callbacks_.size());
        }

        for (const auto& cb : unfired) {
            std::string prefix = std::string(plugin_prefix) + cb.owner;
            if (cb.owner == shared_owner) prefix = shared_owner;

            VersionChangedEvent event;
            event.prefix = prefix;
            event.artifact_name = cb.artifact_name;
            event.alias = cb.alias;

            try {
                cb.handler(event);
            } catch (const std::exception& ex) {
                spdlog::error("Artifact startup callback failed for {}/{} (alias {}): {}",
                              cb.owner, cb.artifact_name, cb.alias, ex.what());
            }
        }
    }

    // Start the version change listener on a dedicated subscription connection.
    void start_artifact_listener(ActionEventQueue& queue) {
        listener_ = std::jthread([this, &queue](std::stop_token stop) {
            while (!stop.stop_requested()) {
                try {
                    run_listener(queue, stop);
                } catch (const std::exception& ex) {
                    if (!stop.stop_requested()) {
                        spdlog::error("Artifact listener error: {}", ex.what());
                    }
                }
                // Reconnect after delay if subscription drops
                if (!stop.stop_requested()) {
                    spdlog::info("Reconnecting to artifact version change channel...");
                    std::this_thread::sleep_for(std::chrono::seconds(5));
                }
            }
        });
    }

    void request_stop() {
        listener_.request_stop();
    }

    void join() {
        if (listener_.joinable()) listener_.join();
    }

    void set_cache_ttl(int seconds) {
        std::lock_guard lock(cache_mu_);
        cache_ttl_secs_ = seconds;
    }

    void clear_cache() {
        std::lock_guard lock(cache_mu_);
        cache_.clear();
    }

private:
    std::string core_url_;
    std::string owner_;
    std::unique_ptr<httplib::Client> client_;
    int cache_ttl_secs_ = 10;
    int failure_cache_ttl_secs_ = 1;

    struct CacheEntry {
        FetchedArtifact artifact;
        std::string error_msg;
        bool has_error = false;
        std::chrono::steady_clock::time_point expires_at;
    };

    std::mutex cache_mu_;
    std::map<std::string, CacheEntry> cache_;

    struct ArtifactCallback {
        std::string owner;
        std::string artifact_name;
        std::string alias;
        std::function<void(const VersionChangedEvent&)> handler;
    };

    std::mutex callback_mu_;
    std::vector<ArtifactCallback> callbacks_;
    int startup_fired_idx_ = 0;
    std::vector<std::shared_ptr<ArtifactDefault>> defaults_;

    std::jthread listener_;

    static int parse_ttl_env(const char* env_name, int default_secs) {
        auto val = env_or_default(env_name, "");
        if (val.empty()) return default_secs;
        try { return std::stoi(val); } catch (...) {}
        return default_secs;
    }

    static std::string cache_key(const std::string& owner,
                                  const std::string& name,
                                  const std::string& alias) {
        return owner + "/" + name + "/" + alias;
    }

    static httplib::Headers auth_headers() {
        return {
            {"X-User-Permissions", "Admin"},
            {"X-User-Name", "cpp-plugin"}
        };
    }

    PublishResponse publish(const std::string& owner, const std::string& name,
                            const std::vector<uint8_t>& data,
                            const std::string& content_type,
                            const PublishOptions* opts) {
        httplib::MultipartFormDataItems items;
        items.push_back({
            "file", std::string(data.begin(), data.end()),
            name, content_type
        });

        if (opts) {
            if (!opts->version.empty()) {
                items.push_back({"version", opts->version, "", ""});
            }
            if (!opts->description.empty()) {
                items.push_back({"description", opts->description, "", ""});
            }
            if (opts->make_active.has_value()) {
                items.push_back({"makeActive",
                    *opts->make_active ? "true" : "false", "", ""});
            }
            if (!opts->alias.empty()) {
                items.push_back({"alias", opts->alias, "", ""});
            }
            for (const auto& [k, v] : opts->labels) {
                items.push_back({k, v, "", ""});
            }
        }

        auto path = std::string(artifacts_path) + "/" + owner + "/" + name;
        auto res = client_->Post(path, auth_headers(), items);

        if (!res) {
            throw std::runtime_error("publish request failed: connection error");
        }
        if (res->status != 201) {
            throw std::runtime_error("publish returned HTTP " +
                std::to_string(res->status) + ": " + res->body);
        }

        auto resp = nlohmann::json::parse(res->body).get<PublishResponse>();
        invalidate_cache(owner, name);
        return resp;
    }

    FetchedArtifact get(const std::string& owner, const std::string& name,
                        const std::string& alias_in) {
        auto alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        auto key = cache_key(owner, name, alias);

        {
            std::lock_guard lock(cache_mu_);
            auto it = cache_.find(key);
            if (it != cache_.end()) {
                auto now = std::chrono::steady_clock::now();
                if (now < it->second.expires_at) {
                    if (it->second.has_error) {
                        throw std::runtime_error(it->second.error_msg);
                    }
                    return it->second.artifact;
                }
            }
        }

        try {
            auto fetched = fetch(owner, name, alias);
            std::lock_guard lock(cache_mu_);
            cache_[key] = CacheEntry{fetched, {}, false,
                std::chrono::steady_clock::now() + std::chrono::seconds(cache_ttl_secs_)};
            return fetched;
        } catch (const std::exception& ex) {
            std::lock_guard lock(cache_mu_);
            cache_[key] = CacheEntry{{}, ex.what(), true,
                std::chrono::steady_clock::now() + std::chrono::seconds(failure_cache_ttl_secs_)};
            throw;
        }
    }

    FetchedArtifact fetch(const std::string& owner, const std::string& name,
                          const std::string& alias) {
        auto path = std::string(artifacts_path) + "/" + owner + "/" +
                    name + "/versions/" + alias;
        auto res = client_->Get(path, auth_headers());

        if (!res) {
            throw std::runtime_error("download request failed: connection error");
        }
        if (res->status == 404) {
            throw std::runtime_error("artifact not found: " + owner + "/" +
                                     name + " (alias \"" + alias + "\")");
        }
        if (res->status != 200) {
            throw std::runtime_error("download returned HTTP " +
                std::to_string(res->status) + ": " + res->body);
        }

        FetchedArtifact fetched;
        fetched.data = std::vector<uint8_t>(res->body.begin(), res->body.end());
        fetched.manifest = parse_manifest_headers(res->headers);
        return fetched;
    }

    static ArtifactManifest parse_manifest_headers(const httplib::Headers& headers) {
        ArtifactManifest m;
        auto get = [&](const std::string& key) -> std::string {
            auto it = headers.find(key);
            return (it != headers.end()) ? it->second : "";
        };
        m.version = get("X-Artifact-Version");
        m.sha256 = get("X-Artifact-Sha256");
        m.description = get("X-Artifact-Description");
        m.content_type = get("Content-Type");
        m.source_prefix = get("X-Artifact-Source-Prefix");

        auto labels_json = get("X-Artifact-Labels");
        if (!labels_json.empty()) {
            try {
                m.labels = nlohmann::json::parse(labels_json)
                    .get<std::map<std::string, std::string>>();
            } catch (...) {}
        }
        return m;
    }

    void invalidate_cache(const std::string& owner, const std::string& name) {
        auto prefix = owner + "/" + name + "/";
        std::lock_guard lock(cache_mu_);
        for (auto it = cache_.begin(); it != cache_.end(); ) {
            if (it->first.starts_with(prefix)) {
                it = cache_.erase(it);
            } else {
                ++it;
            }
        }
    }

    void on_update_fetch_impl(
        const std::string& owner, const std::string& artifact_name,
        const std::string& alias_in, const ArtifactDefault* default_artifact,
        std::function<void(const FetchedArtifact*, const std::string&)> handler) {

        auto fetch_alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        auto alias = fetch_alias;

        // Capture by value for the lambda
        auto owner_copy = owner;
        auto name_copy = artifact_name;
        bool has_default = (default_artifact != nullptr);
        ArtifactDefault default_copy;
        if (has_default) default_copy = *default_artifact;

        auto fetch_handler = [this, owner_copy, name_copy, fetch_alias,
                               has_default, default_copy, handler]
                              (const VersionChangedEvent&) {
            try {
                auto fetched = this->get(owner_copy, name_copy, fetch_alias);
                handler(&fetched, "");
            } catch (const std::exception& ex) {
                if (has_default) {
                    spdlog::info("Artifact {}/{} not found, publishing default",
                                 owner_copy, name_copy);
                    try {
                        PublishOptions pub_opts;
                        pub_opts.version = "1";
                        pub_opts.description = default_copy.description;
                        pub_opts.make_active = true;
                        this->publish(owner_copy, name_copy, default_copy.data,
                                      default_copy.content_type, &pub_opts);
                        auto fetched = this->get(owner_copy, name_copy, fetch_alias);
                        handler(&fetched, "");
                    } catch (const std::exception& pub_ex) {
                        spdlog::error("Failed to publish default artifact {}/{}: {}",
                                      owner_copy, name_copy, pub_ex.what());
                        std::string err = std::string("artifact not found and default publish failed: ") + pub_ex.what();
                        handler(nullptr, err);
                    }
                } else {
                    handler(nullptr, ex.what());
                }
            }
        };

        std::lock_guard lock(callback_mu_);
        callbacks_.push_back(ArtifactCallback{
            owner, artifact_name, alias, std::move(fetch_handler)
        });
    }

    void register_callback(const std::string& owner,
                            const std::string& artifact_name,
                            const std::string& alias_in,
                            std::function<void(const VersionChangedEvent&)> handler) {
        auto alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        std::lock_guard lock(callback_mu_);
        callbacks_.push_back(ArtifactCallback{
            owner, artifact_name, alias, std::move(handler)
        });
    }

    void fire_callbacks(const VersionChangedEvent& event) {
        auto owner = event.owner();
        std::vector<ArtifactCallback> snapshot;
        {
            std::lock_guard lock(callback_mu_);
            snapshot = callbacks_;
        }

        for (const auto& cb : snapshot) {
            if (cb.owner == owner && cb.artifact_name == event.artifact_name &&
                cb.alias == event.alias) {
                try {
                    cb.handler(event);
                    spdlog::info("Artifact update callback completed for {}/{} version={}",
                                 owner, event.artifact_name, event.new_version);
                } catch (const std::exception& ex) {
                    spdlog::error("Artifact update callback failed for {}/{} (alias {}): {}",
                                 owner, event.artifact_name, event.alias, ex.what());
                }
            }
        }
    }

    void run_listener(ActionEventQueue& queue, std::stop_token stop) {
        auto conn = queue.create_subscribe_connection();
        conn->subscribe(version_changed_channel,
            [this](const std::string& payload) {
                handle_version_changed(payload);
            },
            [&stop]() { return stop.stop_requested(); });
    }

    void handle_version_changed(const std::string& payload) {
        VersionChangedEvent event;
        try {
            event = nlohmann::json::parse(payload).get<VersionChangedEvent>();
        } catch (const std::exception& ex) {
            spdlog::error("Failed to parse artifact version change event: {}", ex.what());
            return;
        }

        auto owner = event.owner();

        // Only process events for this plugin or shared artifacts
        if (owner != owner_ && owner != shared_owner) return;

        spdlog::info("Artifact version changed: {}/{} alias={} ({} -> {})",
                     owner, event.artifact_name, event.alias,
                     event.previous_version, event.new_version);

        invalidate_cache(owner, event.artifact_name);
        fire_callbacks(event);
    }
};

} // namespace deltafi
