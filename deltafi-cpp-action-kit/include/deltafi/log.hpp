// ABOUTME: Logging types for action result messages.
// ABOUTME: Defines LogSeverity and LogMessage struct matching the DeltaFi wire format.

#pragma once

#include <chrono>
#include <cstdint>
#include <iomanip>
#include <sstream>
#include <string>

#include <nlohmann/json.hpp>

namespace deltafi {

// Severity level for log messages included in action results.
enum class LogSeverity : std::uint8_t { Trace, Info, Warning, Error };

// Serialize LogSeverity to JSON as uppercase string.
inline void to_json(nlohmann::json& j, const LogSeverity& s) {
    switch (s) {
        case LogSeverity::Trace:
            j = "TRACE";
            break;
        case LogSeverity::Info:
            j = "INFO";
            break;
        case LogSeverity::Warning:
            j = "WARNING";
            break;
        case LogSeverity::Error:
            j = "ERROR";
            break;
    }
}

// Format a time_point as ISO-8601 with timezone offset (matching Go's RFC3339Nano).
inline std::string format_time(const std::chrono::system_clock::time_point& tp) {
    auto t = std::chrono::system_clock::to_time_t(tp);
    auto us =
        std::chrono::duration_cast<std::chrono::microseconds>(tp.time_since_epoch()) % 1000000;
    std::tm tm{};
    gmtime_r(&t, &tm);
    std::ostringstream ss;
    ss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%S");
    ss << '.' << std::setfill('0') << std::setw(6) << us.count() << 'Z';
    return ss.str();
}

// Structured log entry included in action results.
struct LogMessage {
    LogSeverity severity;
    std::string created;
    std::string source;
    std::string message;
};

inline void to_json(nlohmann::json& j, const LogMessage& m) {
    j = nlohmann::json{{"severity", m.severity},
                       {"created", m.created},
                       {"source", m.source},
                       {"message", m.message}};
}

// Create a LogMessage with the current timestamp.
inline LogMessage new_log_message(LogSeverity severity, const std::string& source,
                                  const std::string& message) {
    return LogMessage{.severity = severity,
                      .created = format_time(std::chrono::system_clock::now()),
                      .source = source,
                      .message = message};
}

// RAII stream that appends a LogMessage on destruction.
class LogStream {
public:
    LogStream(std::vector<LogMessage>& messages, LogSeverity severity, const std::string& source)
        : messages_(messages), severity_(severity), source_(source) {}

    ~LogStream() {
        // RAII flush; a destructor must not let exceptions escape.
        try {
            messages_.push_back(new_log_message(severity_, source_, ss_.str()));
        } catch (...) {  // NOLINT(bugprone-empty-catch): destructor must not let exceptions escape
        }
    }

    LogStream(const LogStream&) = delete;
    LogStream& operator=(const LogStream&) = delete;
    LogStream(LogStream&&) = default;
    // Reference member (messages_) can't be reseated, so move-assignment is deleted.
    LogStream& operator=(LogStream&&) = delete;

    template<typename T>
    LogStream& operator<<(const T& value) {
        ss_ << value;
        return *this;
    }

private:
    std::vector<LogMessage>& messages_;
    LogSeverity severity_;
    std::string source_;
    std::ostringstream ss_;
};

}  // namespace deltafi
