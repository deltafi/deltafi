// ABOUTME: Artifact registry client for publishing and retrieving artifacts via core's REST API.
// ABOUTME: Supports plugin-scoped and shared artifacts, caching, version change callbacks via
// Valkey pub/sub.

#pragma once

#include <array>
#include <chrono>
#include <cstdint>
#include <condition_variable>
#include <exception>
#include <functional>
#include <istream>
#include <map>
#include <sstream>
#include <memory>
#include <mutex>
#include <optional>
#include <set>
#include <string>
#include <thread>
#include <tuple>
#include <utility>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "httplib.h"

#include "deltafi/internal/backoff.hpp"
#include "deltafi/internal/bounded_stream.hpp"
#include "deltafi/internal/resp_client.hpp"
#include "deltafi/internal/tls.hpp"
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

// Thrown when a publish conflicts with an artifact that already exists (HTTP 409).
class ArtifactConflict : public std::runtime_error {
public:
    using std::runtime_error::runtime_error;
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

    // True when this artifact was seeded as a plugin default (label default=true).
    bool is_default() const {
        auto it = labels.find("default");
        return it != labels.end() && it->second == "true";
    }
};

// A fully downloaded artifact with its metadata.
struct FetchedArtifact {
    std::vector<uint8_t> data;
    ArtifactManifest manifest;

    std::string data_string() const { return std::string(data.begin(), data.end()); }
};

// Extract an ArtifactManifest from the download response headers.
inline ArtifactManifest parse_manifest(const httplib::Headers& headers) {
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
            m.labels = nlohmann::json::parse(labels_json).get<std::map<std::string, std::string>>();
        } catch (...) {  // NOLINT(bugprone-empty-catch): parse fallback, labels left empty
        }
    }
    return m;
}

// A streaming artifact download: an incremental body reader plus the manifest, which
// is available as soon as the response headers arrive.
struct ArtifactStream {
    std::unique_ptr<std::istream> reader;
    ArtifactManifest manifest;
};

// Streams an artifact download over the content receiver on a producer thread; the
// manifest is captured from the response headers and exposed via wait_manifest().
class ArtifactStreamBuf : public internal::BoundedStreamBuf {
public:
    ArtifactStreamBuf(std::unique_ptr<httplib::Client> client, std::string path,
                      httplib::Headers headers)
        : client_(std::move(client)),
          path_(std::move(path)),
          headers_(std::move(headers)),
          producer_([this] { produce(); }) {}

    ~ArtifactStreamBuf() override {
        request_stop();
        if (producer_.joinable()) producer_.join();
    }

    ArtifactStreamBuf(const ArtifactStreamBuf&) = delete;
    ArtifactStreamBuf& operator=(const ArtifactStreamBuf&) = delete;
    ArtifactStreamBuf(ArtifactStreamBuf&&) = delete;
    ArtifactStreamBuf& operator=(ArtifactStreamBuf&&) = delete;

    // Block until the response headers arrive (manifest ready) or the request fails.
    ArtifactManifest wait_manifest() {
        std::unique_lock lock(hdr_mu_);
        hdr_cv_.wait(lock, [this] { return headers_ready_ || static_cast<bool>(hdr_error_); });
        if (hdr_error_) std::rethrow_exception(hdr_error_);
        return manifest_;
    }

private:
    void produce() {
        int status = 0;
        auto res = client_->Get(
            path_, headers_,
            [&](const httplib::Response& r) -> bool {
                status = r.status;
                if (r.status != 200) return false;  // don't stream an error body
                std::scoped_lock lock(hdr_mu_);
                manifest_ = parse_manifest(r.headers);
                headers_ready_ = true;
                hdr_cv_.notify_all();
                return true;
            },
            [this](const char* data, size_t len) -> bool { return push(data, len); });

        std::exception_ptr err;
        if (status == 404) {
            err = make_error("artifact not found");
        } else if (status != 0 && status != 200) {
            err = make_error("artifact download returned HTTP " + std::to_string(status));
        } else if (!headers_ready_ && !res) {
            err = make_error("artifact download failed: connection error");
        }
        if (err && !headers_ready_) {
            std::scoped_lock lock(hdr_mu_);
            hdr_error_ = err;
            hdr_cv_.notify_all();
        }
        mark_done(err);
    }

    static std::exception_ptr make_error(const std::string& msg) {
        return std::make_exception_ptr(std::runtime_error(msg));
    }

    std::unique_ptr<httplib::Client> client_;
    std::string path_;
    httplib::Headers headers_;

    std::mutex hdr_mu_;
    std::condition_variable hdr_cv_;
    ArtifactManifest manifest_;
    bool headers_ready_ = false;
    std::exception_ptr hdr_error_;

    std::thread producer_;  // declared last: starts after all members are initialized
};

// An istream that owns its streaming artifact streambuf.
class ArtifactContentStream : public std::istream {
public:
    ArtifactContentStream(std::unique_ptr<httplib::Client> client, std::string path,
                          httplib::Headers headers)
        : std::istream(nullptr), buf_(std::move(client), std::move(path), std::move(headers)) {
        rdbuf(&buf_);
    }
    ArtifactManifest wait_manifest() { return buf_.wait_manifest(); }

private:
    ArtifactStreamBuf buf_;
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

// Namespace resolution strategy for fetching an artifact.
enum class ArtifactScope : std::uint8_t {
    PLUGIN_FIRST,  // try the plugin namespace, then fall back to shared (the default)
    PLUGIN_ONLY,   // resolve only in the plugin namespace
    SHARED_ONLY,   // resolve only in the shared namespace
};

// Abstract interface for artifact operations from actions.
class ArtifactClientBase {
public:
    virtual ~ArtifactClientBase() = default;
    ArtifactClientBase() = default;
    ArtifactClientBase(const ArtifactClientBase&) = delete;
    ArtifactClientBase& operator=(const ArtifactClientBase&) = delete;
    ArtifactClientBase(ArtifactClientBase&&) = delete;
    ArtifactClientBase& operator=(ArtifactClientBase&&) = delete;

    virtual PublishResponse publish_artifact(const std::string& name,
                                             const std::vector<uint8_t>& data,
                                             const std::string& content_type,
                                             const PublishOptions* opts = nullptr) = 0;

    virtual PublishResponse publish_shared_artifact(const std::string& name,
                                                    const std::vector<uint8_t>& data,
                                                    const std::string& content_type,
                                                    const PublishOptions* opts = nullptr) = 0;

    // Publish an artifact by streaming an istream (bounded memory) instead of buffering it whole.
    virtual PublishResponse publish_from_reader(const std::string& name, std::istream& reader,
                                                const std::string& content_type,
                                                const PublishOptions* opts = nullptr) = 0;

    virtual PublishResponse publish_shared_from_reader(const std::string& name,
                                                       std::istream& reader,
                                                       const std::string& content_type,
                                                       const PublishOptions* opts = nullptr) = 0;

    virtual FetchedArtifact get_artifact(const std::string& name, const std::string& alias = "",
                                         ArtifactScope scope = ArtifactScope::PLUGIN_FIRST) = 0;

    virtual FetchedArtifact get_shared_artifact(const std::string& name,
                                                const std::string& alias = "") = 0;

    // Stream an artifact incrementally (bounded memory) instead of buffering it whole.
    virtual ArtifactStream stream_artifact(const std::string& name, const std::string& alias = "",
                                           ArtifactScope scope = ArtifactScope::PLUGIN_FIRST) = 0;

    virtual ArtifactStream stream_shared_artifact(const std::string& name,
                                                  const std::string& alias = "") = 0;
};

// HTTP-based artifact client with caching and version change callbacks.
class ArtifactClient : public ArtifactClientBase {
public:
    ArtifactClient(const std::string& core_url, const std::string& owner,
                   std::chrono::milliseconds poll_interval = std::chrono::milliseconds(300000))
        : core_url_(core_url),
          owner_(owner),
          cache_ttl_secs_(parse_ttl_env("ARTIFACT_CACHE_TTL", 10)),
          failure_cache_ttl_secs_(parse_ttl_env("ARTIFACT_FAILURE_CACHE_TTL", 1)),
          poll_interval_(poll_interval) {
        client_ = internal::make_httplib_client(core_url);
        client_->set_connection_timeout(std::chrono::seconds(10));
        client_->set_read_timeout(std::chrono::minutes(5));
        // Background safety net for missed pub/sub notifications (poll-drift invalidation).
        poller_ = std::jthread([this](const std::stop_token& stop) { run_poller(stop); });
    }

    PublishResponse publish_artifact(const std::string& name, const std::vector<uint8_t>& data,
                                     const std::string& content_type,
                                     const PublishOptions* opts = nullptr) override {
        return publish(owner_, name, data, content_type, opts);
    }

    PublishResponse publish_shared_artifact(const std::string& name,
                                            const std::vector<uint8_t>& data,
                                            const std::string& content_type,
                                            const PublishOptions* opts = nullptr) override {
        return publish(shared_owner, name, data, content_type, opts);
    }

    PublishResponse publish_from_reader(const std::string& name, std::istream& reader,
                                        const std::string& content_type,
                                        const PublishOptions* opts = nullptr) override {
        return publish_stream(owner_, name, reader, content_type, opts);
    }

    PublishResponse publish_shared_from_reader(const std::string& name, std::istream& reader,
                                               const std::string& content_type,
                                               const PublishOptions* opts = nullptr) override {
        return publish_stream(shared_owner, name, reader, content_type, opts);
    }

    FetchedArtifact get_artifact(const std::string& name, const std::string& alias = "",
                                 ArtifactScope scope = ArtifactScope::PLUGIN_FIRST) override {
        return resolve(name, alias, scope);
    }

    FetchedArtifact get_shared_artifact(const std::string& name,
                                        const std::string& alias = "") override {
        return resolve(name, alias, ArtifactScope::SHARED_ONLY);
    }

    ArtifactStream stream_artifact(const std::string& name, const std::string& alias = "",
                                   ArtifactScope scope = ArtifactScope::PLUGIN_FIRST) override {
        return stream_resolve(name, alias, scope);
    }

    ArtifactStream stream_shared_artifact(const std::string& name,
                                          const std::string& alias = "") override {
        return stream_resolve(name, alias, ArtifactScope::SHARED_ONLY);
    }

    // Register a callback that auto-fetches the artifact on version change or startup.
    void on_update_fetch(
        const std::string& artifact_name,
        const std::function<void(const FetchedArtifact*, const std::string&)>& handler,
        const std::string& alias = "") {
        on_update_fetch_impl(owner_, artifact_name, alias, nullptr, handler);
    }

    // Like on_update_fetch, but publishes a default if the artifact is not found.
    void on_update_fetch_with_default(
        const std::string& artifact_name, ArtifactDefault default_artifact,
        const std::function<void(const FetchedArtifact*, const std::string&)>& handler,
        const std::string& alias = "") {
        auto def = std::make_shared<ArtifactDefault>(std::move(default_artifact));
        on_update_fetch_impl(owner_, artifact_name, alias, def.get(), handler);
        // Keep the default alive as long as the callback exists
        defaults_.push_back(std::move(def));
    }

    // Register a callback for shared artifact version changes with auto-fetch.
    void on_shared_update_fetch(
        const std::string& artifact_name,
        const std::function<void(const FetchedArtifact*, const std::string&)>& handler,
        const std::string& alias = "") {
        on_update_fetch_impl(shared_owner, artifact_name, alias, nullptr, handler);
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
            std::scoped_lock lock(callback_mu_);
            if (std::cmp_greater_equal(startup_fired_idx_, callbacks_.size())) return;
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
                spdlog::error("Artifact startup callback failed for {}/{} (alias {}): {}", cb.owner,
                              cb.artifact_name, cb.alias, ex.what());
            }
        }
    }

    // Start the version change listener on a dedicated subscription connection.
    void start_artifact_listener(ActionEventQueue& queue) {
        listener_ = std::jthread([this, &queue](const std::stop_token& stop) {
            internal::PollBackoff backoff;
            while (!stop.stop_requested()) {
                auto started = std::chrono::steady_clock::now();
                try {
                    run_listener(queue, stop);
                } catch (const std::exception& ex) {
                    if (!stop.stop_requested()) {
                        spdlog::error("Artifact listener error: {}", ex.what());
                    }
                }
                if (stop.stop_requested()) break;
                // A subscription that stayed up for a while was healthy; reset the backoff so a
                // brief blip does not inherit a long delay. Rapid reconnect failures escalate.
                if (std::chrono::steady_clock::now() - started > std::chrono::seconds(1)) {
                    backoff.reset();
                }
                spdlog::info("Reconnecting to artifact version change channel (retry in ~{}ms)...",
                             backoff.current().count());
                if (!backoff.wait_after_error(stop)) break;
            }
        });
    }

    void request_stop() { listener_.request_stop(); }

    void join() {
        if (listener_.joinable()) listener_.join();
    }

    void set_cache_ttl(int seconds) {
        std::scoped_lock lock(cache_mu_);
        cache_ttl_secs_ = seconds;
    }

    void clear_cache() {
        std::scoped_lock lock(cache_mu_);
        cache_.clear();
    }

private:
    std::string core_url_;
    std::string owner_;
    std::unique_ptr<httplib::Client> client_;
    int cache_ttl_secs_ = 10;
    int failure_cache_ttl_secs_ = 1;
    std::chrono::milliseconds poll_interval_;

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
    // Declared last so it is destroyed (stopped + joined) before the cache/callbacks it touches.
    std::jthread poller_;

    static int parse_ttl_env(const char* env_name, int default_secs) {
        auto val = env_or_default(env_name, "");
        if (val.empty()) return default_secs;
        try {
            return std::stoi(val);
        } catch (...) {  // NOLINT(bugprone-empty-catch): parse fallback to default_secs
        }
        return default_secs;
    }

    static std::string cache_key(const std::string& owner, const std::string& name,
                                 const std::string& alias) {
        return owner + "/" + name + "/" + alias;
    }

    static httplib::Headers auth_headers() {
        return {{"X-User-Permissions", "Admin"}, {"X-User-Name", "cpp-plugin"}};
    }

    PublishResponse publish(const std::string& owner, const std::string& name,
                            const std::vector<uint8_t>& data, const std::string& content_type,
                            const PublishOptions* opts) {
        std::string buffer(data.begin(), data.end());
        std::istringstream reader(buffer);
        return publish_stream(owner, name, reader, content_type, opts);
    }

    // Publish by streaming the payload as multipart form-data: the file part header, then the
    // istream body pumped in bounded chunks, then the metadata fields and closing boundary. The
    // whole request body is produced on demand so a large artifact never materializes in memory.
    PublishResponse publish_stream(const std::string& owner, const std::string& name,
                                   std::istream& reader, const std::string& content_type,
                                   const PublishOptions* opts) {
        const std::string boundary = httplib::detail::make_multipart_data_boundary();

        httplib::UploadFormData file_item{
            .name = "file", .filename = name, .content_type = content_type};
        const std::string prefix =
            httplib::detail::serialize_multipart_formdata_item_begin(file_item, boundary);

        std::string suffix = httplib::detail::serialize_multipart_formdata_item_end();
        auto append_field = [&](const std::string& field_name, const std::string& value) {
            httplib::UploadFormData field{.name = field_name};
            suffix += httplib::detail::serialize_multipart_formdata_item_begin(field, boundary);
            suffix += value + httplib::detail::serialize_multipart_formdata_item_end();
        };
        if (opts) {
            if (!opts->version.empty()) append_field("version", opts->version);
            if (!opts->description.empty()) append_field("description", opts->description);
            if (opts->make_active.has_value())
                append_field("makeActive", *opts->make_active ? "true" : "false");
            if (!opts->alias.empty()) append_field("alias", opts->alias);
            for (const auto& [key, value] : opts->labels) append_field(key, value);
        }
        suffix += httplib::detail::serialize_multipart_formdata_finish(boundary);

        auto provider = [&](size_t /*offset*/, httplib::DataSink& sink) -> bool {
            if (!sink.write(prefix.data(), prefix.size())) return false;
            std::array<char, 64UL * 1024> chunk{};
            while (reader) {
                reader.read(chunk.data(), static_cast<std::streamsize>(chunk.size()));
                auto n = reader.gcount();
                if (n > 0 && !sink.write(chunk.data(), static_cast<size_t>(n))) return false;
            }
            if (!sink.write(suffix.data(), suffix.size())) return false;
            sink.done();
            return true;
        };

        auto path = std::string(artifacts_path) + "/" + owner + "/" + name;
        auto res = client_->Post(path, auth_headers(), provider,
                                 "multipart/form-data; boundary=" + boundary);

        if (!res) {
            throw std::runtime_error("publish request failed: connection error");
        }
        if (res->status == 409) {
            throw ArtifactConflict("artifact already exists: " + owner + "/" + name);
        }
        if (res->status != 201) {
            throw std::runtime_error("publish returned HTTP " + std::to_string(res->status) + ": " +
                                     res->body);
        }

        auto resp = nlohmann::json::parse(res->body).get<PublishResponse>();
        invalidate_cache(owner, name);
        return resp;
    }

    // The owner namespaces to try, in order, for a given scope.
    std::vector<std::string> owners_for(ArtifactScope scope) const {
        switch (scope) {
            case ArtifactScope::PLUGIN_ONLY:
                return {owner_};
            case ArtifactScope::SHARED_ONLY:
                return {std::string(shared_owner)};
            case ArtifactScope::PLUGIN_FIRST:
                return {owner_, std::string(shared_owner)};
        }
        return {owner_};
    }

    // Resolve an artifact across the scope's owner namespaces, returning the first that has it.
    FetchedArtifact resolve(const std::string& name, const std::string& alias,
                            ArtifactScope scope) {
        std::exception_ptr last_error;
        for (const auto& owner : owners_for(scope)) {
            try {
                return get(owner, name, alias);
            } catch (const std::exception&) {
                last_error = std::current_exception();  // not found here; try the next namespace
            }
        }
        if (last_error) std::rethrow_exception(last_error);
        throw std::runtime_error("artifact scope resolved to no namespaces");
    }

    // Like resolve(), but opens a streaming download on the first namespace that has the artifact.
    ArtifactStream stream_resolve(const std::string& name, const std::string& alias,
                                  ArtifactScope scope) {
        std::exception_ptr last_error;
        for (const auto& owner : owners_for(scope)) {
            try {
                return stream(owner, name, alias);
            } catch (const std::exception&) {
                last_error = std::current_exception();
            }
        }
        if (last_error) std::rethrow_exception(last_error);
        throw std::runtime_error("artifact scope resolved to no namespaces");
    }

    FetchedArtifact get(const std::string& owner, const std::string& name,
                        const std::string& alias_in) {
        auto alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        auto key = cache_key(owner, name, alias);

        {
            std::scoped_lock lock(cache_mu_);
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
            std::scoped_lock lock(cache_mu_);
            cache_[key] = CacheEntry{.artifact = fetched,
                                     .expires_at = std::chrono::steady_clock::now() +
                                                   std::chrono::seconds(cache_ttl_secs_)};
            return fetched;
        } catch (const std::exception& ex) {
            std::scoped_lock lock(cache_mu_);
            cache_[key] = CacheEntry{.error_msg = ex.what(),
                                     .has_error = true,
                                     .expires_at = std::chrono::steady_clock::now() +
                                                   std::chrono::seconds(failure_cache_ttl_secs_)};
            throw;
        }
    }

    FetchedArtifact fetch(const std::string& owner, const std::string& name,
                          const std::string& alias) {
        auto path = std::string(artifacts_path) + "/" + owner + "/" + name + "/versions/" + alias;
        auto res = client_->Get(path, auth_headers());

        if (!res) {
            throw std::runtime_error("download request failed: connection error");
        }
        if (res->status == 404) {
            throw std::runtime_error("artifact not found: " + owner + "/" + name + " (alias \"" +
                                     alias + "\")");
        }
        if (res->status != 200) {
            throw std::runtime_error("download returned HTTP " + std::to_string(res->status) +
                                     ": " + res->body);
        }

        FetchedArtifact fetched;
        fetched.data = std::vector<uint8_t>(res->body.begin(), res->body.end());
        fetched.manifest = parse_manifest(res->headers);
        return fetched;
    }

    // Open a streaming download on a dedicated client (so it never blocks the shared
    // client); blocks only until the response headers arrive to capture the manifest.
    ArtifactStream stream(const std::string& owner, const std::string& name,
                          const std::string& alias_in) {
        auto alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        auto path = std::string(artifacts_path) + "/" + owner + "/" + name + "/versions/" + alias;
        auto dedicated = internal::make_httplib_client(core_url_);
        dedicated->set_connection_timeout(std::chrono::seconds(10));
        auto s =
            std::make_unique<ArtifactContentStream>(std::move(dedicated), path, auth_headers());
        ArtifactManifest manifest = s->wait_manifest();  // throws on 404 / error
        return ArtifactStream{.reader = std::move(s), .manifest = manifest};
    }

    void invalidate_cache(const std::string& owner, const std::string& name) {
        auto prefix = owner + "/" + name + "/";
        std::scoped_lock lock(cache_mu_);
        for (auto it = cache_.begin(); it != cache_.end();) {
            if (it->first.starts_with(prefix)) {
                it = cache_.erase(it);
            } else {
                ++it;
            }
        }
    }

    void on_update_fetch_impl(
        const std::string& owner, const std::string& artifact_name, const std::string& alias_in,
        const ArtifactDefault* default_artifact,
        const std::function<void(const FetchedArtifact*, const std::string&)>& handler) {
        auto fetch_alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        auto alias = fetch_alias;

        // Capture by value for the lambda, which outlives this call — the copies
        // are deliberate (a reference would dangle).
        auto owner_copy = owner;         // NOLINT(performance-unnecessary-copy-initialization)
        auto name_copy = artifact_name;  // NOLINT(performance-unnecessary-copy-initialization)
        bool has_default = (default_artifact != nullptr);
        ArtifactDefault default_copy;
        if (has_default) default_copy = *default_artifact;

        auto fetch_handler = [this, owner_copy, name_copy, fetch_alias, has_default, default_copy,
                              handler](const VersionChangedEvent&) {
            try {
                auto fetched = this->get(owner_copy, name_copy, fetch_alias);
                handler(&fetched, "");
            } catch (const std::exception& ex) {
                if (has_default) {
                    spdlog::info("Artifact {}/{} not found, publishing default", owner_copy,
                                 name_copy);
                    try {
                        PublishOptions pub_opts;
                        pub_opts.version = "1";
                        pub_opts.description = default_copy.description;
                        pub_opts.make_active = true;
                        try {
                            this->publish(owner_copy, name_copy, default_copy.data,
                                          default_copy.content_type, &pub_opts);
                        } catch (const ArtifactConflict&) {
                            // Another instance seeded it first; drop the cached miss and use it.
                            spdlog::info(
                                "Default artifact {}/{} already exists; using the existing one",
                                owner_copy, name_copy);
                            this->invalidate_cache(owner_copy, name_copy);
                        }
                        auto fetched = this->get(owner_copy, name_copy, fetch_alias);
                        handler(&fetched, "");
                    } catch (const std::exception& pub_ex) {
                        spdlog::error("Failed to publish default artifact {}/{}: {}", owner_copy,
                                      name_copy, pub_ex.what());
                        std::string err =
                            std::string("artifact not found and default publish failed: ") +
                            pub_ex.what();
                        handler(nullptr, err);
                    }
                } else {
                    handler(nullptr, ex.what());
                }
            }
        };

        std::scoped_lock lock(callback_mu_);
        callbacks_.push_back(ArtifactCallback{.owner = owner,
                                              .artifact_name = artifact_name,
                                              .alias = alias,
                                              .handler = std::move(fetch_handler)});
    }

    void register_callback(const std::string& owner, const std::string& artifact_name,
                           const std::string& alias_in,
                           std::function<void(const VersionChangedEvent&)> handler) {
        auto alias = alias_in.empty() ? std::string(active_alias) : alias_in;
        std::scoped_lock lock(callback_mu_);
        callbacks_.push_back(ArtifactCallback{.owner = owner,
                                              .artifact_name = artifact_name,
                                              .alias = alias,
                                              .handler = std::move(handler)});
    }

    void fire_callbacks(const VersionChangedEvent& event) {
        auto owner = event.owner();
        std::vector<ArtifactCallback> snapshot;
        {
            std::scoped_lock lock(callback_mu_);
            snapshot = callbacks_;
        }

        for (const auto& cb : snapshot) {
            if (cb.owner == owner && cb.artifact_name == event.artifact_name &&
                cb.alias == event.alias) {
                try {
                    cb.handler(event);
                    spdlog::info("Artifact update callback completed for {}/{} version={}", owner,
                                 event.artifact_name, event.new_version);
                } catch (const std::exception& ex) {
                    spdlog::error("Artifact update callback failed for {}/{} (alias {}): {}", owner,
                                  event.artifact_name, event.alias, ex.what());
                }
            }
        }
    }

    // Poll-drift safety net: periodically re-list watched artifacts and, for any whose alias now
    // points at a different version than the one cached, invalidate and re-fire callbacks (catching
    // pub/sub notifications the listener missed).
    void run_poller(const std::stop_token& stop) {
        while (internal::backoff_sleep(stop, poll_interval_)) {
            try {
                poll_for_drift();
            } catch (const std::exception& ex) {
                spdlog::warn("Artifact poll-drift cycle failed: {}", ex.what());
            }
        }
    }

    void poll_for_drift() {
        std::vector<ArtifactCallback> snapshot;
        {
            std::scoped_lock lock(callback_mu_);
            snapshot = callbacks_;
        }
        if (snapshot.empty()) return;

        std::set<std::tuple<std::string, std::string, std::string>> watched;
        std::set<std::string> owners;
        for (const auto& cb : snapshot) {
            watched.emplace(cb.owner, cb.artifact_name, cb.alias);
            owners.insert(cb.owner);
        }

        std::map<std::string, std::map<std::string, std::map<std::string, std::string>>> listings;
        for (const auto& owner : owners) {
            listings[owner] = list_artifacts(owner);
        }

        for (const auto& [owner, name, alias] : watched) {
            auto owner_it = listings.find(owner);
            if (owner_it == listings.end()) continue;
            auto name_it = owner_it->second.find(name);
            if (name_it == owner_it->second.end()) continue;
            auto alias_it = name_it->second.find(alias);
            if (alias_it == name_it->second.end()) continue;  // alias absent (pinned/removed): skip

            auto current = cached_version(owner, name, alias);
            const std::string& remote = alias_it->second;
            if (remote == current) continue;  // no drift

            spdlog::info("Artifact poll detected drift for {}/{} alias={} ({} -> {})", owner, name,
                         alias, current, remote);
            VersionChangedEvent event;
            event.prefix = (owner == shared_owner) ? std::string(shared_owner)
                                                   : (std::string(plugin_prefix) + owner);
            event.artifact_name = name;
            event.alias = alias;
            event.previous_version = current;
            event.new_version = remote;
            invalidate_cache(owner, name);
            fire_callbacks(event);
        }
    }

    // GET /api/v2/artifacts/{owner} -> artifactName -> (alias -> version). Best-effort (empty on
    // any error, since the poller is only a safety net).
    std::map<std::string, std::map<std::string, std::string>> list_artifacts(
        const std::string& owner) {
        std::map<std::string, std::map<std::string, std::string>> result;
        auto res = client_->Get(std::string(artifacts_path) + "/" + owner, auth_headers());
        if (!res || res->status != 200) return result;
        try {
            auto j = nlohmann::json::parse(res->body);
            if (j.contains("artifacts")) {
                for (const auto& a : j["artifacts"]) {
                    auto name = a.value("artifactName", std::string());
                    if (name.empty()) continue;
                    if (a.contains("aliases") && !a["aliases"].is_null()) {
                        result[name] = a["aliases"].get<std::map<std::string, std::string>>();
                    } else {
                        result[name] = {};
                    }
                }
            }
        } catch (const std::exception& ex) {
            spdlog::warn("Failed to parse artifact list for {}: {}", owner, ex.what());
        }
        return result;
    }

    std::string cached_version(const std::string& owner, const std::string& name,
                               const std::string& alias) {
        std::scoped_lock lock(cache_mu_);
        auto it = cache_.find(cache_key(owner, name, alias));
        if (it != cache_.end() && !it->second.has_error) {
            return it->second.artifact.manifest.version;
        }
        return "";
    }

    void run_listener(ActionEventQueue& queue, std::stop_token stop) {
        auto conn = queue.create_subscribe_connection();
        conn->subscribe(
            version_changed_channel,
            [this](const std::string& payload) { handle_version_changed(payload); },
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

        spdlog::info("Artifact version changed: {}/{} alias={} ({} -> {})", owner,
                     event.artifact_name, event.alias, event.previous_version, event.new_version);

        invalidate_cache(owner, event.artifact_name);
        fire_callbacks(event);
    }
};

}  // namespace deltafi
