// ABOUTME: Macro-based parameter schema generation for action parameters.
// ABOUTME: Generates JSON Schema, serialization, and deserialization without reflection.

#pragma once

#include <cctype>
#include <concepts>
#include <cstdlib>
#include <map>
#include <optional>
#include <regex>
#include <string>
#include <tuple>
#include <type_traits>
#include <vector>

#include <nlohmann/json.hpp>

namespace deltafi {

// Stores a reference to an environment variable by name.
// Only the variable name is stored/transmitted; the value is resolved at runtime.
// Name must be UPPER_SNAKE_CASE (e.g. "DB_PASSWORD", "API_KEY").
class EnvVar {
public:
    EnvVar() = default;
    explicit EnvVar(std::string name) : name_(std::move(name)) {}

    // Resolve the environment variable's value. Throws if unset.
    std::string resolve() const {
        if (name_.empty()) {
            throw std::runtime_error("no environment variable name configured");
        }
        const char* val = std::getenv(name_.c_str());
        if (!val) {
            throw std::runtime_error(
                "environment variable '" + name_ + "' is not set");
        }
        return val;
    }

    std::string resolve_or_default(const std::string& default_value) const {
        if (name_.empty()) return default_value;
        const char* val = std::getenv(name_.c_str());
        return val ? val : default_value;
    }

    bool is_set() const { return !name_.empty(); }
    const std::string& name() const { return name_; }

    friend void from_json(const nlohmann::json& j, EnvVar& e) {
        auto s = j.get<std::string>();
        if (!s.empty()) {
            static const std::regex pattern("^[A-Z][A-Z0-9_]*$");
            if (!std::regex_match(s, pattern)) {
                throw std::runtime_error(
                    "invalid environment variable name '" + s +
                    "': must be UPPER_SNAKE_CASE");
            }
        }
        e.name_ = std::move(s);
    }

    friend void to_json(nlohmann::json& j, const EnvVar& e) {
        j = e.name_;
    }

    static nlohmann::json schema() {
        return {
            {"type", "string"},
            {"pattern", "^[A-Z][A-Z0-9_]*$"},
            {"x-deltafi-type", "EnvVar"}
        };
    }

private:
    std::string name_;
};

// Represents a size in bytes, parsed from human-readable strings like "10MB" or "512KB".
class DataSize {
public:
    static constexpr int64_t byte_unit = 1;
    static constexpr int64_t kilobyte = 1024;
    static constexpr int64_t megabyte = 1024 * kilobyte;
    static constexpr int64_t gigabyte = 1024 * megabyte;
    static constexpr int64_t terabyte = 1024 * gigabyte;

    DataSize() = default;
    explicit DataSize(int64_t bytes) : bytes_(bytes) {}

    int64_t bytes() const { return bytes_; }

    std::string to_string() const {
        if (bytes_ >= terabyte && bytes_ % terabyte == 0)
            return std::to_string(bytes_ / terabyte) + "TB";
        if (bytes_ >= gigabyte && bytes_ % gigabyte == 0)
            return std::to_string(bytes_ / gigabyte) + "GB";
        if (bytes_ >= megabyte && bytes_ % megabyte == 0)
            return std::to_string(bytes_ / megabyte) + "MB";
        if (bytes_ >= kilobyte && bytes_ % kilobyte == 0)
            return std::to_string(bytes_ / kilobyte) + "KB";
        return std::to_string(bytes_) + "B";
    }

    static DataSize parse(const std::string& s) {
        if (s.empty()) {
            throw std::runtime_error("empty data size string");
        }
        size_t i = 0;
        while (i < s.size() && s[i] >= '0' && s[i] <= '9') ++i;
        if (i == 0) {
            throw std::runtime_error(
                "invalid data size '" + s + "': must start with a number");
        }
        int64_t n = std::stoll(s.substr(0, i));
        std::string unit;
        for (size_t j = i; j < s.size(); ++j) {
            unit += static_cast<char>(std::toupper(s[j]));
        }
        int64_t multiplier;
        if (unit == "B") multiplier = byte_unit;
        else if (unit == "KB") multiplier = kilobyte;
        else if (unit == "MB") multiplier = megabyte;
        else if (unit == "GB") multiplier = gigabyte;
        else if (unit == "TB") multiplier = terabyte;
        else throw std::runtime_error(
            "invalid data size unit '" + unit + "': must be B, KB, MB, GB, or TB");
        return DataSize(n * multiplier);
    }

    friend void from_json(const nlohmann::json& j, DataSize& d) {
        d = DataSize::parse(j.get<std::string>());
    }

    friend void to_json(nlohmann::json& j, const DataSize& d) {
        j = d.to_string();
    }

    static nlohmann::json schema() {
        return {
            {"type", "string"},
            {"pattern", "^[0-9]+(B|KB|MB|GB|TB)$"},
            {"x-deltafi-type", "DataSize"}
        };
    }

private:
    int64_t bytes_ = 0;
};

} // namespace deltafi

namespace deltafi::params {

// Convert snake_case C++ field name to camelCase JSON field name.
inline std::string snake_to_camel(const char* s) {
    std::string result;
    bool capitalize = false;
    for (const char* p = s; *p; ++p) {
        if (*p == '_') {
            capitalize = true;
        } else {
            result += capitalize ? static_cast<char>(std::toupper(*p)) : *p;
            capitalize = false;
        }
    }
    return result;
}

// Concept for types that have their own schema() method (nested DELTAFI_PARAMS).
template<typename T>
concept HasSchema = requires {
    { T::schema() } -> std::convertible_to<nlohmann::json>;
};

// Type traits for container detection.
template<typename T> struct is_vector : std::false_type {};
template<typename T> struct is_vector<std::vector<T>> : std::true_type {};

template<typename T> struct is_string_map : std::false_type {};
template<typename T> struct is_string_map<std::map<std::string, T>> : std::true_type {};

template<typename T> struct is_optional : std::false_type {};
template<typename T> struct is_optional<std::optional<T>> : std::true_type {};

// Whether a type's default value should be emitted in the JSON schema.
// True for simple scalar types, false for containers, optionals, and nested structs.
template<typename T>
constexpr bool should_emit_default() {
    using Base = std::remove_cv_t<T>;
    if constexpr (is_vector<Base>::value) return false;
    else if constexpr (is_string_map<Base>::value) return false;
    else if constexpr (is_optional<Base>::value) return false;
    else if constexpr (HasSchema<Base>) return false;
    else return true;
}

// Generate JSON Schema type descriptor for a C++ type.
template<typename T>
nlohmann::json type_schema() {
    using Base = std::remove_cv_t<T>;
    if constexpr (is_optional<Base>::value) {
        return type_schema<typename Base::value_type>();
    } else if constexpr (is_vector<Base>::value) {
        nlohmann::json s = {{"type", "array"}};
        s["items"] = type_schema<typename Base::value_type>();
        return s;
    } else if constexpr (is_string_map<Base>::value) {
        nlohmann::json s = {{"type", "object"}};
        s["additionalProperties"] = type_schema<typename Base::mapped_type>();
        return s;
    } else if constexpr (HasSchema<Base>) {
        return Base::schema();
    } else if constexpr (std::is_same_v<Base, std::string>) {
        return {{"type", "string"}};
    } else if constexpr (std::is_same_v<Base, bool>) {
        return {{"type", "boolean"}};
    } else if constexpr (std::is_integral_v<Base>) {
        return {{"type", "integer"}};
    } else if constexpr (std::is_floating_point_v<Base>) {
        return {{"type", "number"}};
    } else {
        return {{"type", "string"}};
    }
}

// Coerce a JSON value to the target C++ type.
// DeltaFi core may send all params as strings after variable substitution.
template<typename T>
T coerce_get(const nlohmann::json& j) {
    using Base = std::remove_cv_t<T>;

    if constexpr (is_optional<Base>::value) {
        if (j.is_null()) return std::nullopt;
        using V = typename Base::value_type;
        return coerce_get<V>(j);
    } else if constexpr (std::is_same_v<Base, bool>) {
        if (j.is_string()) {
            auto s = j.get<std::string>();
            if (s == "true") return true;
            if (s == "false") return false;
        }
    } else if constexpr (std::is_integral_v<Base> && !std::is_same_v<Base, bool>) {
        if (j.is_string()) {
            try { return static_cast<T>(std::stoll(j.get<std::string>())); } catch (...) {}
        }
        if (j.is_number_float()) {
            return static_cast<T>(j.get<double>());
        }
    } else if constexpr (std::is_floating_point_v<Base>) {
        if (j.is_string()) {
            try { return static_cast<T>(std::stod(j.get<std::string>())); } catch (...) {}
        }
    }

    return j.get<T>();
}

// Read a field value from JSON, handling std::optional.
template<typename T>
void read_field(const nlohmann::json& j, const std::string& key, T& out) {
    if (!j.contains(key) || j[key].is_null()) return;
    if constexpr (is_optional<std::remove_cv_t<T>>::value) {
        using V = typename std::remove_cv_t<T>::value_type;
        out = coerce_get<V>(j[key]);
    } else {
        out = coerce_get<T>(j[key]);
    }
}

// Write a field value to JSON, handling std::optional.
template<typename T>
void write_field(nlohmann::json& j, const std::string& key, const T& val) {
    if constexpr (is_optional<std::remove_cv_t<T>>::value) {
        if (val.has_value()) {
            j[key] = *val;
        } else {
            j[key] = nullptr;
        }
    } else {
        j[key] = val;
    }
}

// Field descriptor for an optional parameter.
// Default value is inferred from a default-constructed instance of the owning struct.
template<typename T, typename C>
struct FieldDescriptor {
    T C::* ptr;
    const char* name;
    const char* description;
    bool required;

    void add_to_schema(nlohmann::json& props, nlohmann::json& req) const {
        auto key = snake_to_camel(name);
        auto s = type_schema<T>();
        s["description"] = description;
        if (required) {
            req.push_back(key);
        } else if constexpr (should_emit_default<T>()) {
            C defaults{};
            s["default"] = nlohmann::json(defaults.*ptr);
        }
        props[key] = std::move(s);
    }

    void read_from(const nlohmann::json& j, C& obj) const {
        read_field(j, snake_to_camel(name), obj.*ptr);
    }

    void write_to(nlohmann::json& j, const C& obj) const {
        write_field(j, snake_to_camel(name), obj.*ptr);
    }
};

// Field descriptor for an enum-constrained parameter.
template<typename T, typename C>
struct EnumFieldDescriptor {
    T C::* ptr;
    const char* name;
    const char* description;
    std::vector<T> allowed;

    void add_to_schema(nlohmann::json& props, nlohmann::json& /*req*/) const {
        auto key = snake_to_camel(name);
        auto s = type_schema<T>();
        s["description"] = description;
        C defaults{};
        s["default"] = nlohmann::json(defaults.*ptr);
        s["enum"] = allowed;
        props[key] = std::move(s);
    }

    void read_from(const nlohmann::json& j, C& obj) const {
        read_field(j, snake_to_camel(name), obj.*ptr);
    }

    void write_to(nlohmann::json& j, const C& obj) const {
        write_field(j, snake_to_camel(name), obj.*ptr);
    }
};

// Field descriptor for a range-constrained numeric parameter.
template<typename T, typename C>
struct RangeFieldDescriptor {
    T C::* ptr;
    const char* name;
    const char* description;
    T min_value;
    T max_value;

    void add_to_schema(nlohmann::json& props, nlohmann::json& /*req*/) const {
        auto key = snake_to_camel(name);
        auto s = type_schema<T>();
        s["description"] = description;
        C defaults{};
        s["default"] = nlohmann::json(defaults.*ptr);
        s["minimum"] = min_value;
        s["maximum"] = max_value;
        props[key] = std::move(s);
    }

    void read_from(const nlohmann::json& j, C& obj) const {
        read_field(j, snake_to_camel(name), obj.*ptr);
    }

    void write_to(nlohmann::json& j, const C& obj) const {
        write_field(j, snake_to_camel(name), obj.*ptr);
    }
};

// Factory functions.
template<typename T, typename C>
auto field(T C::* ptr, const char* name, const char* desc) {
    return FieldDescriptor<T, C>{ptr, name, desc, false};
}

template<typename T, typename C>
auto required_field(T C::* ptr, const char* name, const char* desc) {
    return FieldDescriptor<T, C>{ptr, name, desc, true};
}

template<typename T, typename C>
auto enum_field(T C::* ptr, const char* name, const char* desc,
                std::vector<T> allowed) {
    return EnumFieldDescriptor<T, C>{ptr, name, desc, std::move(allowed)};
}

template<typename T, typename C>
auto range_field(T C::* ptr, const char* name, const char* desc,
                 T min_val, T max_val) {
    return RangeFieldDescriptor<T, C>{ptr, name, desc, min_val, max_val};
}

} // namespace deltafi::params

// Generate schema(), from_json(), and to_json() for a parameter struct.
// Each field in __VA_ARGS__ must be a FIELD, REQUIRED, ENUM, or RANGE invocation.
#define DELTAFI_PARAMS(TypeName, ...)                                            \
    using _DeltafiSelf_ = TypeName;                                             \
    static nlohmann::json schema() {                                             \
        nlohmann::json _s = {{"type", "object"},                                 \
                             {"additionalProperties", false}};                   \
        nlohmann::json _props = nlohmann::json::object();                        \
        nlohmann::json _req = nlohmann::json::array();                           \
        auto _fields = std::make_tuple(__VA_ARGS__);                             \
        std::apply([&](auto&... f) {                                             \
            (f.add_to_schema(_props, _req), ...);                                \
        }, _fields);                                                             \
        _s["properties"] = _props;                                               \
        if (!_req.empty()) _s["required"] = _req;                                \
        return _s;                                                               \
    }                                                                            \
    friend void from_json(const nlohmann::json& _j, TypeName& _p) {             \
        auto _fields = std::make_tuple(__VA_ARGS__);                             \
        std::apply([&](auto&... f) {                                             \
            (f.read_from(_j, _p), ...);                                          \
        }, _fields);                                                             \
    }                                                                            \
    friend void to_json(nlohmann::json& _j, const TypeName& _p) {               \
        _j = nlohmann::json::object();                                           \
        auto _fields = std::make_tuple(__VA_ARGS__);                             \
        std::apply([&](auto&... f) {                                             \
            (f.write_to(_j, _p), ...);                                           \
        }, _fields);                                                             \
    }

// Optional field. Default value is inferred from the member initializer.
#define FIELD(_f, desc) \
    deltafi::params::field(&_DeltafiSelf_::_f, #_f, desc)

// Required field (added to schema "required" array).
#define REQUIRED(_f, desc) \
    deltafi::params::required_field(&_DeltafiSelf_::_f, #_f, desc)

// Enum-constrained field. Default from member initializer.
#define ENUM(_f, desc, ...) \
    deltafi::params::enum_field(&_DeltafiSelf_::_f, #_f, desc, {__VA_ARGS__})

// Range-constrained numeric field. Default from member initializer.
#define RANGE(_f, desc, min_val, max_val) \
    deltafi::params::range_field(&_DeltafiSelf_::_f, #_f, desc, min_val, max_val)
