// ABOUTME: Action registry for compile-time type detection and runtime storage.
// ABOUTME: Maps action types to type-erased executors via C++20 concepts.

#pragma once

#include <functional>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "deltafi/action.hpp"
#include "deltafi/artifact.hpp"
#include "deltafi/registration.hpp"
#include "deltafi/worker.hpp"

namespace deltafi {

// Delete saved segments not present in the referenced set (orphan-content GC).
inline void cleanup_orphans(ActionContext& actx, const std::vector<Segment>& referenced) {
    // Best-effort GC invoked from OrphanGuard's destructor; it must never throw.
    try {
        if (actx.saved_segments.empty()) return;

        std::set<std::string> ref_set;
        for (const auto& seg : referenced) ref_set.insert(seg.uuid);

        std::vector<Segment> orphans;
        for (const auto& seg : actx.saved_segments) {
            if (!ref_set.contains(seg.uuid)) orphans.push_back(seg);
        }

        if (!orphans.empty() && actx.content_service) {
            actx.content_service->remove(orphans);
        }
    } catch (const std::exception& ex) {
        // Logging must not throw out of a destructor either (spdlog's lazy
        // logger init can allocate).
        try {
            spdlog::error("orphan cleanup failed: {}", ex.what());
        } catch (...) {  // NOLINT(bugprone-empty-catch): logging must not throw from a destructor
        }
    } catch (...) {  // NOLINT(bugprone-empty-catch): best-effort GC must never throw
    }
}

// RAII: runs cleanup_orphans on scope exit, including stack unwind on a thrown
// action. `referenced` starts empty (a throw -> all saved segments are orphans)
// and is set to the result's referenced segments just before normal completion.
// NOLINTNEXTLINE(cppcoreguidelines-special-member-functions): in-place aggregate guard
struct OrphanGuard {
    ActionContext& actx;
    std::vector<Segment> referenced;
    ~OrphanGuard() { cleanup_orphans(actx, referenced); }
};

// Invoke an action with the appropriate signature, deserializing typed params from the event.
template<typename T>
TransformResultType invoke_transform(T& action, ActionContext& actx, QueueEvent& event,
                                     TransformInput& input) {
    if constexpr (HasParamsType<T>) {
        auto params = event.action_params.get<typename T::Params>();
        return action.transform(actx, params, input);
    } else {
        return action.transform(actx, input);
    }
}

template<typename T>
EgressResultType invoke_egress(T& action, ActionContext& actx, QueueEvent& event,
                               EgressInput& input) {
    if constexpr (HasParamsType<T>) {
        auto params = event.action_params.get<typename T::Params>();
        return action.egress(actx, params, input);
    } else {
        return action.egress(actx, input);
    }
}

template<typename T>
IngressResultType invoke_ingress(T& action, ActionContext& actx, QueueEvent& event) {
    if constexpr (HasParamsType<T>) {
        auto params = event.action_params.get<typename T::Params>();
        return action.ingress(actx, params);
    } else {
        return action.ingress(actx);
    }
}

// Build the type-erased executor for an action: input building, param deserialization, the
// action call, orphan-content GC, and result-to-event mapping — the exact path the worker (and
// the test runners) run. The join branch is compiled only for join-capable transforms.
template<typename T>
ActionExecutor make_transform_executor(std::shared_ptr<T> action) {
    return [action = std::move(action)](ActionContext& actx, QueueEvent& event) -> ActionEvent {
        auto start = actx.start_time;
        OrphanGuard guard{.actx = actx, .referenced = {}};
        if constexpr (JoinAction<T>) {
            if (actx.join.has_value()) {
                auto inputs = build_transform_inputs(actx, event);
                auto merged = action->join(std::move(inputs));
                bind_content_service(merged.content, actx.content_service);
                auto result = invoke_transform(*action, actx, event, merged);
                guard.referenced = collect_result_segments(result);
                return result_to_event(result, start);
            }
        }
        auto input = build_transform_input(actx, event);
        auto result = invoke_transform(*action, actx, event, input);
        guard.referenced = collect_result_segments(result);
        return result_to_event(result, start);
    };
}

template<typename T>
ActionExecutor make_egress_executor(std::shared_ptr<T> action) {
    return [action = std::move(action)](ActionContext& actx, QueueEvent& event) -> ActionEvent {
        auto start = actx.start_time;
        OrphanGuard guard{.actx = actx, .referenced = {}};
        auto input = build_egress_input(actx, event);
        auto result = invoke_egress(*action, actx, event, input);
        guard.referenced = collect_result_segments(result);
        return result_to_event(result, start);
    };
}

template<typename T>
ActionExecutor make_ingress_executor(std::shared_ptr<T> action) {
    return [action = std::move(action)](ActionContext& actx, QueueEvent& event) -> ActionEvent {
        auto start = actx.start_time;
        OrphanGuard guard{.actx = actx, .referenced = {}};
        auto result = invoke_ingress(*action, actx, event);
        guard.referenced = collect_result_segments(result);
        return result_to_event(result, start);
    };
}

// Detect if an action type declares an action_options() method.
template<typename T>
concept HasActionOptions = requires {
    { T::action_options() } -> std::convertible_to<ActionOptions>;
};

// Detect if an action type supports artifact configuration.
template<typename T>
concept ArtifactAware = requires(T t, ArtifactClient& client) {
    { t.configure_artifacts(client) };
};

// A registered action with type-erased executor.
struct RegisteredAction {
    std::string name;
    std::string description;
    std::string action_type;
    ActionExecutor executor;
    nlohmann::json schema = nlohmann::json::object();
    bool supports_join = false;
    std::optional<ActionOptions> options;
    std::function<void(ArtifactClient&)> artifact_configurator;
};

// Stores registered actions and creates type-erased executors.
class Registry {
public:
    // Register a TransformAction.
    template<TransformAction T>
    void register_action(const std::string& domain) {
        std::string name = qualified_name(domain, action_traits<T>::name);
        auto action = std::make_shared<T>();
        ActionExecutor executor = make_transform_executor(action);

        actions_.push_back(RegisteredAction{name, std::string(action_traits<T>::description),
                                            action_type_transform, std::move(executor),
                                            action_schema<T>(), JoinAction<T>, action_opts<T>(),
                                            artifact_config<T>(action)});
    }

    // Register an EgressAction.
    template<EgressAction T>
    void register_action(const std::string& domain) {
        std::string name = qualified_name(domain, action_traits<T>::name);
        auto action = std::make_shared<T>();
        ActionExecutor executor = make_egress_executor(action);

        actions_.push_back(RegisteredAction{name, std::string(action_traits<T>::description),
                                            action_type_egress, std::move(executor),
                                            action_schema<T>(), false, action_opts<T>(),
                                            artifact_config<T>(action)});
    }

    // Register a TimedIngressAction.
    template<TimedIngressAction T>
    void register_action(const std::string& domain) {
        std::string name = qualified_name(domain, action_traits<T>::name);
        auto action = std::make_shared<T>();
        ActionExecutor executor = make_ingress_executor(action);

        actions_.push_back(RegisteredAction{name, std::string(action_traits<T>::description),
                                            action_type_timed_ingress, std::move(executor),
                                            action_schema<T>(), false, action_opts<T>(),
                                            artifact_config<T>(action)});
    }

    const std::vector<RegisteredAction>& actions() const { return actions_; }

    // Call configure_artifacts on all ArtifactAware actions.
    void configure_artifacts(ArtifactClient& client) {
        for (const auto& ra : actions_) {
            if (ra.artifact_configurator) {
                ra.artifact_configurator(client);
            }
        }
    }

private:
    std::vector<RegisteredAction> actions_;

    template<typename T>
    static nlohmann::json action_schema() {
        if constexpr (HasParamsType<T>) {
            return T::Params::schema();
        } else {
            return nlohmann::json::object();
        }
    }

    template<typename T>
    static std::optional<ActionOptions> action_opts() {
        if constexpr (HasActionOptions<T>) {
            return T::action_options();
        } else {
            return std::nullopt;
        }
    }

    template<typename T>
    static std::function<void(ArtifactClient&)> artifact_config(std::shared_ptr<T> action) {
        if constexpr (ArtifactAware<T>) {
            return [action](ArtifactClient& client) { action->configure_artifacts(client); };
        } else {
            (void)action;
            return nullptr;
        }
    }

    static std::string qualified_name(const std::string& domain, std::string_view name) {
        if (domain.empty()) return std::string(name);
        return domain + "." + std::string(name);
    }
};

}  // namespace deltafi
