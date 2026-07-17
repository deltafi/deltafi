// ABOUTME: In-process fake core artifact endpoint for tests: GET download, POST multipart publish.
// ABOUTME: Built on httplib::Server; captures the published file so streamed uploads can be
// checked.

#pragma once

#include <map>
#include <mutex>
#include <string>
#include <thread>

#include <nlohmann/json.hpp>

#include "httplib.h"

namespace deltafi::test {

// Serves just enough of core's /api/v2/artifacts API for the streaming/scope tests:
//   GET  /{owner}/{name}/versions/{alias} -> body + X-Artifact-* headers (404 if not set)
//   POST /{owner}/{name}                  -> stores the multipart "file" part + "version" field
// An artifact set with set_artifact() is served for any owner; set_artifact_for(owner, ...)
// serves only that owner (and 404 elsewhere), so scope-fallback resolution can be exercised.
class FakeArtifactServer {
public:
    FakeArtifactServer() {
        server_.Get(R"(/api/v2/artifacts/.+/versions/.+)",
                    [this](const httplib::Request& req, httplib::Response& res) {
                        std::scoped_lock lock(mu_);
                        auto owner = owner_from_path(req.path);
                        auto it = by_owner_.find(owner);
                        if (it != by_owner_.end()) {
                            serve(res, it->second.body, it->second.version);
                        } else if (available_) {
                            serve(res, body_, version_);
                        } else {
                            res.status = 404;
                        }
                    });
        server_.Post(R"(/api/v2/artifacts/[^/]+/[^/]+)", [this](const httplib::Request& req,
                                                                httplib::Response& res) {
            std::scoped_lock lock(mu_);
            if (req.is_multipart_form_data()) {
                auto file = req.form.files.find("file");
                if (file != req.form.files.end()) published_ = file->second.content;
                auto version = req.form.fields.find("version");
                if (version != req.form.fields.end()) published_version_ = version->second.content;
            } else {
                published_ = req.body;
            }
            if (conflict_) {
                // Simulate a concurrent seed: the artifact now exists (a later GET finds it),
                // but this publish loses the race and gets a 409.
                by_owner_[owner_from_path(req.path)] = Entry{.body = published_, .version = "1"};
                res.status = 409;
                return;
            }
            res.set_content(R"({"artifactName":"a","version":"1","isActive":true,"sha256":""})",
                            "application/json");
            res.status = 201;
        });
        // List artifacts for an owner: GET /api/v2/artifacts/{owner} (single segment).
        server_.Get(R"(/api/v2/artifacts/[^/]+)", [this](const httplib::Request& req,
                                                         httplib::Response& res) {
            std::scoped_lock lock(mu_);
            nlohmann::json artifacts = nlohmann::json::array();
            auto it = listing_.find(owner_from_path(req.path));
            if (it != listing_.end()) {
                for (const auto& [name, aliases] : it->second) {
                    artifacts.push_back({{"artifactName", name}, {"aliases", aliases}});
                }
            }
            res.set_content(nlohmann::json{{"artifacts", artifacts}}.dump(), "application/json");
            res.status = 200;
        });
        port_ = server_.bind_to_any_port("127.0.0.1");
        thread_ = std::thread([this] { server_.listen_after_bind(); });
        server_.wait_until_ready();
    }

    ~FakeArtifactServer() {
        server_.stop();
        if (thread_.joinable()) thread_.join();
    }

    FakeArtifactServer(const FakeArtifactServer&) = delete;
    FakeArtifactServer& operator=(const FakeArtifactServer&) = delete;
    FakeArtifactServer(FakeArtifactServer&&) = delete;
    FakeArtifactServer& operator=(FakeArtifactServer&&) = delete;

    std::string url() const { return "http://127.0.0.1:" + std::to_string(port_); }

    void set_artifact(std::string body, std::string version) {
        std::scoped_lock lock(mu_);
        body_ = std::move(body);
        version_ = std::move(version);
        available_ = true;
    }

    // Serve this artifact only for the given owner namespace (404 for every other owner).
    void set_artifact_for(const std::string& owner, std::string body, std::string version) {
        std::scoped_lock lock(mu_);
        by_owner_[owner] = Entry{.body = std::move(body), .version = std::move(version)};
    }

    // Make publish (POST) return 409 while making the artifact appear (a concurrent-seed race).
    void set_publish_conflict(bool conflict) {
        std::scoped_lock lock(mu_);
        conflict_ = conflict;
    }

    // Record the version an owner's artifact alias points to, as reported by the list endpoint.
    void set_listing(const std::string& owner, const std::string& name, const std::string& alias,
                     const std::string& version) {
        std::scoped_lock lock(mu_);
        listing_[owner][name][alias] = version;
    }

    std::string published() const {
        std::scoped_lock lock(mu_);
        return published_;
    }

    std::string published_version() const {
        std::scoped_lock lock(mu_);
        return published_version_;
    }

private:
    struct Entry {
        std::string body;
        std::string version;
    };

    static void serve(httplib::Response& res, const std::string& body, const std::string& version) {
        res.set_header("X-Artifact-Version", version);
        res.set_header("X-Artifact-Sha256", "sha-test");
        res.set_content(body, "application/octet-stream");
        res.status = 200;
    }

    static std::string owner_from_path(const std::string& path) {
        const std::string prefix = "/api/v2/artifacts/";
        if (!path.starts_with(prefix)) return "";
        auto rest = path.substr(prefix.size());
        auto slash = rest.find('/');
        return slash == std::string::npos ? rest : rest.substr(0, slash);
    }

    httplib::Server server_;
    int port_ = 0;
    std::thread thread_;

    mutable std::mutex mu_;
    std::string body_;
    std::string version_;
    bool available_ = false;
    bool conflict_ = false;
    std::map<std::string, Entry> by_owner_;
    // owner -> name -> alias -> version, served by the list endpoint.
    std::map<std::string, std::map<std::string, std::map<std::string, std::string>>> listing_;
    std::string published_;
    std::string published_version_;
};

}  // namespace deltafi::test
