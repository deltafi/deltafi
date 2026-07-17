// ABOUTME: Result types returned by actions after execution.
// ABOUTME: Defines TransformResult, EgressResult, IngressResult, ErrorResult, FilterResult.

#pragma once

#include <chrono>
#include <map>
#include <ostream>
#include <stdexcept>
#include <string>
#include <variant>
#include <vector>

#include "deltafi/types.hpp"
#include "deltafi/content.hpp"
#include "deltafi/event.hpp"
#include "deltafi/log.hpp"

namespace deltafi {

// Forward declarations for friend operator<< access.
class TransformResults;
class IngressResult;

namespace detail {

inline const char* severity_label(LogSeverity s) {
    switch (s) {
        case LogSeverity::Trace:
            return "TRACE";
        case LogSeverity::Info:
            return "INFO";
        case LogSeverity::Warning:
            return "WARN";
        case LogSeverity::Error:
            return "ERROR";
    }
    return "?";
}

inline void print_indent(std::ostream& os, int depth) {
    for (int i = 0; i < depth; ++i) os << "  ";
}

inline void print_map(std::ostream& os, const std::string& label,
                      const std::map<std::string, std::string>& m, int depth) {
    if (m.empty()) return;
    print_indent(os, depth);
    os << label << ":\n";
    for (const auto& [k, v] : m) {
        print_indent(os, depth + 1);
        os << k << ": " << v << "\n";
    }
}

inline void print_string_list(std::ostream& os, const std::string& label,
                              const std::vector<std::string>& v, int depth) {
    if (v.empty()) return;
    print_indent(os, depth);
    os << label << ": [";
    for (size_t i = 0; i < v.size(); ++i) {
        if (i > 0) os << ", ";
        os << v[i];
    }
    os << "]\n";
}

inline void print_content(std::ostream& os, const std::vector<ActionContent>& content, int depth) {
    if (content.empty()) return;
    print_indent(os, depth);
    os << "content:\n";
    for (size_t i = 0; i < content.size(); ++i) {
        const auto& c = content[i];
        print_indent(os, depth + 1);
        os << "[" << i << "] \"" << c.name << "\" (" << c.media_type << ", " << c.size()
           << " bytes";
        if (!c.tags.empty()) {
            os << ", tags: [";
            for (size_t t = 0; t < c.tags.size(); ++t) {
                if (t > 0) os << ", ";
                os << c.tags[t];
            }
            os << "]";
        }
        os << ")\n";
    }
}

inline void print_messages(std::ostream& os, const std::vector<LogMessage>& msgs, int depth) {
    if (msgs.empty()) return;
    print_indent(os, depth);
    os << "messages:\n";
    for (const auto& m : msgs) {
        print_indent(os, depth + 1);
        os << "[" << severity_label(m.severity) << "] " << m.message << "\n";
    }
}

}  // namespace detail

// Mixin for logging methods shared by all result types.
class ResultBase {
public:
    explicit ResultBase(const std::string& action_name) : action_name_(action_name) {}

    void log_trace(const std::string& msg) {
        messages_.push_back(new_log_message(LogSeverity::Trace, action_name_, msg));
    }
    void log_info(const std::string& msg) {
        messages_.push_back(new_log_message(LogSeverity::Info, action_name_, msg));
    }
    void log_warning(const std::string& msg) {
        messages_.push_back(new_log_message(LogSeverity::Warning, action_name_, msg));
    }
    void log_error(const std::string& msg) {
        messages_.push_back(new_log_message(LogSeverity::Error, action_name_, msg));
    }

    LogStream log_trace() { return LogStream(messages_, LogSeverity::Trace, action_name_); }
    LogStream log_info() { return LogStream(messages_, LogSeverity::Info, action_name_); }
    LogStream log_warning() { return LogStream(messages_, LogSeverity::Warning, action_name_); }
    LogStream log_error() { return LogStream(messages_, LogSeverity::Error, action_name_); }

    void add_metric(const Metric& m) { metrics_.push_back(m); }
    const std::vector<Metric>& metrics() const { return metrics_; }

    const std::vector<LogMessage>& messages() const { return messages_; }

protected:
    std::string action_name_;
    std::vector<LogMessage> messages_;
    std::vector<Metric> metrics_;
};

// Output of a transform action (single DeltaFile).
class TransformResult : public ResultBase {
public:
    explicit TransformResult(ActionContext& actx) : ResultBase(actx.action_name), actx_(actx) {}

    void add_content(const ActionContent& c) { content_.push_back(c); }
    void add_metadata(const std::string& k, const std::string& v) { metadata_[k] = v; }
    void add_metadata(const std::map<std::string, std::string>& m) {
        merge_last_wins(metadata_, m);
    }
    void delete_metadata_key(const std::string& k) { delete_metadata_keys_.push_back(k); }
    void add_annotation(const std::string& k, const std::string& v) { annotations_[k] = v; }
    void add_annotations(const std::map<std::string, std::string>& m) {
        merge_last_wins(annotations_, m);
    }

    // Save data to storage and add the resulting content to this result.
    ActionContent& save_content(const std::vector<uint8_t>& data, const std::string& name,
                                const std::string& media_type) {
        auto c = deltafi::save_content(*actx_.content_service, actx_, data, name, media_type);
        content_.push_back(std::move(c));
        return content_.back();
    }

    // Save a string to storage and add the resulting content to this result.
    ActionContent& save_content(const std::string& data, const std::string& name,
                                const std::string& media_type) {
        auto c =
            deltafi::save_content_string(*actx_.content_service, actx_, data, name, media_type);
        content_.push_back(std::move(c));
        return content_.back();
    }

    // Save content the action streams into an ostream sink (bounded memory, multipart).
    ActionContent& save_content_from_writer(const std::function<void(std::ostream&)>& write_fn,
                                            const std::string& name,
                                            const std::string& media_type) {
        auto c = deltafi::save_content_from_writer(*actx_.content_service, actx_, write_fn, name,
                                                   media_type);
        content_.push_back(std::move(c));
        return content_.back();
    }

    ActionEvent to_action_event(const std::chrono::system_clock::time_point& start) const {
        auto event = new_base_event(actx_, start, "TRANSFORM", messages_, metrics_);
        event.transform.push_back(TransformEvent{.name = "",
                                                 .did = actx_.did,
                                                 .content = content_,
                                                 .metadata = metadata_,
                                                 .delete_metadata_keys = delete_metadata_keys_,
                                                 .annotations = annotations_});
        return event;
    }

    const std::vector<ActionContent>& content() const { return content_; }

    friend std::ostream& operator<<(std::ostream& os, const TransformResult& r);

private:
    ActionContext& actx_;
    std::vector<ActionContent> content_;
    std::map<std::string, std::string> metadata_;
    std::vector<std::string> delete_metadata_keys_;
    std::map<std::string, std::string> annotations_;
};

// Single child in a multi-child split.
class ChildTransformResult : public ResultBase {
public:
    ChildTransformResult(ActionContext& parent_ctx, const std::string& delta_file_name)
        : ResultBase(parent_ctx.action_name),
          actx_(parent_ctx.child_context()),
          delta_file_name_(delta_file_name) {}

    ActionContext& context() { return actx_; }
    void add_content(const ActionContent& c) { content_.push_back(c); }
    void add_metadata(const std::string& k, const std::string& v) { metadata_[k] = v; }
    void add_metadata(const std::map<std::string, std::string>& m) {
        merge_last_wins(metadata_, m);
    }
    void delete_metadata_key(const std::string& k) { delete_metadata_keys_.push_back(k); }
    void add_annotation(const std::string& k, const std::string& v) { annotations_[k] = v; }
    void add_annotations(const std::map<std::string, std::string>& m) {
        merge_last_wins(annotations_, m);
    }

    // Save data to storage and add the resulting content to this child.
    ActionContent& save_content(const std::vector<uint8_t>& data, const std::string& name,
                                const std::string& media_type) {
        auto c = deltafi::save_content(*actx_.content_service, actx_, data, name, media_type);
        content_.push_back(std::move(c));
        return content_.back();
    }

    // Save a string to storage and add the resulting content to this child.
    ActionContent& save_content(const std::string& data, const std::string& name,
                                const std::string& media_type) {
        auto c =
            deltafi::save_content_string(*actx_.content_service, actx_, data, name, media_type);
        content_.push_back(std::move(c));
        return content_.back();
    }

    const std::vector<ActionContent>& content() const { return content_; }
    const std::string& did() const { return actx_.did; }

    friend class TransformResults;
    friend std::ostream& operator<<(std::ostream& os, const TransformResults& r);

private:
    ActionContext actx_;
    std::string delta_file_name_;
    std::vector<ActionContent> content_;
    std::map<std::string, std::string> metadata_;
    std::vector<std::string> delete_metadata_keys_;
    std::map<std::string, std::string> annotations_;
};

// Output of a transform action that splits into multiple children.
class TransformResults : public ResultBase {
public:
    explicit TransformResults(ActionContext& actx) : ResultBase(actx.action_name), actx_(actx) {}

    void add_child(ChildTransformResult child) { children_.push_back(std::move(child)); }

    // Create and add a child, returning a reference for chaining.
    ChildTransformResult& add_child(const std::string& delta_file_name) {
        children_.emplace_back(actx_, delta_file_name);
        return children_.back();
    }

    ActionEvent to_action_event(const std::chrono::system_clock::time_point& start) const {
        auto event = new_base_event(actx_, start, "TRANSFORM", messages_, metrics_);
        for (const auto& child : children_) {
            event.transform.push_back(
                TransformEvent{.name = child.delta_file_name_,
                               .did = child.actx_.did,
                               .content = child.content_,
                               .metadata = child.metadata_,
                               .delete_metadata_keys = child.delete_metadata_keys_,
                               .annotations = child.annotations_,
                               .messages = child.messages_});
        }
        return event;
    }

    const std::vector<ChildTransformResult>& children() const { return children_; }

    friend std::ostream& operator<<(std::ostream& os, const TransformResults& r);

private:
    ActionContext& actx_;
    std::vector<ChildTransformResult> children_;
};

// Output of an egress action.
class EgressResult : public ResultBase {
public:
    explicit EgressResult(ActionContext& actx) : ResultBase(actx.action_name), actx_(actx) {}

    ActionEvent to_action_event(const std::chrono::system_clock::time_point& start) const {
        return new_base_event(actx_, start, "EGRESS", messages_, metrics_);
    }

    friend std::ostream& operator<<(std::ostream& os, const EgressResult& r);

private:
    ActionContext& actx_;
};

// Ingress result item describing a single DeltaFile to be created.
class IngressResultItem {
public:
    explicit IngressResultItem(const std::string& delta_file_name, ContentServiceBase* cs = nullptr,
                               ActionContext* actx = nullptr)
        : did_(generate_uuid()), delta_file_name_(delta_file_name), cs_(cs), actx_(actx) {}

    void add_content(const ActionContent& c) { content_.push_back(c); }
    void add_metadata(const std::string& k, const std::string& v) { metadata_[k] = v; }
    void add_metadata(const std::map<std::string, std::string>& m) {
        merge_last_wins(metadata_, m);
    }
    void add_annotation(const std::string& k, const std::string& v) { annotations_[k] = v; }
    void add_annotations(const std::map<std::string, std::string>& m) {
        merge_last_wins(annotations_, m);
    }

    // Save a string to storage and add the resulting content to this item.
    ActionContent& save_content(const std::string& data, const std::string& name,
                                const std::string& media_type) {
        std::vector<uint8_t> bytes(data.begin(), data.end());
        auto seg = cs_->save(did_, bytes);
        if (actx_) actx_->track_segment(seg);
        ActionContent c{.name = name, .media_type = media_type, .segments = {seg}};
        c.content_service_ = cs_;
        content_.push_back(std::move(c));
        return content_.back();
    }

    // Save bytes to storage and add the resulting content to this item.
    ActionContent& save_content(const std::vector<uint8_t>& data, const std::string& name,
                                const std::string& media_type) {
        auto seg = cs_->save(did_, data);
        if (actx_) actx_->track_segment(seg);
        ActionContent c{.name = name, .media_type = media_type, .segments = {seg}};
        c.content_service_ = cs_;
        content_.push_back(std::move(c));
        return content_.back();
    }

    const std::string& did() const { return did_; }
    const std::vector<ActionContent>& content() const { return content_; }

    friend class IngressResult;
    friend std::ostream& operator<<(std::ostream& os, const IngressResult& r);

private:
    std::string did_;
    std::string delta_file_name_;
    std::vector<ActionContent> content_;
    std::map<std::string, std::string> metadata_;
    std::map<std::string, std::string> annotations_;
    ContentServiceBase* cs_ = nullptr;
    ActionContext* actx_ = nullptr;
};

// Output of a timed ingress action.
class IngressResult : public ResultBase {
public:
    explicit IngressResult(ActionContext& actx)
        : ResultBase(actx.action_name), actx_(actx), status_("HEALTHY") {}

    void add_item(IngressResultItem item) { items_.push_back(std::move(item)); }

    // Create and add an item, returning a reference for chaining.
    IngressResultItem& create_item(const std::string& delta_file_name) {
        items_.emplace_back(delta_file_name, actx_.content_service, &actx_);
        return items_.back();
    }

    void set_memo(const std::string& m) { memo_ = m; }
    void set_status(const std::string& s) { status_ = s; }
    void set_status_message(const std::string& m) { status_message_ = m; }
    void set_execute_immediate(bool b) { execute_immediate_ = b; }

    ActionEvent to_action_event(const std::chrono::system_clock::time_point& start) const {
        auto event = new_base_event(actx_, start, "INGRESS", messages_, metrics_);
        IngressEvent ie;
        ie.memo = memo_;
        ie.execute_immediate = execute_immediate_;
        ie.status = status_;
        ie.status_message = status_message_;
        for (const auto& item : items_) {
            ie.ingress_items.push_back(IngressEventItem{.did = item.did_,
                                                        .delta_file_name = item.delta_file_name_,
                                                        .content = item.content_,
                                                        .metadata = item.metadata_,
                                                        .annotations = item.annotations_});
        }
        event.ingress = std::move(ie);
        return event;
    }

    const std::vector<IngressResultItem>& items() const { return items_; }

    friend std::ostream& operator<<(std::ostream& os, const IngressResult& r);

private:
    ActionContext& actx_;
    std::vector<IngressResultItem> items_;
    std::string memo_;
    std::string status_;
    std::string status_message_;
    bool execute_immediate_ = false;
};

// Error result with cause and optional context.
class ErrorResult : public ResultBase {
public:
    ErrorResult(ActionContext& actx, const std::string& cause)
        : ResultBase(actx.action_name), actx_(actx), cause_(cause) {}

    ErrorResult& set_context(const std::string& ctx) {
        context_ = ctx;
        return *this;
    }
    void add_annotation(const std::string& k, const std::string& v) { annotations_[k] = v; }
    void add_annotations(const std::map<std::string, std::string>& m) {
        merge_last_wins(annotations_, m);
    }

    ActionEvent to_action_event(const std::chrono::system_clock::time_point& start) const {
        auto event = new_base_event(actx_, start, "ERROR", messages_, metrics_);
        event.error = ErrorEvent{.cause = cause_, .context = context_, .annotations = annotations_};
        return event;
    }

    friend std::ostream& operator<<(std::ostream& os, const ErrorResult& r);

private:
    ActionContext& actx_;
    std::string cause_;
    std::string context_;
    std::map<std::string, std::string> annotations_;
};

// Filter result indicating the action chose to filter this DeltaFile.
class FilterResult : public ResultBase {
public:
    FilterResult(ActionContext& actx, const std::string& message)
        : ResultBase(actx.action_name), actx_(actx), message_(message) {}

    FilterResult& set_context(const std::string& ctx) {
        context_ = ctx;
        return *this;
    }
    void add_annotation(const std::string& k, const std::string& v) { annotations_[k] = v; }
    void add_annotations(const std::map<std::string, std::string>& m) {
        merge_last_wins(annotations_, m);
    }

    ActionEvent to_action_event(const std::chrono::system_clock::time_point& start) const {
        auto event = new_base_event(actx_, start, "FILTER", messages_, metrics_);
        event.filter =
            FilterEvent{.message = message_, .context = context_, .annotations = annotations_};
        return event;
    }

    friend std::ostream& operator<<(std::ostream& os, const FilterResult& r);

private:
    ActionContext& actx_;
    std::string message_;
    std::string context_;
    std::map<std::string, std::string> annotations_;
};

// --- Debug output operators ---

inline std::ostream& operator<<(std::ostream& os, const TransformResult& r) {
    os << "TransformResult {\n";
    detail::print_content(os, r.content_, 1);
    detail::print_map(os, "metadata", r.metadata_, 1);
    detail::print_string_list(os, "deleteMetadataKeys", r.delete_metadata_keys_, 1);
    detail::print_map(os, "annotations", r.annotations_, 1);
    detail::print_messages(os, r.messages_, 1);
    os << "}";
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const TransformResults& r) {
    os << "TransformResults {\n";
    if (!r.children_.empty()) {
        detail::print_indent(os, 1);
        os << "children:\n";
        for (size_t i = 0; i < r.children_.size(); ++i) {
            const auto& child = r.children_[i];
            detail::print_indent(os, 2);
            os << "[" << i << "] \"" << child.delta_file_name_ << "\" (did: " << child.actx_.did
               << ") {\n";
            detail::print_content(os, child.content_, 3);
            detail::print_map(os, "metadata", child.metadata_, 3);
            detail::print_string_list(os, "deleteMetadataKeys", child.delete_metadata_keys_, 3);
            detail::print_map(os, "annotations", child.annotations_, 3);
            detail::print_messages(os, child.messages_, 3);
            detail::print_indent(os, 2);
            os << "}\n";
        }
    }
    detail::print_messages(os, r.messages_, 1);
    os << "}";
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const EgressResult& r) {
    os << "EgressResult {\n";
    detail::print_messages(os, r.messages_, 1);
    os << "}";
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const IngressResult& r) {
    os << "IngressResult {\n";
    if (!r.memo_.empty()) {
        detail::print_indent(os, 1);
        os << "memo: " << r.memo_ << "\n";
    }
    detail::print_indent(os, 1);
    os << "status: " << r.status_ << "\n";
    if (!r.status_message_.empty()) {
        detail::print_indent(os, 1);
        os << "statusMessage: " << r.status_message_ << "\n";
    }
    detail::print_indent(os, 1);
    os << "executeImmediate: " << (r.execute_immediate_ ? "true" : "false") << "\n";
    if (!r.items_.empty()) {
        detail::print_indent(os, 1);
        os << "items:\n";
        for (size_t i = 0; i < r.items_.size(); ++i) {
            const auto& item = r.items_[i];
            detail::print_indent(os, 2);
            os << "[" << i << "] \"" << item.delta_file_name_ << "\" (did: " << item.did_
               << ") {\n";
            detail::print_content(os, item.content_, 3);
            detail::print_map(os, "metadata", item.metadata_, 3);
            detail::print_map(os, "annotations", item.annotations_, 3);
            detail::print_indent(os, 2);
            os << "}\n";
        }
    }
    detail::print_messages(os, r.messages_, 1);
    os << "}";
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const ErrorResult& r) {
    os << "ErrorResult {\n";
    detail::print_indent(os, 1);
    os << "cause: " << r.cause_ << "\n";
    if (!r.context_.empty()) {
        detail::print_indent(os, 1);
        os << "context: " << r.context_ << "\n";
    }
    detail::print_map(os, "annotations", r.annotations_, 1);
    detail::print_messages(os, r.messages_, 1);
    os << "}";
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const FilterResult& r) {
    os << "FilterResult {\n";
    detail::print_indent(os, 1);
    os << "message: " << r.message_ << "\n";
    if (!r.context_.empty()) {
        detail::print_indent(os, 1);
        os << "context: " << r.context_ << "\n";
    }
    detail::print_map(os, "annotations", r.annotations_, 1);
    detail::print_messages(os, r.messages_, 1);
    os << "}";
    return os;
}

// Thrown by an action to signal an error disposition, carrying full context.
// Converted to an ErrorResult by the worker's fault barrier.
class ErrorResultException : public std::runtime_error {
public:
    explicit ErrorResultException(std::string cause, std::string context = "")
        : std::runtime_error(cause), cause_(std::move(cause)), context_(std::move(context)) {}

    ErrorResultException& add_annotation(const std::string& k, const std::string& v) {
        annotations_[k] = v;
        return *this;
    }
    ErrorResultException& add_metric(const Metric& m) {
        metrics_.push_back(m);
        return *this;
    }

    ErrorResult to_result(ActionContext& actx) const {
        ErrorResult r(actx, cause_);
        r.set_context(context_);
        for (const auto& [k, v] : annotations_) r.add_annotation(k, v);
        for (const auto& m : metrics_) r.add_metric(m);
        return r;
    }

private:
    std::string cause_;
    std::string context_;
    std::map<std::string, std::string> annotations_;
    std::vector<Metric> metrics_;
};

// Thrown by an action to signal a (non-error) filter disposition.
// Converted to a FilterResult by the worker's fault barrier.
class FilterResultException : public std::runtime_error {
public:
    explicit FilterResultException(std::string message, std::string context = "")
        : std::runtime_error(message), message_(std::move(message)), context_(std::move(context)) {}

    FilterResultException& add_annotation(const std::string& k, const std::string& v) {
        annotations_[k] = v;
        return *this;
    }
    FilterResultException& add_metric(const Metric& m) {
        metrics_.push_back(m);
        return *this;
    }

    FilterResult to_result(ActionContext& actx) const {
        FilterResult r(actx, message_);
        r.set_context(context_);
        for (const auto& [k, v] : annotations_) r.add_annotation(k, v);
        for (const auto& m : metrics_) r.add_metric(m);
        return r;
    }

private:
    std::string message_;
    std::string context_;
    std::map<std::string, std::string> annotations_;
    std::vector<Metric> metrics_;
};

// Variant types for action return values.
using TransformResultType =
    std::variant<TransformResult, TransformResults, FilterResult, ErrorResult>;
using EgressResultType = std::variant<EgressResult, FilterResult, ErrorResult>;
using IngressResultType = std::variant<IngressResult, FilterResult, ErrorResult>;

// Debug output for result variant types.
inline std::ostream& operator<<(std::ostream& os, const TransformResultType& r) {
    std::visit([&os](const auto& v) { os << v; }, r);
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const EgressResultType& r) {
    std::visit([&os](const auto& v) { os << v; }, r);
    return os;
}

inline std::ostream& operator<<(std::ostream& os, const IngressResultType& r) {
    std::visit([&os](const auto& v) { os << v; }, r);
    return os;
}

// Convert any result variant to an ActionEvent.
inline ActionEvent result_to_event(const TransformResultType& result,
                                   const std::chrono::system_clock::time_point& start) {
    return std::visit([&start](const auto& r) { return r.to_action_event(start); }, result);
}

inline ActionEvent result_to_event(const EgressResultType& result,
                                   const std::chrono::system_clock::time_point& start) {
    return std::visit([&start](const auto& r) { return r.to_action_event(start); }, result);
}

inline ActionEvent result_to_event(const IngressResultType& result,
                                   const std::chrono::system_clock::time_point& start) {
    return std::visit([&start](const auto& r) { return r.to_action_event(start); }, result);
}

// Collect all segments from a result for orphan cleanup.
inline std::vector<Segment> collect_result_segments(const TransformResultType& result) {
    std::vector<Segment> segments;
    std::visit(
        [&segments](const auto& r) {
            using T = std::decay_t<decltype(r)>;
            if constexpr (std::is_same_v<T, TransformResult>) {
                for (const auto& c : r.content())
                    segments.insert(segments.end(), c.segments.begin(), c.segments.end());
            } else if constexpr (std::is_same_v<T, TransformResults>) {
                for (const auto& child : r.children())
                    for (const auto& c : child.content())
                        segments.insert(segments.end(), c.segments.begin(), c.segments.end());
            }
        },
        result);
    return segments;
}

inline std::vector<Segment> collect_result_segments(const IngressResultType& result) {
    std::vector<Segment> segments;
    if (const auto* ir = std::get_if<IngressResult>(&result)) {
        for (const auto& item : ir->items())
            for (const auto& c : item.content())
                segments.insert(segments.end(), c.segments.begin(), c.segments.end());
    }
    return segments;
}

inline std::vector<Segment> collect_result_segments(const EgressResultType& /*result*/) {
    return {};
}

}  // namespace deltafi
