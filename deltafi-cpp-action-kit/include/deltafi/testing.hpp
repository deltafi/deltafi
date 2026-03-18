// ABOUTME: Test utilities for unit testing DeltaFi actions without external services.
// ABOUTME: Provides in-memory content store, test context factory, and result assertion helpers.

#pragma once

#include <istream>
#include <map>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "deltafi/action.hpp"
#include "deltafi/artifact.hpp"
#include "deltafi/content.hpp"
#include "deltafi/event.hpp"
#include "deltafi/lookup.hpp"
#include "deltafi/result.hpp"
#include "deltafi/types.hpp"

namespace deltafi::testing {

// In-memory content store for testing without MinIO/S3.
class InMemoryContentStore : public ContentServiceBase {
public:
    Segment save(const std::string& did,
                 const std::vector<uint8_t>& data) override {
        auto uuid = generate_uuid();
        std::lock_guard lock(mu_);
        store_[uuid] = data;
        return Segment{uuid, 0, static_cast<int64_t>(data.size()), did};
    }

    std::vector<uint8_t> load(const std::vector<Segment>& segments) override {
        std::lock_guard lock(mu_);
        std::vector<uint8_t> result;
        for (const auto& seg : segments) {
            auto it = store_.find(seg.uuid);
            if (it == store_.end()) {
                throw std::runtime_error("segment not found: " + seg.uuid);
            }
            auto& data = it->second;
            auto offset = static_cast<size_t>(seg.offset);
            auto size = static_cast<size_t>(seg.size);
            if (size == 0) size = data.size();
            if (offset + size > data.size()) {
                size = data.size() - offset;
            }
            result.insert(result.end(), data.begin() + offset,
                          data.begin() + offset + size);
        }
        return result;
    }

    Segment save_from_reader(const std::string& did,
                              std::istream& reader, int64_t size) override {
        std::vector<uint8_t> data;
        if (size > 0) {
            data.resize(static_cast<size_t>(size));
            reader.read(reinterpret_cast<char*>(data.data()),
                        static_cast<std::streamsize>(size));
            data.resize(static_cast<size_t>(reader.gcount()));
        } else {
            std::istreambuf_iterator<char> begin(reader), end;
            data.assign(begin, end);
        }
        return save(did, data);
    }

    std::unique_ptr<std::istream> load_reader(
        const std::vector<Segment>& segments) override {
        auto data = load(segments);
        auto ss = std::make_unique<std::stringstream>();
        ss->write(reinterpret_cast<const char*>(data.data()),
                  static_cast<std::streamsize>(data.size()));
        ss->seekg(0);
        return ss;
    }

    void remove(const std::vector<Segment>& segments) override {
        std::lock_guard lock(mu_);
        for (const auto& seg : segments) {
            store_.erase(seg.uuid);
        }
    }

    // Create an ActionContent with data stored in this store.
    ActionContent create_content(const std::string& did,
                                 const std::string& name,
                                 const std::string& media_type,
                                 const std::vector<uint8_t>& data) {
        auto seg = save(did, data);
        ActionContent c{name, media_type, {seg}, {}};
        c.content_service_ = this;
        return c;
    }

    // Create an ActionContent from a string.
    ActionContent create_string_content(const std::string& did,
                                        const std::string& name,
                                        const std::string& media_type,
                                        const std::string& data) {
        return create_content(did, name, media_type,
                              std::vector<uint8_t>(data.begin(), data.end()));
    }

private:
    std::mutex mu_;
    std::map<std::string, std::vector<uint8_t>> store_;
};

// Mock artifact client for testing actions that use the artifact registry.
class MockArtifactClient : public ArtifactClientBase {
public:
    // Pre-store an artifact for retrieval.
    void set_artifact(const std::string& name, const std::string& alias,
                      FetchedArtifact artifact) {
        std::lock_guard lock(mu_);
        artifacts_[name + "/" + (alias.empty() ? "active" : alias)] = std::move(artifact);
    }

    PublishResponse publish_artifact(
        const std::string& name, const std::vector<uint8_t>& /*data*/,
        const std::string& /*content_type*/,
        const PublishOptions* /*opts*/ = nullptr) override {
        std::lock_guard lock(mu_);
        publish_count_++;
        return PublishResponse{{}, name, "1", true, ""};
    }

    PublishResponse publish_shared_artifact(
        const std::string& name, const std::vector<uint8_t>& /*data*/,
        const std::string& /*content_type*/,
        const PublishOptions* /*opts*/ = nullptr) override {
        std::lock_guard lock(mu_);
        publish_count_++;
        return PublishResponse{{}, name, "1", true, ""};
    }

    FetchedArtifact get_artifact(
        const std::string& name, const std::string& alias = "") override {
        std::lock_guard lock(mu_);
        auto key = name + "/" + (alias.empty() ? "active" : alias);
        auto it = artifacts_.find(key);
        if (it != artifacts_.end()) return it->second;
        throw std::runtime_error("artifact not found: " + name);
    }

    FetchedArtifact get_shared_artifact(
        const std::string& name, const std::string& alias = "") override {
        return get_artifact(name, alias);
    }

    int publish_count() const {
        std::lock_guard lock(mu_);
        return publish_count_;
    }

private:
    mutable std::mutex mu_;
    std::map<std::string, FetchedArtifact> artifacts_;
    int publish_count_ = 0;
};

// Mock lookup client for testing actions that query lookup tables.
class MockLookupClient : public LookupClientBase {
public:
    // Pre-configure results for a table name.
    void set_results(const std::string& table_name, LookupResults results) {
        std::lock_guard lock(mu_);
        results_[table_name] = std::move(results);
    }

    // Pre-configure an error for a table name.
    void set_error(const std::string& table_name, const std::string& message) {
        std::lock_guard lock(mu_);
        errors_[table_name] = message;
    }

    LookupResults lookup(const std::string& table_name,
                          const LookupOptions* /*opts*/ = nullptr) override {
        std::lock_guard lock(mu_);
        call_count_++;
        auto err_it = errors_.find(table_name);
        if (err_it != errors_.end()) {
            throw std::runtime_error(err_it->second);
        }
        auto it = results_.find(table_name);
        if (it != results_.end()) {
            return it->second;
        }
        return LookupResults{};
    }

    int call_count() const {
        std::lock_guard lock(mu_);
        return call_count_;
    }

private:
    mutable std::mutex mu_;
    std::map<std::string, LookupResults> results_;
    std::map<std::string, std::string> errors_;
    int call_count_ = 0;
};

// Test context with sensible defaults for unit testing actions.
struct TestContext {
    ActionContext action_context;
    std::unique_ptr<InMemoryContentStore> store;

    // Create an ActionContent using this context's DID and store.
    ActionContent content(const std::string& name,
                          const std::string& media_type,
                          const std::string& data) {
        return store->create_string_content(
            action_context.did, name, media_type, data);
    }
};

// Create a TestContext with sensible defaults for the given DID.
inline TestContext new_test_context(const std::string& did) {
    TestContext tc;
    tc.store = std::make_unique<InMemoryContentStore>();
    tc.action_context.did = did;
    tc.action_context.delta_file_name = "test-file";
    tc.action_context.data_source = "test-data-source";
    tc.action_context.flow_name = "test-flow";
    tc.action_context.flow_id = generate_uuid();
    tc.action_context.action_name = "TestAction";
    tc.action_context.action_version = "0.0.0";
    tc.action_context.hostname = "test-host";
    tc.action_context.system_name = "test-system";
    tc.action_context.start_time = std::chrono::system_clock::now();
    tc.action_context.content_service = tc.store.get();
    return tc;
}

// Convert a result variant to an ActionEvent for inspection.
inline ActionEvent result_event(const TransformResultType& result,
                                ActionContext& actx) {
    return result_to_event(result, actx.start_time);
}

inline ActionEvent result_event(const EgressResultType& result,
                                ActionContext& actx) {
    return result_to_event(result, actx.start_time);
}

inline ActionEvent result_event(const IngressResultType& result,
                                ActionContext& actx) {
    return result_to_event(result, actx.start_time);
}

// Assertion helpers — return typed event or throw on type mismatch.
inline const std::vector<TransformEvent>& require_transform(
    const ActionEvent& event) {
    if (event.type != "TRANSFORM") {
        throw std::runtime_error(
            "expected TRANSFORM event, got " + event.type);
    }
    return event.transform;
}

inline const ErrorEvent& require_error(const ActionEvent& event,
                                        const std::string& expected_cause = "") {
    if (event.type != "ERROR" || !event.error.has_value()) {
        throw std::runtime_error(
            "expected ERROR event, got " + event.type);
    }
    if (!expected_cause.empty() && event.error->cause != expected_cause) {
        throw std::runtime_error(
            "expected error cause '" + expected_cause +
            "', got '" + event.error->cause + "'");
    }
    return *event.error;
}

inline const FilterEvent& require_filter(const ActionEvent& event,
                                          const std::string& expected_message = "") {
    if (event.type != "FILTER" || !event.filter.has_value()) {
        throw std::runtime_error(
            "expected FILTER event, got " + event.type);
    }
    if (!expected_message.empty() && event.filter->message != expected_message) {
        throw std::runtime_error(
            "expected filter message '" + expected_message +
            "', got '" + event.filter->message + "'");
    }
    return *event.filter;
}

inline void require_egress(const ActionEvent& event) {
    if (event.type != "EGRESS") {
        throw std::runtime_error(
            "expected EGRESS event, got " + event.type);
    }
}

inline const IngressEvent& require_ingress(const ActionEvent& event) {
    if (event.type != "INGRESS" || !event.ingress.has_value()) {
        throw std::runtime_error(
            "expected INGRESS event, got " + event.type);
    }
    return *event.ingress;
}

// Run a transform action and return the ActionEvent.
template<typename Action>
ActionEvent run_transform(Action& action, ActionContext& actx,
                           TransformInput& input) {
    if constexpr (HasParamsType<Action>) {
        typename Action::Params params{};
        auto result = action.transform(actx, params, input);
        return result_event(result, actx);
    } else {
        auto result = action.transform(actx, input);
        return result_event(result, actx);
    }
}

// Run a transform action with explicit parameters and return the ActionEvent.
template<typename Action, typename Params>
ActionEvent run_transform(Action& action, ActionContext& actx,
                           Params& params, TransformInput& input) {
    auto result = action.transform(actx, params, input);
    return result_event(result, actx);
}

// Run an egress action and return the ActionEvent.
template<typename Action>
ActionEvent run_egress(Action& action, ActionContext& actx,
                        EgressInput& input) {
    if constexpr (HasParamsType<Action>) {
        typename Action::Params params{};
        auto result = action.egress(actx, params, input);
        return result_event(result, actx);
    } else {
        auto result = action.egress(actx, input);
        return result_event(result, actx);
    }
}

// Run a timed ingress action and return the ActionEvent.
template<typename Action>
ActionEvent run_ingress(Action& action, ActionContext& actx) {
    if constexpr (HasParamsType<Action>) {
        typename Action::Params params{};
        auto result = action.ingress(actx, params);
        return result_event(result, actx);
    } else {
        auto result = action.ingress(actx);
        return result_event(result, actx);
    }
}

} // namespace deltafi::testing
