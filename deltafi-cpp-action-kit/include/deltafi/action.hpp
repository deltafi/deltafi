// ABOUTME: Action interfaces and type detection for DeltaFi actions.
// ABOUTME: Defines TransformAction, EgressAction, TimedIngressAction concepts.

#pragma once

#include <concepts>
#include <string_view>

#include "deltafi/input.hpp"
#include "deltafi/result.hpp"

namespace deltafi {

// Action type constants used in registration and event payloads.
inline constexpr const char* action_type_transform = "TRANSFORM";
inline constexpr const char* action_type_egress = "EGRESS";
inline constexpr const char* action_type_timed_ingress = "TIMED_INGRESS";

// Traits template specialized by DELTAFI_ACTION to provide name and description.
template<typename T>
struct action_traits;

// Detect if an action type declares a Params type with a schema() method.
template<typename T>
concept HasParamsType = requires {
    typename T::Params;
    { T::Params::schema() } -> std::convertible_to<nlohmann::json>;
};

// Transform action without parameters: transform(context, input)
template<typename T>
concept TransformActionNoParams =
    !HasParamsType<T> && requires(T t, ActionContext& ctx, TransformInput& input) {
        { t.transform(ctx, input) } -> std::same_as<TransformResultType>;
        { action_traits<T>::name } -> std::convertible_to<std::string_view>;
        { action_traits<T>::description } -> std::convertible_to<std::string_view>;
    };

// Transform action with parameters: transform(context, params, input)
template<typename T>
concept TransformActionWithParams =
    HasParamsType<T> && requires(T t, ActionContext& ctx, T::Params& p, TransformInput& input) {
        { t.transform(ctx, p, input) } -> std::same_as<TransformResultType>;
        { action_traits<T>::name } -> std::convertible_to<std::string_view>;
        { action_traits<T>::description } -> std::convertible_to<std::string_view>;
    };

// A TransformAction processes content and produces transformed output.
template<typename T>
concept TransformAction = TransformActionNoParams<T> || TransformActionWithParams<T>;

// Egress action without parameters: egress(context, input)
template<typename T>
concept EgressActionNoParams =
    !HasParamsType<T> && requires(T t, ActionContext& ctx, EgressInput& input) {
        { t.egress(ctx, input) } -> std::same_as<EgressResultType>;
        { action_traits<T>::name } -> std::convertible_to<std::string_view>;
        { action_traits<T>::description } -> std::convertible_to<std::string_view>;
    };

// Egress action with parameters: egress(context, params, input)
template<typename T>
concept EgressActionWithParams =
    HasParamsType<T> && requires(T t, ActionContext& ctx, T::Params& p, EgressInput& input) {
        { t.egress(ctx, p, input) } -> std::same_as<EgressResultType>;
        { action_traits<T>::name } -> std::convertible_to<std::string_view>;
        { action_traits<T>::description } -> std::convertible_to<std::string_view>;
    };

// An EgressAction sends content to an external destination.
template<typename T>
concept EgressAction = EgressActionNoParams<T> || EgressActionWithParams<T>;

// Timed ingress action without parameters: ingress(context)
template<typename T>
concept TimedIngressActionNoParams = !HasParamsType<T> && requires(T t, ActionContext& ctx) {
    { t.ingress(ctx) } -> std::same_as<IngressResultType>;
    { action_traits<T>::name } -> std::convertible_to<std::string_view>;
    { action_traits<T>::description } -> std::convertible_to<std::string_view>;
};

// Timed ingress action with parameters: ingress(context, params)
template<typename T>
concept TimedIngressActionWithParams =
    HasParamsType<T> && requires(T t, ActionContext& ctx, T::Params& p) {
        { t.ingress(ctx, p) } -> std::same_as<IngressResultType>;
        { action_traits<T>::name } -> std::convertible_to<std::string_view>;
        { action_traits<T>::description } -> std::convertible_to<std::string_view>;
    };

// A TimedIngressAction creates new DeltaFiles on a schedule.
template<typename T>
concept TimedIngressAction = TimedIngressActionNoParams<T> || TimedIngressActionWithParams<T>;

// A JoinAction merges multiple TransformInputs before transformation.
template<typename T>
concept JoinAction = TransformAction<T> && requires(T t, std::vector<TransformInput> inputs) {
    { t.join(std::move(inputs)) } -> std::same_as<TransformInput>;
};

// Merge multiple TransformInputs by concatenating content and combining metadata.
inline TransformInput default_join(std::vector<TransformInput> inputs) {
    std::vector<ActionContent> all_content;
    std::map<std::string, std::string> all_metadata;
    for (auto& input : inputs) {
        all_content.insert(all_content.end(), std::make_move_iterator(input.content.begin()),
                           std::make_move_iterator(input.content.end()));
        merge_last_wins(all_metadata, input.metadata);
    }
    return TransformInput{.content = std::move(all_content), .metadata = std::move(all_metadata)};
}

}  // namespace deltafi
