// ABOUTME: In-process fake core GraphQL endpoint for lookup-client tests (query + admin mutations).
// ABOUTME: Records requests and returns canned responses; supports a delay for coalescing tests.

#pragma once

#include <chrono>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>

#include "httplib.h"

namespace deltafi::test {

// Serves POST /api/v2/graphql. A test sets the response body it should return; the server
// records every request body and counts calls, and can delay each response so concurrent
// callers overlap (to exercise single-flight coalescing).
class FakeCoreServer {
public:
    FakeCoreServer() {
        server_.Post("/api/v2/graphql",
                     [this](const httplib::Request& req, httplib::Response& res) {
                         std::chrono::milliseconds delay{0};
                         std::string body;
                         {
                             std::scoped_lock lock(mu_);
                             requests_.push_back(req.body);
                             body = response_;
                             delay = delay_;
                         }
                         if (delay.count() > 0) std::this_thread::sleep_for(delay);
                         res.set_content(body, "application/json");
                         res.status = 200;
                     });
        port_ = server_.bind_to_any_port("127.0.0.1");
        thread_ = std::thread([this] { server_.listen_after_bind(); });
        server_.wait_until_ready();
    }

    ~FakeCoreServer() {
        server_.stop();
        if (thread_.joinable()) thread_.join();
    }

    FakeCoreServer(const FakeCoreServer&) = delete;
    FakeCoreServer& operator=(const FakeCoreServer&) = delete;
    FakeCoreServer(FakeCoreServer&&) = delete;
    FakeCoreServer& operator=(FakeCoreServer&&) = delete;

    std::string url() const { return "http://127.0.0.1:" + std::to_string(port_); }

    // Set the raw JSON envelope returned for every request (e.g. {"data":{"lookup":{...}}}).
    void set_response(const nlohmann::json& response) {
        std::scoped_lock lock(mu_);
        response_ = response.dump();
    }

    // Delay each response, so concurrent callers overlap in the handler.
    void set_response_delay(std::chrono::milliseconds delay) {
        std::scoped_lock lock(mu_);
        delay_ = delay;
    }

    int request_count() const {
        std::scoped_lock lock(mu_);
        return static_cast<int>(requests_.size());
    }

    // The parsed body of the Nth request (0-indexed), for asserting the query + variables.
    nlohmann::json request(size_t index) const {
        std::scoped_lock lock(mu_);
        return nlohmann::json::parse(requests_.at(index));
    }

private:
    httplib::Server server_;
    int port_ = 0;
    std::thread thread_;

    mutable std::mutex mu_;
    std::string response_ = R"({"data":{}})";
    std::chrono::milliseconds delay_{0};
    std::vector<std::string> requests_;
};

}  // namespace deltafi::test
