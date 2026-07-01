// ABOUTME: Lookup table client for querying tables via core's GraphQL API.
// ABOUTME: Lookup table supplier interface and runner for providing table data to core on demand.

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

#include "deltafi/internal/crypto.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/types.hpp"

namespace deltafi {

// Sort direction for lookup queries.
enum class SortDirection { ASC, DESC };

inline std::string sort_direction_string(SortDirection d) {
    return (d == SortDirection::ASC) ? "ASC" : "DESC";
}

// Options for a lookup table query.
struct LookupOptions {
    std::map<std::string, std::vector<std::string>> matching_column_values;
    std::vector<std::string> result_columns;
    std::string sort_column;
    std::optional<SortDirection> sort_direction;
    std::optional<int> offset;
    std::optional<int> limit;
};

// Results from a lookup table query.
struct LookupResults {
    int total_count = 0;
    std::optional<std::string> created;
    std::optional<std::string> modified;
    std::vector<std::map<std::string, std::string>> rows;
};

// Describes a lookup table for plugin registration.
struct LookupTableDefinition {
    std::string name;
    std::vector<std::string> columns;
    std::vector<std::string> key_columns;
    bool service_backed = false;
    bool backing_service_active = false;
    bool pull_through = false;
    std::string refresh_duration;
    std::vector<Variable> variables;
};

inline void to_json(nlohmann::json& j, const LookupTableDefinition& d) {
    j = nlohmann::json{
        {"name", d.name},
        {"columns", d.columns},
        {"keyColumns", d.key_columns},
        {"serviceBacked", d.service_backed},
        {"backingServiceActive", d.backing_service_active},
        {"pullThrough", d.pull_through}
    };
    if (!d.refresh_duration.empty()) {
        j["refreshDuration"] = d.refresh_duration;
    }
    if (!d.variables.empty()) {
        j["variables"] = d.variables;
    }
}

// Abstract interface for lookup table queries from actions.
class LookupClientBase {
public:
    virtual ~LookupClientBase() = default;
    virtual LookupResults lookup(const std::string& table_name,
                                  const LookupOptions* opts = nullptr) = 0;
};

// Abstract interface for lookup table data suppliers.
class LookupTableSupplierBase {
public:
    virtual ~LookupTableSupplierBase() = default;
    virtual LookupTableDefinition table() const = 0;
    virtual std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& variables,
        const std::map<std::string, std::vector<std::string>>& matching_column_values,
        const std::vector<std::string>& result_columns) = 0;
};

// GraphQL query matching the Go implementation.
inline constexpr const char* lookup_query = R"(query($lookupTableName: String!, $matchingColumnValues: [MatchingColumnValueInput!], $resultColumns: [String!], $sortColumn: String, $sortDirection: SortDirection, $offset: Int, $limit: Int) {
  lookup(lookupTableName: $lookupTableName, matchingColumnValues: $matchingColumnValues, resultColumns: $resultColumns, sortColumn: $sortColumn, sortDirection: $sortDirection, offset: $offset, limit: $limit) {
    totalCount
    created
    modified
    rows {
      column
      value
    }
  }
})";

// HTTP-based lookup client that queries core's GraphQL API with caching.
class LookupClient : public LookupClientBase {
public:
    explicit LookupClient(const std::string& core_url)
        : core_url_(core_url) {
        cache_ttl_secs_ = parse_ttl_env("TABLE_LOOKUP_CACHE_TTL", 30);
        failure_cache_ttl_secs_ = parse_ttl_env("TABLE_LOOKUP_FAILURE_CACHE_TTL", 1);

        // Parse host from core URL for httplib
        std::string host_port = core_url;
        if (host_port.starts_with("http://")) {
            host_port = host_port.substr(7);
        } else if (host_port.starts_with("https://")) {
            host_port = host_port.substr(8);
        }
        client_ = std::make_unique<httplib::Client>(core_url);
        client_->set_connection_timeout(std::chrono::seconds(10));
        client_->set_read_timeout(std::chrono::seconds(30));
    }

    LookupResults lookup(const std::string& table_name,
                          const LookupOptions* opts = nullptr) override {
        auto key = cache_key(table_name, opts);

        // Check cache
        {
            std::lock_guard lock(mu_);
            auto it = cache_.find(key);
            if (it != cache_.end()) {
                auto now = std::chrono::steady_clock::now();
                if (now < it->second.expires_at) {
                    if (it->second.has_error) {
                        throw std::runtime_error(it->second.error_msg);
                    }
                    return it->second.results;
                }
            }
        }

        // Execute query
        try {
            auto results = fetch_lookup(table_name, opts);
            std::lock_guard lock(mu_);
            cache_[key] = CacheEntry{results, {}, false,
                std::chrono::steady_clock::now() + std::chrono::seconds(cache_ttl_secs_)};
            return results;
        } catch (const std::exception& ex) {
            std::lock_guard lock(mu_);
            cache_[key] = CacheEntry{{}, ex.what(), true,
                std::chrono::steady_clock::now() + std::chrono::seconds(failure_cache_ttl_secs_)};
            throw;
        }
    }

    // Upload table data to core via REST.
    void upload_table(const std::string& table_name,
                      const std::string& content_type,
                      const std::string& data) {
        auto path = "/api/v2/lookup/" + table_name;
        httplib::Headers headers = {
            {"X-User-Permissions", "Admin"},
            {"X-User-Name", "cpp-plugin"}
        };
        auto res = client_->Post(path, headers, data, content_type);
        if (!res) {
            throw std::runtime_error("upload request failed: connection error");
        }
        if (res->status < 200 || res->status >= 300) {
            throw std::runtime_error("upload returned HTTP " +
                std::to_string(res->status) + ": " + res->body);
        }
    }

    void set_cache_ttl(int seconds) {
        std::lock_guard lock(mu_);
        cache_ttl_secs_ = seconds;
    }

    void clear_cache() {
        std::lock_guard lock(mu_);
        cache_.clear();
    }

private:
    std::string core_url_;
    std::unique_ptr<httplib::Client> client_;
    int cache_ttl_secs_ = 30;
    int failure_cache_ttl_secs_ = 1;

    struct CacheEntry {
        LookupResults results;
        std::string error_msg;
        bool has_error = false;
        std::chrono::steady_clock::time_point expires_at;
    };

    std::mutex mu_;
    std::map<std::string, CacheEntry> cache_;

    static int parse_ttl_env(const char* env_name, int default_secs) {
        auto val = env_or_default(env_name, "");
        if (val.empty()) return default_secs;
        try { return std::stoi(val); } catch (...) {}
        return default_secs;
    }

    static std::string cache_key(const std::string& table_name,
                                  const LookupOptions* opts) {
        std::string input = table_name;
        if (opts) {
            nlohmann::json j;
            if (!opts->matching_column_values.empty())
                j["mcv"] = opts->matching_column_values;
            if (!opts->result_columns.empty())
                j["rc"] = opts->result_columns;
            if (!opts->sort_column.empty())
                j["sc"] = opts->sort_column;
            if (opts->sort_direction.has_value())
                j["sd"] = sort_direction_string(*opts->sort_direction);
            if (opts->offset.has_value())
                j["o"] = *opts->offset;
            if (opts->limit.has_value())
                j["l"] = *opts->limit;
            input += j.dump();
        }
        return internal::sha256_hex(input);
    }

    LookupResults fetch_lookup(const std::string& table_name,
                                const LookupOptions* opts) {
        nlohmann::json variables;
        variables["lookupTableName"] = table_name;

        if (opts) {
            if (!opts->matching_column_values.empty()) {
                nlohmann::json mcv = nlohmann::json::array();
                for (const auto& [col, vals] : opts->matching_column_values) {
                    mcv.push_back({{"column", col}, {"value", vals}});
                }
                variables["matchingColumnValues"] = mcv;
            }
            if (!opts->result_columns.empty()) {
                variables["resultColumns"] = opts->result_columns;
            }
            if (!opts->sort_column.empty()) {
                variables["sortColumn"] = opts->sort_column;
            }
            if (opts->sort_direction.has_value()) {
                variables["sortDirection"] = sort_direction_string(*opts->sort_direction);
            }
            if (opts->offset.has_value()) {
                variables["offset"] = *opts->offset;
            }
            if (opts->limit.has_value()) {
                variables["limit"] = *opts->limit;
            }
        }

        nlohmann::json req_body = {
            {"query", lookup_query},
            {"variables", variables}
        };

        httplib::Headers headers = {
            {"X-User-Permissions", "Admin"},
            {"X-User-Name", "cpp-plugin"}
        };

        auto res = client_->Post("/api/v2/graphql", headers,
                                  req_body.dump(), "application/json");
        if (!res) {
            throw std::runtime_error("graphql request failed: connection error");
        }
        if (res->status != 200) {
            throw std::runtime_error("graphql request returned HTTP " +
                std::to_string(res->status) + ": " + res->body);
        }

        auto resp = nlohmann::json::parse(res->body);

        if (resp.contains("errors") && !resp["errors"].empty()) {
            throw std::runtime_error("graphql error: " +
                resp["errors"][0]["message"].get<std::string>());
        }

        return parse_results(resp["data"]["lookup"]);
    }

    static LookupResults parse_results(const nlohmann::json& lookup) {
        LookupResults results;
        results.total_count = lookup.value("totalCount", 0);

        if (lookup.contains("created") && !lookup["created"].is_null()) {
            results.created = lookup["created"].get<std::string>();
        }
        if (lookup.contains("modified") && !lookup["modified"].is_null()) {
            results.modified = lookup["modified"].get<std::string>();
        }

        if (lookup.contains("rows")) {
            for (const auto& row : lookup["rows"]) {
                std::map<std::string, std::string> m;
                for (const auto& cv : row) {
                    m[cv["column"].get<std::string>()] = cv["value"].get<std::string>();
                }
                results.rows.push_back(std::move(m));
            }
        }

        return results;
    }
};

// Event from core requesting supplier data.
struct LookupTableEvent {
    std::string id;
    std::string lookup_table_name;
    std::map<std::string, std::vector<std::string>> matching_column_values;
    std::vector<std::string> result_columns;
    std::map<std::string, std::string> variables;
};

inline void from_json(const nlohmann::json& j, LookupTableEvent& e) {
    j.at("id").get_to(e.id);
    j.at("lookupTableName").get_to(e.lookup_table_name);
    if (j.contains("matchingColumnValues") && !j["matchingColumnValues"].is_null()) {
        j.at("matchingColumnValues").get_to(e.matching_column_values);
    }
    if (j.contains("resultColumns") && !j["resultColumns"].is_null()) {
        j.at("resultColumns").get_to(e.result_columns);
    }
    if (j.contains("variables") && !j["variables"].is_null()) {
        j.at("variables").get_to(e.variables);
    }
}

// Response sent back to core with supplier data.
struct LookupTableEventResult {
    std::string lookup_table_event_id;
    std::string lookup_table_name;
    std::vector<std::map<std::string, std::string>> rows;
};

inline void to_json(nlohmann::json& j, const LookupTableEventResult& r) {
    j = nlohmann::json{
        {"lookupTableEventId", r.lookup_table_event_id},
        {"lookupTableName", r.lookup_table_name},
        {"rows", r.rows}
    };
}

inline std::string lookup_table_event_key(const std::string& table_name) {
    return "lookup-table-event-" + table_name;
}

// Upload initial data from each supplier to core after registration.
inline void upload_supplier_data(LookupClient& client,
    const std::map<std::string, std::shared_ptr<LookupTableSupplierBase>>& suppliers) {
    for (const auto& [name, supplier] : suppliers) {
        try {
            auto rows = supplier->get_rows({}, {}, {});
            if (rows.empty()) {
                spdlog::info("[lookup-supplier] no initial rows for \"{}\", skipping upload", name);
                continue;
            }
            auto data = nlohmann::json(rows).dump();
            client.upload_table(name, "application/json", data);
            spdlog::info("[lookup-supplier] uploaded {} initial rows for \"{}\"",
                         rows.size(), name);
        } catch (const std::exception& ex) {
            spdlog::error("[lookup-supplier] failed to upload initial data for \"{}\": {}",
                          name, ex.what());
        }
    }
}

// Supplier runner thread that listens for lookup table events and dispatches to suppliers.
class LookupTableSupplierRunner {
public:
    LookupTableSupplierRunner(
        ActionEventQueue& queue,
        std::map<std::string, std::shared_ptr<LookupTableSupplierBase>> suppliers)
        : queue_(queue), suppliers_(std::move(suppliers)) {}

    void start() {
        if (suppliers_.empty()) return;

        for (const auto& [name, _] : suppliers_) {
            queue_names_.push_back(lookup_table_event_key(name));
        }

        conn_ = queue_.create_poller_connection();
        runner_ = std::jthread([this](std::stop_token stop) {
            run(stop);
        });
    }

    void request_stop() {
        runner_.request_stop();
    }

    void join() {
        if (runner_.joinable()) runner_.join();
    }

private:
    ActionEventQueue& queue_;
    std::map<std::string, std::shared_ptr<LookupTableSupplierBase>> suppliers_;
    std::vector<std::string> queue_names_;
    std::unique_ptr<internal::RespConnection> conn_;
    std::jthread runner_;

    void run(std::stop_token stop) {
        while (!stop.stop_requested()) {
            try {
                auto raw = queue_.take_from_any(*conn_, queue_names_);
                if (raw.empty()) continue;

                auto event = nlohmann::json::parse(raw).get<LookupTableEvent>();

                auto it = suppliers_.find(event.lookup_table_name);
                if (it == suppliers_.end()) {
                    spdlog::warn("[lookup-supplier] no supplier for table \"{}\"",
                                 event.lookup_table_name);
                    continue;
                }

                std::vector<std::map<std::string, std::string>> rows;
                try {
                    rows = it->second->get_rows(
                        event.variables,
                        event.matching_column_values,
                        event.result_columns);
                } catch (const std::exception& ex) {
                    spdlog::error("[lookup-supplier] error getting rows for \"{}\": {}",
                                 event.lookup_table_name, ex.what());
                }

                LookupTableEventResult result{event.id, event.lookup_table_name, rows};
                auto result_json = nlohmann::json(result).dump();
                queue_.put(event.id, result_json);

            } catch (const std::exception& ex) {
                if (stop.stop_requested()) break;
                spdlog::error("[lookup-supplier] poll error, reconnecting: {}",
                              ex.what());
                conn_.reset();
                try {
                    conn_ = queue_.create_poller_connection();
                } catch (const std::exception& reconnect_ex) {
                    if (stop.stop_requested()) break;
                    spdlog::error("[lookup-supplier] reconnect failed: {}",
                                  reconnect_ex.what());
                    std::this_thread::sleep_for(std::chrono::seconds(1));
                }
            }
        }
    }
};

} // namespace deltafi
