// ABOUTME: Action execution engine with poller and worker threads.
// ABOUTME: Polls Redis queues for work, executes actions, publishes results, and cleans up orphans.

#pragma once

#include <chrono>
#include <functional>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>
#include <BS_thread_pool.hpp>

#include "deltafi/types.hpp"
#include "deltafi/content.hpp"
#include "deltafi/event.hpp"
#include "deltafi/heartbeat.hpp"
#include "deltafi/input.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/result.hpp"

namespace deltafi {

// Deserialized queue message content for a single DeltaFile.
struct DeltaFileMessage {
    std::map<std::string, std::string> metadata;
    std::vector<ActionContent> content_list;
};

inline void from_json(const nlohmann::json& j, DeltaFileMessage& m) {
    if (j.contains("metadata") && !j["metadata"].is_null()) {
        j.at("metadata").get_to(m.metadata);
    }
    if (j.contains("contentList")) {
        j.at("contentList").get_to(m.content_list);
    }
}

// Action context fields from the queue event.
struct QueueActionContext {
    std::string did;
    std::string delta_file_name;
    std::string data_source;
    std::string flow_name;
    std::string flow_id;
    std::string action_name;
    std::string action_version;
    std::string hostname;
    std::string system_name;
    std::string memo;
    std::optional<JoinConfiguration> join;
    std::vector<std::string> joined_dids;
};

inline void from_json(const nlohmann::json& j, QueueActionContext& c) {
    j.at("did").get_to(c.did);
    if (j.contains("deltaFileName")) j.at("deltaFileName").get_to(c.delta_file_name);
    if (j.contains("dataSource")) j.at("dataSource").get_to(c.data_source);
    j.at("flowName").get_to(c.flow_name);
    j.at("flowId").get_to(c.flow_id);
    j.at("actionName").get_to(c.action_name);
    if (j.contains("actionVersion")) j.at("actionVersion").get_to(c.action_version);
    if (j.contains("hostname")) j.at("hostname").get_to(c.hostname);
    if (j.contains("systemName")) j.at("systemName").get_to(c.system_name);
    if (j.contains("memo")) j.at("memo").get_to(c.memo);
    if (j.contains("join") && !j["join"].is_null()) {
        c.join = j.at("join").get<JoinConfiguration>();
    }
    if (j.contains("joinedDids")) j.at("joinedDids").get_to(c.joined_dids);
}

// Deserialized work item from the Redis queue.
struct QueueEvent {
    std::vector<DeltaFileMessage> delta_file_messages;
    QueueActionContext action_context;
    nlohmann::json action_params;
    std::string queue_name;
    std::string return_address;
};

inline void from_json(const nlohmann::json& j, QueueEvent& e) {
    if (j.contains("deltaFileMessages")) {
        j.at("deltaFileMessages").get_to(e.delta_file_messages);
    }
    j.at("actionContext").get_to(e.action_context);
    if (j.contains("actionParams") && !j["actionParams"].is_null()) {
        e.action_params = j.at("actionParams");
    }
    if (j.contains("queueName")) j.at("queueName").get_to(e.queue_name);
    if (j.contains("returnAddress")) j.at("returnAddress").get_to(e.return_address);
}

// Determine the result queue name from the return address.
inline std::string result_queue_name(const std::string& return_address) {
    if (return_address.empty()) return "dgs";
    return "dgs-" + return_address;
}

// Type-erased executor that runs an action and returns an ActionEvent.
using ActionExecutor = std::function<ActionEvent(ActionContext&, QueueEvent&)>;

// Build an ActionContext from the queue event.
inline ActionContext build_action_context(const QueueEvent& event,
                                           ContentServiceBase* cs,
                                           LookupClientBase* lc = nullptr,
                                           ArtifactClientBase* ac = nullptr) {
    ActionContext actx;
    actx.did = event.action_context.did;
    actx.delta_file_name = event.action_context.delta_file_name;
    actx.data_source = event.action_context.data_source;
    actx.flow_name = event.action_context.flow_name;
    actx.flow_id = event.action_context.flow_id;
    actx.action_name = event.action_context.action_name;
    actx.action_version = event.action_context.action_version;
    actx.hostname = event.action_context.hostname;
    actx.system_name = event.action_context.system_name;
    actx.start_time = std::chrono::system_clock::now();
    actx.memo = event.action_context.memo;
    actx.content_service = cs;
    actx.lookup_client = lc;
    actx.artifact_client = ac;
    actx.join = event.action_context.join;
    actx.joined_dids = event.action_context.joined_dids;
    return actx;
}

// Build a TransformInput from the first DeltaFileMessage.
inline TransformInput build_transform_input(ActionContext& actx,
                                              const QueueEvent& event) {
    TransformInput input;
    if (!event.delta_file_messages.empty()) {
        const auto& msg = event.delta_file_messages[0];
        input.content = msg.content_list;
        input.metadata = msg.metadata;
        bind_content_service(input.content, actx.content_service);
    }
    return input;
}

// Build TransformInputs from all DeltaFileMessages (for join actions).
inline std::vector<TransformInput> build_transform_inputs(
    ActionContext& actx, const QueueEvent& event) {
    std::vector<TransformInput> inputs;
    inputs.reserve(event.delta_file_messages.size());
    for (const auto& msg : event.delta_file_messages) {
        TransformInput input;
        input.content = msg.content_list;
        input.metadata = msg.metadata;
        bind_content_service(input.content, actx.content_service);
        inputs.push_back(std::move(input));
    }
    return inputs;
}

// Build an EgressInput from the first DeltaFileMessage.
inline EgressInput build_egress_input(ActionContext& actx,
                                        const QueueEvent& event) {
    EgressInput input;
    if (!event.delta_file_messages.empty()) {
        const auto& msg = event.delta_file_messages[0];
        input.content = msg.content_list;
        input.metadata = msg.metadata;
        bind_content_service(input.content, actx.content_service);
    }
    return input;
}

// Manages polling and execution for a single registered action.
class ActionWorker {
public:
    ActionWorker(const std::string& name,
                 ActionExecutor executor,
                 ActionEventQueue& queue,
                 ContentServiceBase& content_service,
                 InFlightTracker& tracker,
                 const std::string& app_name,
                 int worker_count,
                 LookupClientBase* lookup_client = nullptr,
                 ArtifactClientBase* artifact_client = nullptr)
        : name_(name), executor_(std::move(executor)),
          queue_(queue), content_service_(content_service),
          tracker_(tracker), app_name_(app_name),
          pool_(worker_count), lookup_client_(lookup_client),
          artifact_client_(artifact_client) {}

    void start() {
        poller_conn_ = queue_.create_poller_connection();
        poller_ = std::jthread([this](std::stop_token stop) {
            poll(stop);
        });
    }

    void request_stop() {
        poller_.request_stop();
    }

    void join() {
        if (poller_.joinable()) poller_.join();
        pool_.wait();
    }

private:
    std::string name_;
    ActionExecutor executor_;
    ActionEventQueue& queue_;
    ContentServiceBase& content_service_;
    InFlightTracker& tracker_;
    std::string app_name_;
    BS::thread_pool pool_;
    LookupClientBase* lookup_client_ = nullptr;
    ArtifactClientBase* artifact_client_ = nullptr;
    std::jthread poller_;
    std::unique_ptr<internal::RespConnection> poller_conn_;

    void poll(std::stop_token stop) {
        while (!stop.stop_requested()) {
            try {
                auto raw = queue_.take(*poller_conn_, name_);
                if (raw.empty()) continue;

                // Submit work to thread pool
                pool_.detach_task([this, raw = std::move(raw)] {
                    process(raw);
                });
            } catch (const std::exception& ex) {
                if (stop.stop_requested()) break;
                spdlog::error("[{}] poll error, reconnecting: {}",
                              name_, ex.what());
                poller_conn_.reset();
                try {
                    poller_conn_ = queue_.create_poller_connection();
                } catch (const std::exception& reconnect_ex) {
                    if (stop.stop_requested()) break;
                    spdlog::error("[{}] reconnect failed: {}",
                                  name_, reconnect_ex.what());
                    std::this_thread::sleep_for(std::chrono::seconds(1));
                }
            }
        }
    }

    void process(const std::string& raw) {
        QueueEvent event;
        try {
            event = nlohmann::json::parse(raw).get<QueueEvent>();
        } catch (const std::exception& ex) {
            spdlog::error("[{}] failed to deserialize event: {}",
                          name_, ex.what());
            return;
        }

        auto start_time = std::chrono::system_clock::now();
        auto actx = build_action_context(event, &content_service_, lookup_client_, artifact_client_);

        tracker_.start(actx.did, actx.action_name, app_name_);

        ActionEvent action_event;
        try {
            action_event = executor_(actx, event);
        } catch (const std::exception& ex) {
            spdlog::error("[{}] action error: {}", name_, ex.what());
            ErrorResult err(actx, ex.what());
            action_event = err.to_action_event(start_time);
        } catch (...) {
            spdlog::error("[{}] unknown action error", name_);
            ErrorResult err(actx, "unknown error");
            action_event = err.to_action_event(start_time);
        }

        tracker_.stop(actx.did, actx.action_name);

        // Publish result
        try {
            auto result_json = nlohmann::json(action_event).dump();
            auto qname = result_queue_name(event.return_address);
            queue_.put(qname, result_json);
        } catch (const std::exception& ex) {
            spdlog::error("[{}] failed to publish result: {}",
                          name_, ex.what());
        }
    }
};

} // namespace deltafi
