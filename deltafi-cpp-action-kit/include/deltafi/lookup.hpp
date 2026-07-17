// ABOUTME: Lookup table client for querying tables via core's GraphQL API.
// ABOUTME: Lookup table supplier interface and runner for providing table data to core on demand.

#pragma once

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <future>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <stop_token>
#include <string>
#include <thread>
#include <vector>

#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>

#include "httplib.h"

#include "deltafi/internal/backoff.hpp"
#include "deltafi/internal/crypto.hpp"
#include "deltafi/internal/tls.hpp"
#include "deltafi/queue.hpp"
#include "deltafi/types.hpp"

namespace deltafi {

// Sort direction for lookup queries.
enum class SortDirection : std::uint8_t { ASC, DESC };

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
    // Seconds until the table's next scheduled refresh; empty when the table has no refresh
    // schedule (the result must not be cached beyond the client's default TTL).
    std::optional<int> ttl_seconds;
    std::vector<std::map<std::string, std::string>> rows;
};

// Outcome of a lookup-table admin mutation (create/upsert/remove/set-active).
struct LookupAdminResult {
    bool success = false;
    std::vector<std::string> info;
    std::vector<std::string> errors;
};

inline void from_json(const nlohmann::json& j, LookupAdminResult& r) {
    r.success = j.value("success", false);
    if (j.contains("info") && !j["info"].is_null()) {
        r.info = j["info"].get<std::vector<std::string>>();
    }
    if (j.contains("errors") && !j["errors"].is_null()) {
        r.errors = j["errors"].get<std::vector<std::string>>();
    }
}

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
    j = nlohmann::json{{"name", d.name},
                       {"columns", d.columns},
                       {"keyColumns", d.key_columns},
                       {"serviceBacked", d.service_backed},
                       {"backingServiceActive", d.backing_service_active},
                       {"pullThrough", d.pull_through}};
    if (!d.refresh_duration.empty()) {
        j["refreshDuration"] = d.refresh_duration;
    }
    if (!d.variables.empty()) {
        j["variables"] = d.variables;
    }
}

// A lookup table as returned by get_lookup_tables.
struct LookupTable {
    std::string name;
    std::vector<std::string> columns;
    std::vector<std::string> key_columns;
    bool service_backed = false;
    bool backing_service_active = false;
    bool pull_through = false;
    std::string refresh_duration;
    std::string last_refresh;
    std::optional<int> total_rows;
};

inline void from_json(const nlohmann::json& j, LookupTable& t) {
    t.name = j.value("name", "");
    t.columns = j.value("columns", std::vector<std::string>{});
    t.key_columns = j.value("keyColumns", std::vector<std::string>{});
    t.service_backed = j.value("serviceBacked", false);
    t.backing_service_active = j.value("backingServiceActive", false);
    t.pull_through = j.value("pullThrough", false);
    t.refresh_duration = j.value("refreshDuration", "");
    t.last_refresh = j.value("lastRefresh", "");
    if (j.contains("totalRows") && !j["totalRows"].is_null()) {
        t.total_rows = j["totalRows"].get<int>();
    }
}

// Abstract interface for lookup table queries from actions.
class LookupClientBase {
public:
    virtual ~LookupClientBase() = default;
    LookupClientBase() = default;
    LookupClientBase(const LookupClientBase&) = delete;
    LookupClientBase& operator=(const LookupClientBase&) = delete;
    LookupClientBase(LookupClientBase&&) = delete;
    LookupClientBase& operator=(LookupClientBase&&) = delete;
    virtual LookupResults lookup(const std::string& table_name,
                                 const LookupOptions* opts = nullptr) = 0;

    // Admin operations (create/populate/manage lookup tables).
    virtual std::vector<LookupTable> get_lookup_tables() = 0;
    virtual LookupAdminResult create_lookup_table(const LookupTableDefinition& table) = 0;
    virtual LookupAdminResult upsert_rows(
        const std::string& table_name,
        const std::vector<std::map<std::string, std::string>>& rows) = 0;
    virtual LookupAdminResult remove_rows(
        const std::string& table_name,
        const std::vector<std::map<std::string, std::string>>& rows) = 0;
    virtual LookupAdminResult set_backing_service_active(const std::string& table_name,
                                                         bool active) = 0;
};

// Abstract interface for lookup table data suppliers.
class LookupTableSupplierBase {
public:
    virtual ~LookupTableSupplierBase() = default;
    LookupTableSupplierBase() = default;
    LookupTableSupplierBase(const LookupTableSupplierBase&) = delete;
    LookupTableSupplierBase& operator=(const LookupTableSupplierBase&) = delete;
    LookupTableSupplierBase(LookupTableSupplierBase&&) = delete;
    LookupTableSupplierBase& operator=(LookupTableSupplierBase&&) = delete;
    virtual LookupTableDefinition table() const = 0;
    virtual std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& variables,
        const std::map<std::string, std::vector<std::string>>& matching_column_values,
        const std::vector<std::string>& result_columns) = 0;
};

// GraphQL query matching the Go implementation.
inline constexpr const char* lookup_query =
    R"(query($lookupTableName: String!, $matchingColumnValues: [MatchingColumnValueInput!], $resultColumns: [String!], $sortColumn: String, $sortDirection: SortDirection, $offset: Int, $limit: Int) {
  lookup(lookupTableName: $lookupTableName, matchingColumnValues: $matchingColumnValues, resultColumns: $resultColumns, sortColumn: $sortColumn, sortDirection: $sortDirection, offset: $offset, limit: $limit) {
    totalCount
    created
    modified
    ttlSeconds
    rows {
      column
      value
    }
  }
})";

// Admin GraphQL operations (must match core's lookup-table schema exactly).
inline constexpr const char* get_lookup_tables_query =
    R"(query { getLookupTables { name columns keyColumns serviceBacked backingServiceActive pullThrough refreshDuration lastRefresh totalRows } })";
inline constexpr const char* create_lookup_table_mutation =
    R"(mutation($lookupTableInput: LookupTableInput!) { createLookupTable(lookupTableInput: $lookupTableInput) { success info errors } })";
inline constexpr const char* upsert_lookup_table_rows_mutation =
    R"(mutation($lookupTableName: String!, $rows: [[ColumnValueInput!]!]!) { upsertLookupTableRows(lookupTableName: $lookupTableName, rows: $rows) { success info errors } })";
inline constexpr const char* remove_lookup_table_rows_mutation =
    R"(mutation($lookupTableName: String!, $rows: [[ColumnValueInput!]!]!) { removeLookupTableRows(lookupTableName: $lookupTableName, rows: $rows) { success info errors } })";
inline constexpr const char* set_backing_service_active_mutation =
    R"(mutation($lookupTableName: String!, $active: Boolean!) { setLookupTableBackingServiceActive(lookupTableName: $lookupTableName, active: $active) { success info errors } })";

// HTTP-based lookup client that queries core's GraphQL API with caching.
class LookupClient : public LookupClientBase {
public:
    explicit LookupClient(const std::string& core_url, std::chrono::milliseconds evict_interval =
                                                           std::chrono::milliseconds(60000))
        : core_url_(core_url),
          cache_ttl_secs_(parse_ttl_env("TABLE_LOOKUP_CACHE_TTL", 30)),
          failure_cache_ttl_secs_(parse_ttl_env("TABLE_LOOKUP_FAILURE_CACHE_TTL", 1)),
          evict_interval_(evict_interval) {
        client_ = internal::make_httplib_client(core_url);
        client_->set_connection_timeout(std::chrono::seconds(10));
        client_->set_read_timeout(std::chrono::seconds(30));
        evictor_ = std::jthread([this](const std::stop_token& stop) { run_evictor(stop); });
    }

    LookupResults lookup(const std::string& table_name,
                         const LookupOptions* opts = nullptr) override {
        auto key = cache_key(table_name, opts);

        std::shared_future<LookupResults> follow;
        std::shared_ptr<std::promise<LookupResults>> lead;
        {
            std::scoped_lock lock(mu_);
            auto it = cache_.find(key);
            if (it != cache_.end() && std::chrono::steady_clock::now() < it->second.expires_at) {
                if (it->second.has_error) {
                    throw std::runtime_error(it->second.error_msg);
                }
                return it->second.results;
            }
            // Coalesce concurrent misses for this key: the first caller leads the query, the
            // rest follow and await its result (a single backend call serves them all).
            auto flight = in_flight_.find(key);
            if (flight != in_flight_.end()) {
                follow = flight->second;
            } else {
                lead = std::make_shared<std::promise<LookupResults>>();
                in_flight_[key] = lead->get_future().share();
            }
        }

        if (!lead) {
            return follow.get();  // await the leader; rethrows the leader's error
        }

        try {
            auto results = fetch_lookup(table_name, opts);
            {
                std::scoped_lock lock(mu_);
                cache_[key] = CacheEntry{.results = results,
                                         .expires_at = std::chrono::steady_clock::now() +
                                                       std::chrono::seconds(cache_ttl_secs_)};
                in_flight_.erase(key);
            }
            lead->set_value(results);
            return results;
        } catch (const std::exception& ex) {
            {
                std::scoped_lock lock(mu_);
                cache_[key] =
                    CacheEntry{.error_msg = ex.what(),
                               .has_error = true,
                               .expires_at = std::chrono::steady_clock::now() +
                                             std::chrono::seconds(failure_cache_ttl_secs_)};
                in_flight_.erase(key);
            }
            lead->set_exception(std::current_exception());
            throw;
        }
    }

    // Upload table data to core via REST.
    void upload_table(const std::string& table_name, const std::string& content_type,
                      const std::string& data) {
        auto path = "/api/v2/lookup/" + table_name;
        httplib::Headers headers = {{"X-User-Permissions", "Admin"}, {"X-User-Name", "cpp-plugin"}};
        auto res = client_->Post(path, headers, data, content_type);
        if (!res) {
            throw std::runtime_error("upload request failed: connection error");
        }
        if (res->status < 200 || res->status >= 300) {
            throw std::runtime_error("upload returned HTTP " + std::to_string(res->status) + ": " +
                                     res->body);
        }
    }

    std::vector<LookupTable> get_lookup_tables() override {
        auto data = post_graphql(get_lookup_tables_query, nlohmann::json::object());
        return data["getLookupTables"].get<std::vector<LookupTable>>();
    }

    LookupAdminResult create_lookup_table(const LookupTableDefinition& table) override {
        // LookupTableInput mirrors LookupTableDefinition minus the registration-only `variables`.
        nlohmann::json input = {{"name", table.name},
                                {"columns", table.columns},
                                {"keyColumns", table.key_columns},
                                {"serviceBacked", table.service_backed},
                                {"backingServiceActive", table.backing_service_active},
                                {"pullThrough", table.pull_through}};
        if (!table.refresh_duration.empty()) {
            input["refreshDuration"] = table.refresh_duration;
        }
        auto data = post_graphql(create_lookup_table_mutation, {{"lookupTableInput", input}});
        return data["createLookupTable"].get<LookupAdminResult>();
    }

    LookupAdminResult upsert_rows(
        const std::string& table_name,
        const std::vector<std::map<std::string, std::string>>& rows) override {
        return mutate_rows(upsert_lookup_table_rows_mutation, "upsertLookupTableRows", table_name,
                           rows);
    }

    LookupAdminResult remove_rows(
        const std::string& table_name,
        const std::vector<std::map<std::string, std::string>>& rows) override {
        return mutate_rows(remove_lookup_table_rows_mutation, "removeLookupTableRows", table_name,
                           rows);
    }

    LookupAdminResult set_backing_service_active(const std::string& table_name,
                                                 bool active) override {
        auto data = post_graphql(set_backing_service_active_mutation,
                                 {{"lookupTableName", table_name}, {"active", active}});
        return data["setLookupTableBackingServiceActive"].get<LookupAdminResult>();
    }

    void set_cache_ttl(int seconds) {
        std::scoped_lock lock(mu_);
        cache_ttl_secs_ = seconds;
    }

    void clear_cache() {
        std::scoped_lock lock(mu_);
        cache_.clear();
    }

    // Drop every cache entry whose TTL has elapsed. Called on a timer by the background evictor
    // (so keys that are never looked up again do not leak) and available for direct use in tests.
    void evict_expired() {
        auto now = std::chrono::steady_clock::now();
        std::scoped_lock lock(mu_);
        for (auto it = cache_.begin(); it != cache_.end();) {
            if (now >= it->second.expires_at) {
                it = cache_.erase(it);
            } else {
                ++it;
            }
        }
    }

    size_t cache_size() const {
        std::scoped_lock lock(mu_);
        return cache_.size();
    }

private:
    void run_evictor(const std::stop_token& stop) {
        while (internal::backoff_sleep(stop, evict_interval_)) {
            evict_expired();
        }
    }

    // POST a GraphQL query/mutation and return its `data` object, throwing on connection,
    // HTTP, or GraphQL-level errors.
    nlohmann::json post_graphql(const std::string& query, const nlohmann::json& variables) {
        nlohmann::json req_body = {{"query", query}, {"variables", variables}};
        httplib::Headers headers = {{"X-User-Permissions", "Admin"}, {"X-User-Name", "cpp-plugin"}};

        auto res = client_->Post("/api/v2/graphql", headers, req_body.dump(), "application/json");
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
        return resp["data"];
    }

    // Upsert/remove rows in batches (core caps each mutation), aggregating the per-batch results.
    LookupAdminResult mutate_rows(const std::string& mutation, const std::string& field,
                                  const std::string& table_name,
                                  const std::vector<std::map<std::string, std::string>>& rows) {
        LookupAdminResult aggregate;
        aggregate.success = true;
        for (size_t start = 0; start < rows.size(); start += batch_row_size) {
            size_t end = std::min(start + batch_row_size, rows.size());
            nlohmann::json rows_json = nlohmann::json::array();
            for (size_t i = start; i < end; ++i) {
                nlohmann::json row = nlohmann::json::array();
                for (const auto& [column, value] : rows[i]) {
                    row.push_back({{"column", column}, {"value", value}});
                }
                rows_json.push_back(std::move(row));
            }
            auto data = post_graphql(
                mutation, {{"lookupTableName", table_name}, {"rows", std::move(rows_json)}});
            auto result = data[field].get<LookupAdminResult>();
            aggregate.success = aggregate.success && result.success;
            aggregate.info.insert(aggregate.info.end(), result.info.begin(), result.info.end());
            aggregate.errors.insert(aggregate.errors.end(), result.errors.begin(),
                                    result.errors.end());
        }
        return aggregate;
    }

    static constexpr size_t batch_row_size = 10000;

    std::string core_url_;
    std::unique_ptr<httplib::Client> client_;
    int cache_ttl_secs_ = 30;
    int failure_cache_ttl_secs_ = 1;
    std::chrono::milliseconds evict_interval_;

    struct CacheEntry {
        LookupResults results;
        std::string error_msg;
        bool has_error = false;
        std::chrono::steady_clock::time_point expires_at;
    };

    mutable std::mutex mu_;
    std::map<std::string, CacheEntry> cache_;
    // Keyed by cache key: the in-progress query each concurrent miss coalesces onto.
    std::map<std::string, std::shared_future<LookupResults>> in_flight_;

    static int parse_ttl_env(const char* env_name, int default_secs) {
        auto val = env_or_default(env_name, "");
        if (val.empty()) return default_secs;
        try {
            return std::stoi(val);
        } catch (...) {  // NOLINT(bugprone-empty-catch): parse fallback to default_secs
        }
        return default_secs;
    }

    static std::string cache_key(const std::string& table_name, const LookupOptions* opts) {
        std::string input = table_name;
        if (opts) {
            nlohmann::json j;
            if (!opts->matching_column_values.empty()) j["mcv"] = opts->matching_column_values;
            if (!opts->result_columns.empty()) j["rc"] = opts->result_columns;
            if (!opts->sort_column.empty()) j["sc"] = opts->sort_column;
            if (opts->sort_direction.has_value())
                j["sd"] = sort_direction_string(*opts->sort_direction);
            if (opts->offset.has_value()) j["o"] = *opts->offset;
            if (opts->limit.has_value()) j["l"] = *opts->limit;
            input += j.dump();
        }
        return internal::sha256_hex(input);
    }

    LookupResults fetch_lookup(const std::string& table_name, const LookupOptions* opts) {
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

        return parse_results(post_graphql(lookup_query, variables)["lookup"]);
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
        if (lookup.contains("ttlSeconds") && !lookup["ttlSeconds"].is_null()) {
            results.ttl_seconds = lookup["ttlSeconds"].get<int>();
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

    // Declared last so it is destroyed first: the jthread requests stop and joins before the
    // cache it evicts is torn down.
    std::jthread evictor_;
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
    j = nlohmann::json{{"lookupTableEventId", r.lookup_table_event_id},
                       {"lookupTableName", r.lookup_table_name},
                       {"rows", r.rows}};
}

inline std::string lookup_table_event_key(const std::string& table_name) {
    return "lookup-table-event-" + table_name;
}

// Upload initial data from each supplier to core after registration.
inline void upload_supplier_data(
    LookupClient& client,
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
            spdlog::info("[lookup-supplier] uploaded {} initial rows for \"{}\"", rows.size(),
                         name);
        } catch (const std::exception& ex) {
            spdlog::error("[lookup-supplier] failed to upload initial data for \"{}\": {}", name,
                          ex.what());
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
        runner_ = std::jthread([this](const std::stop_token& stop) { run(stop); });
    }

    void request_stop() { runner_.request_stop(); }

    void join() {
        if (runner_.joinable()) runner_.join();
    }

private:
    ActionEventQueue& queue_;
    std::map<std::string, std::shared_ptr<LookupTableSupplierBase>> suppliers_;
    std::vector<std::string> queue_names_;
    std::unique_ptr<internal::RespConnection> conn_;
    std::jthread runner_;

    void run(const std::stop_token& stop) {
        internal::PollBackoff backoff;
        while (!stop.stop_requested()) {
            try {
                auto raw = queue_.take_from_any(*conn_, queue_names_);
                backoff.reset();  // healthy read (event or idle timeout) resets the backoff
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
                    rows = it->second->get_rows(event.variables, event.matching_column_values,
                                                event.result_columns);
                } catch (const std::exception& ex) {
                    spdlog::error("[lookup-supplier] error getting rows for \"{}\": {}",
                                  event.lookup_table_name, ex.what());
                }

                LookupTableEventResult result{.lookup_table_event_id = event.id,
                                              .lookup_table_name = event.lookup_table_name,
                                              .rows = rows};
                auto result_json = nlohmann::json(result).dump();
                queue_.put(event.id, result_json);

            } catch (const std::exception& ex) {
                if (stop.stop_requested()) break;
                spdlog::error("[lookup-supplier] poll error (retry in ~{}ms), reconnecting: {}",
                              backoff.current().count(), ex.what());
                conn_.reset();
                try {
                    conn_ = queue_.create_poller_connection();
                } catch (const std::exception& reconnect_ex) {
                    spdlog::error("[lookup-supplier] reconnect failed: {}", reconnect_ex.what());
                }
                if (!backoff.wait_after_error(stop)) break;
            }
        }
    }
};

}  // namespace deltafi
