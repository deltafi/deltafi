// ABOUTME: Core types for the DeltaFi C++ Action Kit library.
// ABOUTME: Defines PluginCoordinates, Segment, ActionContent, ActionContext, Variable, FlowPlan.

#pragma once

#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

namespace deltafi {

// Forward declarations
class ContentServiceBase;
class LookupClientBase;
class ArtifactClientBase;

// Identifies a plugin in the DeltaFi ecosystem.
struct PluginCoordinates {
    std::string group_id;
    std::string artifact_id;
    std::string version;
};

inline void to_json(nlohmann::json& j, const PluginCoordinates& c) {
    j = nlohmann::json{
        {"groupId", c.group_id},
        {"artifactId", c.artifact_id},
        {"version", c.version}
    };
}

inline void from_json(const nlohmann::json& j, PluginCoordinates& c) {
    j.at("groupId").get_to(c.group_id);
    j.at("artifactId").get_to(c.artifact_id);
    j.at("version").get_to(c.version);
}

// Reference to a byte range stored in MinIO/S3.
struct Segment {
    std::string uuid;
    int64_t offset = 0;
    int64_t size = 0;
    std::string did;

    std::string object_path() const {
        std::string prefix = did;
        if (prefix.size() > 3) prefix = prefix.substr(0, 3);
        return prefix + "/" + did + "/" + uuid;
    }
};

inline void to_json(nlohmann::json& j, const Segment& s) {
    j = nlohmann::json{
        {"uuid", s.uuid},
        {"offset", s.offset},
        {"size", s.size},
        {"did", s.did}
    };
}

inline void from_json(const nlohmann::json& j, Segment& s) {
    j.at("uuid").get_to(s.uuid);
    j.at("offset").get_to(s.offset);
    j.at("size").get_to(s.size);
    j.at("did").get_to(s.did);
}

// Named piece of content composed of one or more segments.
struct ActionContent {
    std::string name;
    std::string media_type;
    std::vector<Segment> segments;
    std::vector<std::string> tags;

    // Transient pointer set by the framework — not serialized.
    ContentServiceBase* content_service_ = nullptr;

    // Load all content bytes from storage.
    std::vector<uint8_t> load_bytes() const;

    // Load content as a string from storage.
    std::string load_string() const;

    // Load content as a streaming reader from storage.
    std::unique_ptr<std::istream> load_reader() const;

    int64_t size() const {
        int64_t total = 0;
        for (const auto& seg : segments) total += seg.size;
        return total;
    }

    ActionContent copy() const {
        return ActionContent{name, media_type, segments, tags};
    }

    void set_name(const std::string& n) { name = n; }
    void set_media_type(const std::string& mt) { media_type = mt; }
    void add_tag(const std::string& tag) { tags.push_back(tag); }

    bool remove_tag(const std::string& tag) {
        for (auto it = tags.begin(); it != tags.end(); ++it) {
            if (*it == tag) { tags.erase(it); return true; }
        }
        return false;
    }

    bool has_tag(const std::string& tag) const {
        for (const auto& t : tags) {
            if (t == tag) return true;
        }
        return false;
    }

    // Zero-copy segment slicing for subcontent.
    ActionContent subcontent(int64_t offset, int64_t sz) const {
        return subcontent_named(offset, sz, name, media_type);
    }

    ActionContent subcontent_named(int64_t off, int64_t sz,
                                    const std::string& n,
                                    const std::string& mt) const {
        std::vector<Segment> segs;
        int64_t remaining = sz;
        int64_t pos = 0;

        for (const auto& seg : segments) {
            if (remaining <= 0) break;
            int64_t seg_end = pos + seg.size;
            if (seg_end <= off) { pos = seg_end; continue; }

            int64_t skip = (off > pos) ? (off - pos) : 0;
            int64_t available = seg.size - skip;
            int64_t take = std::min(available, remaining);

            segs.push_back(Segment{seg.uuid, seg.offset + skip, take, seg.did});
            remaining -= take;
            pos = seg_end;
        }
        return ActionContent{n, mt, std::move(segs), {}};
    }

    void append(const ActionContent& other) {
        segments.insert(segments.end(),
                        other.segments.begin(), other.segments.end());
    }

    void prepend(const ActionContent& other) {
        segments.insert(segments.begin(),
                        other.segments.begin(), other.segments.end());
    }
};

inline void to_json(nlohmann::json& j, const ActionContent& c) {
    j = nlohmann::json{
        {"name", c.name},
        {"mediaType", c.media_type},
        {"segments", c.segments}
    };
    if (!c.tags.empty()) {
        j["tags"] = c.tags;
    }
}

inline void from_json(const nlohmann::json& j, ActionContent& c) {
    j.at("name").get_to(c.name);
    j.at("mediaType").get_to(c.media_type);
    j.at("segments").get_to(c.segments);
    if (j.contains("tags")) {
        j.at("tags").get_to(c.tags);
    }
}

// Join parameters from the flow definition.
struct JoinConfiguration {
    double max_age = 0;
    int min_num = 0;
    int max_num = 0;
    std::string metadata_key;
};

inline void from_json(const nlohmann::json& j, JoinConfiguration& c) {
    j.at("maxAge").get_to(c.max_age);
    j.at("minNum").get_to(c.min_num);
    j.at("maxNum").get_to(c.max_num);
    j.at("metadataKey").get_to(c.metadata_key);
}

inline void to_json(nlohmann::json& j, const JoinConfiguration& c) {
    j = nlohmann::json{
        {"maxAge", c.max_age},
        {"minNum", c.min_num},
        {"maxNum", c.max_num},
        {"metadataKey", c.metadata_key}
    };
}

// Per-invocation execution context passed to every action.
struct ActionContext {
    std::string did;
    std::string delta_file_name;
    std::string data_source;
    std::string flow_name;
    std::string flow_id;
    std::string action_name;
    std::string action_version;
    std::string hostname;
    std::string system_name;
    std::chrono::system_clock::time_point start_time;
    std::string memo;

    ContentServiceBase* content_service = nullptr;
    LookupClientBase* lookup_client = nullptr;
    ArtifactClientBase* artifact_client = nullptr;
    std::optional<JoinConfiguration> join;
    std::vector<std::string> joined_dids;
    std::vector<Segment> saved_segments;

    // Create a child context with a new UUID for multi-child splitting.
    ActionContext child_context() const;

    void track_segment(const Segment& seg) {
        saved_segments.push_back(seg);
    }
};

// Plugin variable for flow parameterization.
struct Variable {
    std::string name;
    std::string description;
    std::string data_type;
    bool required = false;
    nlohmann::json default_value;
    nlohmann::json value;
    bool masked = false;
};

inline void to_json(nlohmann::json& j, const Variable& v) {
    j = nlohmann::json{
        {"name", v.name},
        {"description", v.description},
        {"dataType", v.data_type},
        {"required", v.required}
    };
    if (!v.default_value.is_null()) j["defaultValue"] = v.default_value;
    if (!v.value.is_null()) j["value"] = v.value;
    if (v.masked) j["masked"] = v.masked;
}

inline void from_json(const nlohmann::json& j, Variable& v) {
    j.at("name").get_to(v.name);
    if (j.contains("description")) j.at("description").get_to(v.description);
    if (j.contains("dataType")) j.at("dataType").get_to(v.data_type);
    if (j.contains("required")) j.at("required").get_to(v.required);
    if (j.contains("defaultValue")) v.default_value = j.at("defaultValue");
    if (j.contains("value")) v.value = j.at("value");
    if (j.contains("masked")) j.at("masked").get_to(v.masked);
}

// Flow definition as opaque JSON passed through to core.
// Only name and type are extracted for logging; the raw JSON is preserved.
struct FlowPlan {
    std::string name;
    std::string type;
    nlohmann::json raw;
};

inline void from_json(const nlohmann::json& j, FlowPlan& fp) {
    fp.raw = j;
    if (j.contains("name")) j.at("name").get_to(fp.name);
    if (j.contains("type")) j.at("type").get_to(fp.type);
}

inline void to_json(nlohmann::json& j, const FlowPlan& fp) {
    j = fp.raw;
}

// Thread-safe string map for caching data updated via callbacks.
class SafeStringMap {
public:
    void store(std::map<std::string, std::string> data) {
        std::lock_guard lock(mu_);
        data_ = std::move(data);
    }

    void for_each(std::function<void(const std::string&, const std::string&)> fn) const {
        std::lock_guard lock(mu_);
        for (const auto& [k, v] : data_) {
            fn(k, v);
        }
    }

private:
    mutable std::mutex mu_;
    std::map<std::string, std::string> data_;
};

// Read environment variable with fallback default.
inline std::string env_or_default(const std::string& key,
                                   const std::string& fallback) {
    const char* val = std::getenv(key.c_str());
    if (val && val[0] != '\0') return val;
    return fallback;
}

} // namespace deltafi
