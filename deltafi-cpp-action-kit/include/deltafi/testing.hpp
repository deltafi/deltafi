// ABOUTME: Test utilities for unit testing DeltaFi actions without external services.
// ABOUTME: Provides in-memory content store, test context factory, and result assertion helpers.

#pragma once

#include <functional>
#include <istream>
#include <ostream>
#include <map>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

#include "deltafi/action.hpp"
#include "deltafi/artifact.hpp"
#include "deltafi/content.hpp"
#include "deltafi/event.hpp"
#include "deltafi/lookup.hpp"
#include "deltafi/registry.hpp"
#include "deltafi/result.hpp"
#include "deltafi/types.hpp"
#include "deltafi/worker.hpp"

namespace deltafi::testing {

// In-memory content store for testing without MinIO/S3.
class InMemoryContentStore : public ContentServiceBase {
public:
    Segment save(const std::string& did, const std::vector<uint8_t>& data) override {
        auto uuid = generate_uuid();
        std::scoped_lock lock(mu_);
        ++save_count_;
        if (fail_after_ > 0 && save_count_ >= fail_after_) {
            throw std::runtime_error("InMemoryContentStore: injected save failure");
        }
        store_[uuid] = data;
        return Segment{
            .uuid = uuid, .offset = 0, .size = static_cast<int64_t>(data.size()), .did = did};
    }

    std::vector<uint8_t> load(const std::vector<Segment>& segments) override {
        std::scoped_lock lock(mu_);
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
            auto begin = data.begin() + static_cast<std::ptrdiff_t>(offset);
            result.insert(result.end(), begin, begin + static_cast<std::ptrdiff_t>(size));
        }
        return result;
    }

    Segment save_from_reader(const std::string& did, std::istream& reader, int64_t size) override {
        std::vector<uint8_t> data;
        if (size > 0) {
            data.resize(static_cast<size_t>(size));
            reader.read(reinterpret_cast<char*>(data.data()), static_cast<std::streamsize>(size));
            data.resize(static_cast<size_t>(reader.gcount()));
        } else {
            std::istreambuf_iterator<char> begin(reader);
            std::istreambuf_iterator<char> end;
            data.assign(begin, end);
        }
        return save(did, data);
    }

    Segment save_from_writer(const std::string& did,
                             const std::function<void(std::ostream&)>& write_fn) override {
        std::ostringstream out;
        write_fn(out);
        auto s = out.str();
        return save(did, std::vector<uint8_t>(s.begin(), s.end()));
    }

    std::unique_ptr<std::istream> load_reader(const std::vector<Segment>& segments) override {
        auto data = load(segments);
        auto ss = std::make_unique<std::stringstream>();
        ss->write(reinterpret_cast<const char*>(data.data()),
                  static_cast<std::streamsize>(data.size()));
        ss->seekg(0);
        return ss;
    }

    void remove(const std::vector<Segment>& segments) override {
        std::scoped_lock lock(mu_);
        for (const auto& seg : segments) {
            store_.erase(seg.uuid);
        }
    }

    // Create an ActionContent with data stored in this store.
    ActionContent create_content(const std::string& did, const std::string& name,
                                 const std::string& media_type, const std::vector<uint8_t>& data) {
        auto seg = save(did, data);
        ActionContent c{.name = name, .media_type = media_type, .segments = {seg}, .tags = {}};
        c.content_service_ = this;
        return c;
    }

    // Create an ActionContent from a string.
    ActionContent create_string_content(const std::string& did, const std::string& name,
                                        const std::string& media_type, const std::string& data) {
        return create_content(did, name, media_type,
                              std::vector<uint8_t>(data.begin(), data.end()));
    }

    // Number of content blobs currently held (drops as orphan GC removes unreferenced content).
    size_t size() const {
        std::scoped_lock lock(mu_);
        return store_.size();
    }

    // Total save() calls attempted, including any that were failed by fail_after_nth.
    int save_count() const {
        std::scoped_lock lock(mu_);
        return save_count_;
    }

    // Fault injection: make the Nth save() (1-based) and every save after it throw, to exercise
    // an action's error path. Pass 0 to disable.
    void fail_after_nth(int n) {
        std::scoped_lock lock(mu_);
        fail_after_ = n;
    }

private:
    mutable std::mutex mu_;
    std::map<std::string, std::vector<uint8_t>> store_;
    int save_count_ = 0;
    int fail_after_ = 0;
};

// Mock artifact client for testing actions that use the artifact registry.
class MockArtifactClient : public ArtifactClientBase {
public:
    // Pre-store an artifact for retrieval.
    void set_artifact(const std::string& name, const std::string& alias, FetchedArtifact artifact) {
        std::scoped_lock lock(mu_);
        artifacts_[name + "/" + (alias.empty() ? "active" : alias)] = std::move(artifact);
    }

    PublishResponse publish_artifact(const std::string& name, const std::vector<uint8_t>& /*data*/,
                                     const std::string& /*content_type*/,
                                     const PublishOptions* /*opts*/ = nullptr) override {
        std::scoped_lock lock(mu_);
        publish_count_++;
        return PublishResponse{.plugin_name = {},
                               .artifact_name = name,
                               .version = "1",
                               .is_active = true,
                               .sha256 = ""};
    }

    PublishResponse publish_shared_artifact(const std::string& name,
                                            const std::vector<uint8_t>& /*data*/,
                                            const std::string& /*content_type*/,
                                            const PublishOptions* /*opts*/ = nullptr) override {
        std::scoped_lock lock(mu_);
        publish_count_++;
        return PublishResponse{.plugin_name = {},
                               .artifact_name = name,
                               .version = "1",
                               .is_active = true,
                               .sha256 = ""};
    }

    PublishResponse publish_from_reader(const std::string& name, std::istream& /*reader*/,
                                        const std::string& /*content_type*/,
                                        const PublishOptions* /*opts*/ = nullptr) override {
        std::scoped_lock lock(mu_);
        publish_count_++;
        return PublishResponse{.plugin_name = {},
                               .artifact_name = name,
                               .version = "1",
                               .is_active = true,
                               .sha256 = ""};
    }

    PublishResponse publish_shared_from_reader(const std::string& name, std::istream& /*reader*/,
                                               const std::string& /*content_type*/,
                                               const PublishOptions* /*opts*/ = nullptr) override {
        std::scoped_lock lock(mu_);
        publish_count_++;
        return PublishResponse{.plugin_name = {},
                               .artifact_name = name,
                               .version = "1",
                               .is_active = true,
                               .sha256 = ""};
    }

    FetchedArtifact get_artifact(const std::string& name, const std::string& alias = "",
                                 ArtifactScope /*scope*/ = ArtifactScope::PLUGIN_FIRST) override {
        std::scoped_lock lock(mu_);
        auto key = name + "/" + (alias.empty() ? "active" : alias);
        auto it = artifacts_.find(key);
        if (it != artifacts_.end()) return it->second;
        throw std::runtime_error("artifact not found: " + name);
    }

    FetchedArtifact get_shared_artifact(const std::string& name,
                                        const std::string& alias = "") override {
        return get_artifact(name, alias);
    }

    ArtifactStream stream_artifact(const std::string& name, const std::string& alias = "",
                                   ArtifactScope /*scope*/ = ArtifactScope::PLUGIN_FIRST) override {
        auto fetched = get_artifact(name, alias);
        auto ss = std::make_unique<std::stringstream>();
        ss->write(reinterpret_cast<const char*>(fetched.data.data()),
                  static_cast<std::streamsize>(fetched.data.size()));
        ss->seekg(0);
        return ArtifactStream{.reader = std::move(ss), .manifest = fetched.manifest};
    }

    ArtifactStream stream_shared_artifact(const std::string& name,
                                          const std::string& alias = "") override {
        return stream_artifact(name, alias);
    }

    int publish_count() const {
        std::scoped_lock lock(mu_);
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
        std::scoped_lock lock(mu_);
        results_[table_name] = std::move(results);
    }

    // Pre-configure an error for a table name.
    void set_error(const std::string& table_name, const std::string& message) {
        std::scoped_lock lock(mu_);
        errors_[table_name] = message;
    }

    LookupResults lookup(const std::string& table_name,
                         const LookupOptions* /*opts*/ = nullptr) override {
        std::scoped_lock lock(mu_);
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
        std::scoped_lock lock(mu_);
        return call_count_;
    }

    // Admin operations are no-ops in the mock (they succeed without recording state).
    std::vector<LookupTable> get_lookup_tables() override { return {}; }

    LookupAdminResult create_lookup_table(const LookupTableDefinition& /*table*/) override {
        return LookupAdminResult{.success = true};
    }

    LookupAdminResult upsert_rows(
        const std::string& /*table_name*/,
        const std::vector<std::map<std::string, std::string>>& /*rows*/) override {
        return LookupAdminResult{.success = true};
    }

    LookupAdminResult remove_rows(
        const std::string& /*table_name*/,
        const std::vector<std::map<std::string, std::string>>& /*rows*/) override {
        return LookupAdminResult{.success = true};
    }

    LookupAdminResult set_backing_service_active(const std::string& /*table_name*/,
                                                 bool /*active*/) override {
        return LookupAdminResult{.success = true};
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
    ActionContent content(const std::string& name, const std::string& media_type,
                          const std::string& data) const {
        return store->create_string_content(action_context.did, name, media_type, data);
    }

    // Wire mock clients so an action run through the runners can reach them.
    void use_lookup(LookupClientBase& lookup_client) {
        action_context.lookup_client = &lookup_client;
    }
    void use_artifact(ArtifactClientBase& artifact_client) {
        action_context.artifact_client = &artifact_client;
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

// Build a transform/egress input carrying one content item saved to the test store.
inline TransformInput new_deltafile(const TestContext& tc, const std::string& data,
                                    const std::string& name = "content",
                                    const std::string& media_type = "application/octet-stream",
                                    const std::map<std::string, std::string>& metadata = {}) {
    TransformInput input;
    input.content.push_back(tc.content(name, media_type, data));
    input.metadata = metadata;
    return input;
}

// Set up a context for a timed-ingress run: apply the memo the action reads for cross-run state.
inline void new_timed_ingress_deltafile(TestContext& tc, const std::string& memo) {
    tc.action_context.memo = memo;
}

// Convert a result variant to an ActionEvent for inspection.
inline ActionEvent result_event(const TransformResultType& result, ActionContext& actx) {
    return result_to_event(result, actx.start_time);
}

inline ActionEvent result_event(const EgressResultType& result, ActionContext& actx) {
    return result_to_event(result, actx.start_time);
}

inline ActionEvent result_event(const IngressResultType& result, ActionContext& actx) {
    return result_to_event(result, actx.start_time);
}

// Assertion helpers — return typed event or throw on type mismatch.
inline const std::vector<TransformEvent>& require_transform(const ActionEvent& event) {
    if (event.type != "TRANSFORM") {
        throw std::runtime_error("expected TRANSFORM event, got " + event.type);
    }
    return event.transform;
}

inline const ErrorEvent& require_error(const ActionEvent& event,
                                       std::string_view expected_cause = "") {
    if (event.type != "ERROR" || !event.error.has_value()) {
        throw std::runtime_error("expected ERROR event, got " + event.type);
    }
    if (!expected_cause.empty() && event.error->cause != expected_cause) {
        throw std::runtime_error("expected error cause '" + std::string(expected_cause) +
                                 "', got '" + event.error->cause + "'");
    }
    return *event.error;
}

inline const FilterEvent& require_filter(const ActionEvent& event,
                                         std::string_view expected_message = "") {
    if (event.type != "FILTER" || !event.filter.has_value()) {
        throw std::runtime_error("expected FILTER event, got " + event.type);
    }
    if (!expected_message.empty() && event.filter->message != expected_message) {
        throw std::runtime_error("expected filter message '" + std::string(expected_message) +
                                 "', got '" + event.filter->message + "'");
    }
    return *event.filter;
}

inline void require_egress(const ActionEvent& event) {
    if (event.type != "EGRESS") {
        throw std::runtime_error("expected EGRESS event, got " + event.type);
    }
}

inline const IngressEvent& require_ingress(const ActionEvent& event) {
    if (event.type != "INGRESS" || !event.ingress.has_value()) {
        throw std::runtime_error("expected INGRESS event, got " + event.type);
    }
    return *event.ingress;
}

// Assert exactly one transform event and return it.
inline const TransformEvent& require_single_transform(const ActionEvent& event) {
    const auto& transforms = require_transform(event);
    if (transforms.size() != 1) {
        throw std::runtime_error("expected exactly one transform event, got " +
                                 std::to_string(transforms.size()));
    }
    return transforms.front();
}

// Assert a metadata key equals the expected value on a transform event.
inline void require_metadata(const TransformEvent& te, const std::string& key,
                             const std::string& value) {
    auto it = te.metadata.find(key);
    if (it == te.metadata.end()) {
        throw std::runtime_error("missing metadata key: " + key);
    }
    if (it->second != value) {
        throw std::runtime_error("metadata[" + key + "]=\"" + it->second + "\", expected \"" +
                                 value + "\"");
    }
}

// Assert an annotation key equals the expected value on a transform event.
inline void require_annotation(const TransformEvent& te, const std::string& key,
                               const std::string& value) {
    auto it = te.annotations.find(key);
    if (it == te.annotations.end()) {
        throw std::runtime_error("missing annotation key: " + key);
    }
    if (it->second != value) {
        throw std::runtime_error("annotation[" + key + "]=\"" + it->second + "\", expected \"" +
                                 value + "\"");
    }
}

// Assert a transform event carries content with the given name and return it.
inline const ActionContent& require_content(const TransformEvent& te, const std::string& name) {
    for (const auto& c : te.content) {
        if (c.name == name) return c;
    }
    throw std::runtime_error("no content named: " + name);
}

// Assert the event carries a metric with the given name and return it.
inline const Metric& require_metric(const ActionEvent& event, const std::string& name) {
    for (const auto& m : event.metrics) {
        if (m.name == name) return m;
    }
    throw std::runtime_error("no metric named: " + name);
}

// Assert some log message on the event contains the given substring and return it.
inline const LogMessage& require_message(const ActionEvent& event, std::string_view substring) {
    for (const auto& m : event.messages) {
        if (m.message.find(substring) != std::string::npos) return m;
    }
    throw std::runtime_error("no log message containing: " + std::string(substring));
}

namespace detail {
// Wrap a stack action in a non-owning shared_ptr; the executor is used synchronously within the
// runner, so the referenced action outlives it.
template<typename Action>
std::shared_ptr<Action> borrow(Action& action) {
    return std::shared_ptr<Action>(&action, [](Action*) {});
}

// A QueueEvent carrying one message's content + metadata, with typed params serialized so the
// executor deserializes them back on the real dispatch path.
template<typename Action>
QueueEvent single_message_event(const std::vector<ActionContent>& content,
                                const std::map<std::string, std::string>& metadata) {
    QueueEvent event;
    event.delta_file_messages.push_back(
        DeltaFileMessage{.metadata = metadata, .content_list = content});
    if constexpr (HasParamsType<Action>) {
        event.action_params = nlohmann::json(typename Action::Params{});
    }
    return event;
}
}  // namespace detail

// Run a transform action through the production dispatch path (input building, param
// deserialization, orphan-content GC, and the fault barrier) and return the ActionEvent.
template<typename Action>
ActionEvent run_transform(Action& action, ActionContext& actx, TransformInput& input) {
    auto event = detail::single_message_event<Action>(input.content, input.metadata);
    auto executor = make_transform_executor(detail::borrow(action));
    return dispatch_with_barrier(executor, actx, event);
}

// Run a transform action with explicit parameters (serialized into the event, then deserialized
// by the executor — exercising the real param-binding path) and return the ActionEvent.
template<typename Action, typename Params>
ActionEvent run_transform(Action& action, ActionContext& actx, Params& params,
                          TransformInput& input) {
    QueueEvent event;
    event.delta_file_messages.push_back(
        DeltaFileMessage{.metadata = input.metadata, .content_list = input.content});
    event.action_params = nlohmann::json(params);
    auto executor = make_transform_executor(detail::borrow(action));
    return dispatch_with_barrier(executor, actx, event);
}

// Run a join action's merge -> transform through the production dispatch path (actx.join must be
// set) and return the single ActionEvent.
template<typename Action>
ActionEvent run_join(Action& action, ActionContext& actx, std::vector<TransformInput>& inputs) {
    QueueEvent event;
    for (const auto& input : inputs) {
        event.delta_file_messages.push_back(
            DeltaFileMessage{.metadata = input.metadata, .content_list = input.content});
    }
    if constexpr (HasParamsType<Action>) {
        event.action_params = nlohmann::json(typename Action::Params{});
    }
    auto executor = make_transform_executor(detail::borrow(action));
    return dispatch_with_barrier(executor, actx, event);
}

// Run an egress action through the production dispatch path and return the ActionEvent.
template<typename Action>
ActionEvent run_egress(Action& action, ActionContext& actx, EgressInput& input) {
    auto event = detail::single_message_event<Action>(input.content, input.metadata);
    auto executor = make_egress_executor(detail::borrow(action));
    return dispatch_with_barrier(executor, actx, event);
}

// Run a timed ingress action through the production dispatch path and return the ActionEvent.
template<typename Action>
ActionEvent run_ingress(Action& action, ActionContext& actx) {
    QueueEvent event;
    if constexpr (HasParamsType<Action>) {
        event.action_params = nlohmann::json(typename Action::Params{});
    }
    auto executor = make_ingress_executor(detail::borrow(action));
    return dispatch_with_barrier(executor, actx, event);
}

}  // namespace deltafi::testing
