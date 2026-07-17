// ABOUTME: Minimal block-YAML-subset parser producing nlohmann::json, for action-options files.
// ABOUTME: Supports block maps, block sequences (incl. sequences of maps), and plain/quoted
// scalars.

#pragma once

#include <cstddef>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

namespace deltafi::simpleyaml {

// This is intentionally NOT a general YAML parser: it covers the block subset used by action
// options — nested maps, sequences, sequences of single-line maps, `#` line comments, and plain
// or quoted scalar values. All scalars are produced as JSON strings.

struct Line {
    int indent;
    std::string text;
};

inline std::string trim(const std::string& s) {
    auto begin = s.find_first_not_of(" \t");
    if (begin == std::string::npos) return "";
    auto end = s.find_last_not_of(" \t");
    return s.substr(begin, end - begin + 1);
}

inline nlohmann::json scalar(const std::string& raw) {
    auto s = trim(raw);
    if (s.size() >= 2 &&
        ((s.front() == '"' && s.back() == '"') || (s.front() == '\'' && s.back() == '\''))) {
        return s.substr(1, s.size() - 2);
    }
    return s;
}

// Split "key: value" on the first ": " (or a trailing ":"). Returns true if a key was found.
inline bool split_kv(const std::string& text, std::string& key, std::string& value) {
    for (size_t i = 0; i < text.size(); ++i) {
        if (text[i] == ':' && (i + 1 == text.size() || text[i + 1] == ' ')) {
            key = trim(text.substr(0, i));
            value = (i + 1 < text.size()) ? trim(text.substr(i + 1)) : "";
            return true;
        }
    }
    return false;
}

inline std::vector<Line> tokenize(const std::string& doc) {
    std::vector<Line> lines;
    size_t pos = 0;
    while (pos <= doc.size()) {
        auto nl = doc.find('\n', pos);
        std::string line = doc.substr(pos, nl == std::string::npos ? std::string::npos : nl - pos);
        pos = (nl == std::string::npos) ? doc.size() + 1 : nl + 1;
        size_t spaces = 0;
        while (spaces < line.size() && line[spaces] == ' ') {
            ++spaces;
        }
        auto content = trim(line);
        if (content.empty() || content[0] == '#') continue;  // blank or comment line
        lines.push_back({.indent = static_cast<int>(spaces), .text = content});
    }
    return lines;
}

inline bool is_seq(const std::string& text) {
    return text == "-" || text.starts_with("- ");
}

nlohmann::json parse_node(const std::vector<Line>& lines, size_t& idx, int indent);

inline nlohmann::json parse_map(const std::vector<Line>& lines, size_t& idx, int indent) {
    nlohmann::json obj = nlohmann::json::object();
    while (idx < lines.size() && lines[idx].indent == indent && !is_seq(lines[idx].text)) {
        std::string key;
        std::string value;
        if (!split_kv(lines[idx].text, key, value)) {
            ++idx;
            continue;
        }
        ++idx;
        if (!value.empty()) {
            obj[key] = scalar(value);
        } else if (idx < lines.size() && lines[idx].indent > indent) {
            obj[key] = parse_node(lines, idx, lines[idx].indent);
        } else {
            obj[key] = nullptr;
        }
    }
    return obj;
}

inline nlohmann::json parse_seq(const std::vector<Line>& lines, size_t& idx, int indent) {
    nlohmann::json arr = nlohmann::json::array();
    while (idx < lines.size() && lines[idx].indent == indent && is_seq(lines[idx].text)) {
        std::string after = lines[idx].text == "-" ? "" : lines[idx].text.substr(2);
        std::string key;
        std::string value;
        int child_indent = indent + 2;
        if (after.empty()) {
            ++idx;
            arr.push_back(idx < lines.size() && lines[idx].indent > indent
                              ? parse_node(lines, idx, lines[idx].indent)
                              : nlohmann::json(nullptr));
        } else if (split_kv(after, key, value)) {
            // A single-line map entry starts this item; more entries continue at child_indent.
            nlohmann::json obj = nlohmann::json::object();
            obj[key] = value.empty() ? nlohmann::json(nullptr) : scalar(value);
            ++idx;
            auto entries = parse_map(lines, idx, child_indent);
            for (const auto& [k, v] : entries.items()) obj[k] = v;
            arr.push_back(std::move(obj));
        } else {
            arr.push_back(scalar(after));
            ++idx;
        }
    }
    return arr;
}

inline nlohmann::json parse_node(const std::vector<Line>& lines, size_t& idx, int indent) {
    if (idx >= lines.size()) return nlohmann::json::object();
    return is_seq(lines[idx].text) ? parse_seq(lines, idx, indent) : parse_map(lines, idx, indent);
}

// Parse a block-YAML-subset document into JSON.
inline nlohmann::json to_json(const std::string& doc) {
    auto lines = tokenize(doc);
    if (lines.empty()) return nlohmann::json::object();
    size_t idx = 0;
    return parse_node(lines, idx, lines[0].indent);
}

}  // namespace deltafi::simpleyaml
