// ABOUTME: In-process fake S3 server for tests: single/multipart PUT, Range GET, complete/abort.
// ABOUTME: Built on httplib::Server; ignores SigV4 auth (flow-only) and assembles multipart
// objects.

#pragma once

#include <atomic>
#include <cstddef>
#include <map>
#include <mutex>
#include <string>
#include <thread>

#include "httplib.h"

namespace deltafi::test {

// Minimal S3-compatible responder for the streaming tests. It does not validate
// AWS signatures (SigV4 correctness is covered by a separate unit vector); it
// implements just enough of the object + multipart API for the client's flow:
//   PUT    /bucket/key                                 -> single-object store
//   PUT    /bucket/key?partNumber=N&uploadId=UID       -> stage a part (returns ETag)
//   POST   /bucket/key?uploads                         -> begin upload (returns <UploadId>)
//   POST   /bucket/key?uploadId=UID                    -> assemble staged parts in order
//   DELETE /bucket/key?uploadId=UID                    -> abort (drop staged parts)
//   GET    /bucket/key   (optional Range)              -> serve bytes; empty+Range -> 416
class FakeS3Server {
public:
    FakeS3Server() {
        setup_routes();
        port_ = server_.bind_to_any_port("127.0.0.1");
        thread_ = std::thread([this] { server_.listen_after_bind(); });
        server_.wait_until_ready();
    }

    ~FakeS3Server() {
        server_.stop();
        if (thread_.joinable()) thread_.join();
    }

    FakeS3Server(const FakeS3Server&) = delete;
    FakeS3Server& operator=(const FakeS3Server&) = delete;
    FakeS3Server(FakeS3Server&&) = delete;
    FakeS3Server& operator=(FakeS3Server&&) = delete;

    std::string url() const { return "127.0.0.1:" + std::to_string(port_); }

    // Introspection for assertions.
    std::string stored_object(const std::string& path) const {
        std::scoped_lock lock(mu_);
        auto it = objects_.find(path);
        return it == objects_.end() ? std::string{} : it->second;
    }
    size_t max_single_put_bytes() const {
        std::scoped_lock lock(mu_);
        return max_single_put_;
    }
    bool has_pending_uploads() const {
        std::scoped_lock lock(mu_);
        return !uploads_.empty();
    }
    int part_count() const {
        std::scoped_lock lock(mu_);
        return part_count_;
    }

    // Force the Nth UploadPart (1-based) to fail with 500, for the abort test.
    void fail_part(int n) {
        std::scoped_lock lock(mu_);
        fail_part_ = n;
    }

private:
    void setup_routes() {
        server_.Put(R"(/.*)", [this](const httplib::Request& req, httplib::Response& res) {
            if (req.has_param("uploadId") && req.has_param("partNumber")) {
                handle_upload_part(req, res);
            } else {
                handle_single_put(req, res);
            }
        });
        server_.Post(R"(/.*)", [this](const httplib::Request& req, httplib::Response& res) {
            if (req.has_param("uploads")) {
                handle_create_multipart(req, res);
            } else if (req.has_param("uploadId")) {
                handle_complete_multipart(req, res);
            } else {
                res.status = 400;
            }
        });
        server_.Delete(R"(/.*)", [this](const httplib::Request& req, httplib::Response& res) {
            if (req.has_param("uploadId")) {
                std::scoped_lock lock(mu_);
                uploads_.erase(req.get_param_value("uploadId"));
                res.status = 204;
            } else {
                std::scoped_lock lock(mu_);
                objects_.erase(req.path);
                res.status = 204;
            }
        });
        server_.Get(R"(/.*)", [this](const httplib::Request& req, httplib::Response& res) {
            handle_get(req, res);
        });
    }

    void handle_single_put(const httplib::Request& req, httplib::Response& res) {
        std::scoped_lock lock(mu_);
        objects_[req.path] = req.body;
        max_single_put_ = std::max(max_single_put_, req.body.size());
        res.status = 200;
    }

    void handle_upload_part(const httplib::Request& req, httplib::Response& res) {
        std::scoped_lock lock(mu_);
        auto upload_id = req.get_param_value("uploadId");
        int part = std::stoi(req.get_param_value("partNumber"));
        part_count_++;
        if (fail_part_ > 0 && part_count_ == fail_part_) {
            res.status = 500;
            return;
        }
        uploads_[upload_id][part] = req.body;
        res.set_header("ETag", "\"etag-" + upload_id + "-" + std::to_string(part) + "\"");
        res.status = 200;
    }

    void handle_create_multipart(const httplib::Request& /*req*/, httplib::Response& res) {
        std::scoped_lock lock(mu_);
        auto upload_id = "upload-" + std::to_string(next_upload_id_++);
        uploads_[upload_id];  // create empty part map
        res.set_content("<?xml version=\"1.0\"?><InitiateMultipartUploadResult><UploadId>" +
                            upload_id + "</UploadId></InitiateMultipartUploadResult>",
                        "application/xml");
        res.status = 200;
    }

    void handle_complete_multipart(const httplib::Request& req, httplib::Response& res) {
        std::scoped_lock lock(mu_);
        auto upload_id = req.get_param_value("uploadId");
        auto it = uploads_.find(upload_id);
        if (it == uploads_.end()) {
            res.status = 404;
            return;
        }
        std::string assembled;
        for (const auto& [part_num, bytes] : it->second) {  // std::map iterates in part order
            assembled += bytes;
        }
        objects_[req.path] = assembled;
        uploads_.erase(it);
        res.set_content(
            "<?xml version=\"1.0\"?><CompleteMultipartUploadResult><ETag>\"complete\""
            "</ETag></CompleteMultipartUploadResult>",
            "application/xml");
        res.status = 200;
    }

    void handle_get(const httplib::Request& req, httplib::Response& res) {
        std::scoped_lock lock(mu_);
        auto it = objects_.find(req.path);
        const std::string& body = it == objects_.end() ? empty_ : it->second;

        if (!req.has_header("Range")) {
            res.set_content(body, "application/octet-stream");
            res.status = 200;
            return;
        }

        // Parse "bytes=start-end".
        auto range = req.get_header_value("Range");
        size_t start = 0;
        size_t end = body.empty() ? 0 : body.size() - 1;
        if (auto eq = range.find('='); eq != std::string::npos) {
            auto spec = range.substr(eq + 1);
            auto dash = spec.find('-');
            if (dash != std::string::npos) {
                if (dash > 0) start = std::stoull(spec.substr(0, dash));
                if (dash + 1 < spec.size()) end = std::stoull(spec.substr(dash + 1));
            }
        }
        if (body.empty() || start > end || start >= body.size()) {
            res.status = 416;  // InvalidRange (matches empty-object behavior)
            return;
        }
        end = std::min(end, body.size() - 1);
        res.set_content(body.substr(start, end - start + 1), "application/octet-stream");
        res.set_header("Content-Range", "bytes " + std::to_string(start) + "-" +
                                            std::to_string(end) + "/" +
                                            std::to_string(body.size()));
        res.status = 206;
    }

    httplib::Server server_;
    int port_ = 0;
    std::thread thread_;

    mutable std::mutex mu_;
    std::map<std::string, std::string> objects_;                 // path -> bytes
    std::map<std::string, std::map<int, std::string>> uploads_;  // uploadId -> partNum -> bytes
    std::atomic<int> next_upload_id_{1};
    size_t max_single_put_ = 0;
    int part_count_ = 0;
    int fail_part_ = 0;
    const std::string empty_;
};

}  // namespace deltafi::test
