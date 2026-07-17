// ABOUTME: ActionEvent wire format structs for communication with DeltaFi core.
// ABOUTME: Defines the JSON structures published to the result queue after action execution.

#pragma once

#include <map>
#include <optional>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "deltafi/types.hpp"
#include "deltafi/log.hpp"

namespace deltafi {

// Per-DeltaFile transform result.
struct TransformEvent {
    std::string name;
    std::string did;
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;
    std::vector<std::string> delete_metadata_keys;
    std::map<std::string, std::string> annotations;
    std::vector<LogMessage> messages;
};

inline void to_json(nlohmann::json& j, const TransformEvent& e) {
    j = nlohmann::json{{"did", e.did}, {"content", e.content}};
    if (!e.name.empty()) j["name"] = e.name;
    if (!e.metadata.empty()) j["metadata"] = e.metadata;
    if (!e.delete_metadata_keys.empty()) j["deleteMetadataKeys"] = e.delete_metadata_keys;
    if (!e.annotations.empty()) j["annotations"] = e.annotations;
    if (!e.messages.empty()) j["messages"] = e.messages;
}

// Error description.
struct ErrorEvent {
    std::string cause;
    std::string context;
    std::map<std::string, std::string> annotations;
};

inline void to_json(nlohmann::json& j, const ErrorEvent& e) {
    j = nlohmann::json{{"cause", e.cause}};
    if (!e.context.empty()) j["context"] = e.context;
    if (!e.annotations.empty()) j["annotations"] = e.annotations;
}

// Filter decision.
struct FilterEvent {
    std::string message;
    std::string context;
    std::map<std::string, std::string> annotations;
};

inline void to_json(nlohmann::json& j, const FilterEvent& e) {
    j = nlohmann::json{{"message", e.message}};
    if (!e.context.empty()) j["context"] = e.context;
    if (!e.annotations.empty()) j["annotations"] = e.annotations;
}

// Single DeltaFile created by ingress.
struct IngressEventItem {
    std::string did;
    std::string delta_file_name;
    std::vector<ActionContent> content;
    std::map<std::string, std::string> metadata;
    std::map<std::string, std::string> annotations;
};

inline void to_json(nlohmann::json& j, const IngressEventItem& e) {
    j = nlohmann::json{
        {"did", e.did}, {"deltaFileName", e.delta_file_name}, {"content", e.content}};
    if (!e.metadata.empty()) j["metadata"] = e.metadata;
    if (!e.annotations.empty()) j["annotations"] = e.annotations;
}

// Ingress result describing new DeltaFiles.
struct IngressEvent {
    std::string memo;
    bool execute_immediate = false;
    std::string status;
    std::string status_message;
    std::vector<IngressEventItem> ingress_items;
};

inline void to_json(nlohmann::json& j, const IngressEvent& e) {
    j = nlohmann::json{{"executeImmediate", e.execute_immediate}};
    if (!e.memo.empty()) j["memo"] = e.memo;
    if (!e.status.empty()) j["status"] = e.status;
    if (!e.status_message.empty()) j["statusMessage"] = e.status_message;
    if (!e.ingress_items.empty()) j["ingressItems"] = e.ingress_items;
}

// Named measurement included in action results.
struct Metric {
    std::string name;
    double value = 0;
    std::map<std::string, std::string> tags;
};

inline void to_json(nlohmann::json& j, const Metric& m) {
    j = nlohmann::json{{"name", m.name}, {"value", m.value}};
    if (!m.tags.empty()) j["tags"] = m.tags;
}

// Top-level result payload published to Redis after action execution.
struct ActionEvent {
    std::string did;
    std::string flow_name;
    std::string flow_id;
    std::string action_name;
    std::string start;
    std::string stop;
    std::string type;
    std::vector<TransformEvent> transform;
    std::optional<ErrorEvent> error;
    std::optional<FilterEvent> filter;
    std::optional<IngressEvent> ingress;
    std::vector<Metric> metrics;
    std::vector<LogMessage> messages;
};

inline void to_json(nlohmann::json& j, const ActionEvent& e) {
    j = nlohmann::json{{"did", e.did},        {"flowName", e.flow_name},
                       {"flowId", e.flow_id}, {"actionName", e.action_name},
                       {"start", e.start},    {"stop", e.stop},
                       {"type", e.type}};
    if (!e.transform.empty()) j["transform"] = e.transform;
    if (e.error) j["error"] = *e.error;
    if (e.filter) j["filter"] = *e.filter;
    if (e.ingress) j["ingress"] = *e.ingress;
    if (!e.metrics.empty()) j["metrics"] = e.metrics;
    if (!e.messages.empty()) j["messages"] = e.messages;
}

// Create an ActionEvent with common fields filled in.
inline ActionEvent new_base_event(const ActionContext& actx,
                                  const std::chrono::system_clock::time_point& start,
                                  const std::string& event_type,
                                  const std::vector<LogMessage>& messages,
                                  const std::vector<Metric>& metrics = {}) {
    ActionEvent event;
    event.did = actx.did;
    event.flow_name = actx.flow_name;
    event.flow_id = actx.flow_id;
    event.action_name = actx.action_name;
    event.start = format_time(start);
    event.stop = format_time(std::chrono::system_clock::now());
    event.type = event_type;
    event.messages = messages;
    event.metrics = metrics;
    return event;
}

}  // namespace deltafi
