/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
// ABOUTME: Flow MCP tools for flow management
// ABOUTME: Provides tools for listing, creating, and controlling flows

package tools

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

// Seams over the generated GraphQL save functions so handlers can be tested
// without a live server.
var (
	saveTransformFlow     = graphql.SaveTransform
	saveRestDataSource    = graphql.SaveRestDataSource
	saveTimedDataSource   = graphql.SaveTimedDataSource
	saveOnErrorDataSource = graphql.SaveOnErrorDataSource
	saveDataSink          = graphql.SaveDataSink
	getSystemFlowPlans    = graphql.GetSystemFlowPlans
)

func init() {
	reg := registry.Get()

	// list_flows - List all flows
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_flows",
			"List all flows (transforms, data sources, data sinks) with their status",
		),
		Handler: listFlows,
		Tags:    []string{"flows"},
	})

	// get_flow - Get flow details
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_flow",
			"Get detailed information about a specific flow",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: getFlow,
		Tags:    []string{"flows"},
	})

	// get_flow_plan - Get a flow's plan (the input used to create it)
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_flow_plan",
			"Get the plan for a system flow: the original configuration with variable placeholders intact and no runtime state. Unlike get_flow, the result decodes directly back into load_flow. Only flows created via load_flow or the UI have plans; plugin-provided flows do not.",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: getFlowPlan,
		Tags:    []string{"flows"},
	})

	// start_flow - Start a flow
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("start_flow",
			"Start a stopped or paused flow",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: startFlow,
		Tags:    []string{"flows"},
	})

	// stop_flow - Stop a flow
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("stop_flow",
			"Stop a running flow",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: stopFlow,
		Tags:    []string{"flows"},
	})

	// pause_flow - Pause a flow
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("pause_flow",
			"Pause a running flow (stops accepting new data but completes in-flight)",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: pauseFlow,
		Tags:    []string{"flows"},
	})

	// delete_flow - Delete a flow
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("delete_flow",
			mcp.WithDescription("Delete a system flow by name and type. The flow must be stopped first. Only system flows (created via load_flow or the UI) can be deleted; plugin flows are removed by uninstalling or upgrading their plugin."),
			mcp.WithDestructiveHintAnnotation(true),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: deleteFlow,
		Tags:    []string{"flows"},
	})

	// list_topics - List all pub/sub topics
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_topics",
			"List all pub/sub topics and their publishers/subscribers",
		),
		Handler: listTopics,
		Tags:    []string{"flows"},
	})

	// get_action_descriptors - List available action types
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_action_descriptors",
			"List all available action types from installed plugins. Use get_action_schema for parameter details.",
		),
		Handler: getActionDescriptors,
		Tags:    []string{"flows"},
	})

	// get_action_schema - Get parameter schema for a specific action
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_action_schema",
			"Get the parameter schema and documentation for a specific action type",
			mcp.WithString("action", mcp.Required(), mcp.Description("Action name (e.g., org.deltafi.core.action.FilterAction)")),
		),
		Handler: getActionSchema,
		Tags:    []string{"flows"},
	})

	// enable_test_mode - Enable test mode on a flow
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("enable_test_mode",
			"Enable test mode on a flow (data is processed but not persisted)",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: enableTestMode,
		Tags:    []string{"flows"},
	})

	// disable_test_mode - Disable test mode
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("disable_test_mode",
			"Disable test mode on a flow",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: disableTestMode,
		Tags:    []string{"flows"},
	})

	// load_flow - Create or update a flow from JSON configuration
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("load_flow",
			"Create or update a flow from JSON configuration. If a flow with the same name exists, it will be replaced.",
			mcp.WithObject("config", mcp.Required(), mcp.Description(`Flow configuration object. Required fields:
- name: Flow name (string)
- type: Flow type (TRANSFORM, REST_DATA_SOURCE, TIMED_DATA_SOURCE, ON_ERROR_DATA_SOURCE, DATA_SINK)

For transforms, also include:
- subscribe: Array of {topic, condition?} objects
- transformActions: Array of {name, type, parameters} objects
- publish: Object with matchingPolicy, defaultRule, and rules array

Use get_flow_plan to see an example of a complete flow structure that decodes back into this tool.`)),
		),
		Handler: loadFlow,
		Tags:    []string{"flows"},
	})

	// validate_flow - Validate a flow configuration
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("validate_flow",
			"Validate a flow's configuration and return any errors including action parameter validation",
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: validateFlow,
		Tags:    []string{"flows"},
	})

	// export_system_plans - Export the full set of flow plans
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("export_system_plans",
			"Export all system flow plans as a single payload. The result is the unwrapped set of plans (dataSinkPlans, restDataSources, timedDataSources, onErrorDataSources, transformPlans) that import_system_plans consumes directly, so the export/import pair round-trips.",
		),
		Handler: exportSystemPlans,
		Tags:    []string{"flows"},
	})

	// import_system_plans - Bulk import the full set of flow plans
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("import_system_plans",
			"Bulk import a full set of flow plans. Useful for environment setup or migrating configuration between systems. Use export_system_plans to obtain a compatible payload.",
			mcp.WithObject("plans", mcp.Required(), mcp.Description(`Object with optional arrays for each plan type:
- dataSinkPlans: array of data sink plans
- restDataSources: array of REST data source plans
- timedDataSources: array of timed data source plans
- onErrorDataSources: array of on-error data source plans
- transformPlans: array of transform plans`)),
		),
		Handler: importSystemPlans,
		Tags:    []string{"flows"},
	})
}

func listFlows(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	transforms, err := graphql.ListTransforms()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	sources, err := graphql.ListDataSources()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	sinks, err := graphql.ListDataSinks()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type FlowInfo struct {
		Name     string `json:"name"`
		Type     string `json:"type"`
		State    string `json:"state"`
		Valid    bool   `json:"valid"`
		TestMode bool   `json:"test_mode"`
	}

	flows := make([]FlowInfo, 0)

	for _, t := range transforms.GetAllFlows.GetTransform() {
		flows = append(flows, FlowInfo{
			Name:     t.GetName(),
			Type:     "transform",
			State:    string(t.GetFlowStatus().State),
			Valid:    t.GetFlowStatus().Valid,
			TestMode: t.GetFlowStatus().TestMode,
		})
	}

	for _, ds := range sources.GetAllFlows.GetRestDataSource() {
		flows = append(flows, FlowInfo{
			Name:     ds.GetName(),
			Type:     "rest-data-source",
			State:    string(ds.GetFlowStatus().State),
			Valid:    ds.GetFlowStatus().Valid,
			TestMode: ds.GetFlowStatus().TestMode,
		})
	}

	for _, ds := range sources.GetAllFlows.GetTimedDataSource() {
		flows = append(flows, FlowInfo{
			Name:     ds.GetName(),
			Type:     "timed-data-source",
			State:    string(ds.GetFlowStatus().State),
			Valid:    ds.GetFlowStatus().Valid,
			TestMode: ds.GetFlowStatus().TestMode,
		})
	}

	for _, ds := range sources.GetAllFlows.GetOnErrorDataSource() {
		flows = append(flows, FlowInfo{
			Name:     ds.GetName(),
			Type:     "on-error-data-source",
			State:    string(ds.GetFlowStatus().State),
			Valid:    ds.GetFlowStatus().Valid,
			TestMode: ds.GetFlowStatus().TestMode,
		})
	}

	for _, sink := range sinks.GetAllFlows.GetDataSink() {
		flows = append(flows, FlowInfo{
			Name:     sink.GetName(),
			Type:     "data-sink",
			State:    string(sink.GetFlowStatus().State),
			Valid:    sink.GetFlowStatus().Valid,
			TestMode: sink.GetFlowStatus().TestMode,
		})
	}

	return registry.ResultFromJSON(flows)
}

func getFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowType, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	switch flowType {
	case "transform":
		resp, err := graphql.GetTransform(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		// TransformFlowFields is embedded with json:"-", access inner struct for proper serialization
		return registry.ResultFromJSON(resp.GetTransformFlow.TransformFlowFields)

	case "rest-data-source":
		resp, err := graphql.GetRestDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetRestDataSource)

	case "timed-data-source":
		resp, err := graphql.GetTimedDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetTimedDataSource)

	case "on-error-data-source":
		resp, err := graphql.GetOnErrorDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetOnErrorDataSource)

	case "data-sink":
		resp, err := graphql.GetDataSink(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetDataSink)

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s", flowType)), nil
	}
}

func getFlowPlan(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowType, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := getSystemFlowPlans()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	plans := resp.GetAllSystemFlowPlans

	switch flowType {
	case "transform":
		for _, plan := range plans.TransformPlans {
			if plan.Name == name {
				return registry.ResultFromJSON(plan)
			}
		}

	case "rest-data-source":
		for _, plan := range plans.RestDataSources {
			if plan.Name == name {
				return registry.ResultFromJSON(plan)
			}
		}

	case "timed-data-source":
		for _, plan := range plans.TimedDataSources {
			if plan.Name == name {
				return registry.ResultFromJSON(plan)
			}
		}

	case "on-error-data-source":
		for _, plan := range plans.OnErrorDataSources {
			if plan.Name == name {
				return registry.ResultFromJSON(plan)
			}
		}

	case "data-sink":
		for _, plan := range plans.DataSinkPlans {
			if plan.Name == name {
				return registry.ResultFromJSON(plan)
			}
		}

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s (valid: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink)", flowType)), nil
	}

	return registry.ErrorResult(fmt.Sprintf("no system flow plan named %q of type %s (only flows created via load_flow or the UI have plans; plugin-provided flows do not)", name, flowType)), nil
}

func exportSystemPlans(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := getSystemFlowPlans()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.GetAllSystemFlowPlans)
}

func startFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	return setFlowState(req, graphql.FlowStateRunning, "started")
}

func stopFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	return setFlowState(req, graphql.FlowStateStopped, "stopped")
}

func pauseFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	return setFlowState(req, graphql.FlowStatePaused, "paused")
}

func deleteFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowTypeStr, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	var removed bool
	switch flowTypeStr {
	case "transform":
		resp, err := graphql.DeleteTransform(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		removed = resp.RemoveTransformFlowPlan

	case "rest-data-source":
		resp, err := graphql.DeleteRestDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		removed = resp.RemoveRestDataSourcePlan

	case "timed-data-source":
		resp, err := graphql.DeleteTimedDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		removed = resp.RemoveTimedDataSourcePlan

	case "on-error-data-source":
		resp, err := graphql.DeleteOnErrorDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		removed = resp.RemoveOnErrorDataSourcePlan

	case "data-sink":
		resp, err := graphql.DeleteDataSink(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		removed = resp.RemoveDataSinkPlan

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s (valid: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink)", flowTypeStr)), nil
	}

	if !removed {
		return registry.ErrorResult(fmt.Sprintf("failed to delete %s %s: not found among system flows (plugin flows are removed by uninstalling or upgrading their plugin)", flowTypeStr, name)), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Successfully deleted %s %s", flowTypeStr, name)), nil
}

func setFlowState(req mcp.CallToolRequest, state graphql.FlowState, action string) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowTypeStr, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	flowType, err := parseFlowType(flowTypeStr)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	_, err = graphql.SetFlowState(flowType, name, state)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Successfully %s %s %s", action, flowTypeStr, name)), nil
}

func parseFlowType(s string) (graphql.FlowType, error) {
	switch s {
	case "transform":
		return graphql.FlowTypeTransform, nil
	case "rest-data-source":
		return graphql.FlowTypeRestDataSource, nil
	case "timed-data-source":
		return graphql.FlowTypeTimedDataSource, nil
	case "on-error-data-source":
		return graphql.FlowTypeOnErrorDataSource, nil
	case "data-sink":
		return graphql.FlowTypeDataSink, nil
	default:
		return "", fmt.Errorf("unknown flow type: %s (valid: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink)", s)
	}
}

func listTopics(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetAllTopics()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetAllTopics)
}

func getActionDescriptors(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetPluginDetails()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type ActionInfo struct {
		Name         string `json:"name"`
		Plugin       string `json:"plugin"`
		Type         string `json:"type"`
		Description  string `json:"description,omitempty"`
		SupportsJoin bool   `json:"supports_join"`
	}

	actions := make([]ActionInfo, 0)
	for _, plugin := range resp.Plugins {
		for _, action := range plugin.Actions {
			desc := ""
			if action.ActionOptions.Description != nil {
				desc = *action.ActionOptions.Description
			}
			actions = append(actions, ActionInfo{
				Name:         action.Name,
				Plugin:       plugin.DisplayName,
				Type:         string(action.Type),
				Description:  desc,
				SupportsJoin: action.SupportsJoin,
			})
		}
	}

	return registry.ResultFromJSON(actions)
}

func getActionSchema(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	actionName, errResult := requireNonEmptyString(req, "action")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.GetPluginDetails()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	for _, plugin := range resp.Plugins {
		for _, action := range plugin.Actions {
			if action.Name == actionName {
				type ActionSchema struct {
					Name         string          `json:"name"`
					Plugin       string          `json:"plugin"`
					Type         string          `json:"type"`
					Description  string          `json:"description,omitempty"`
					SupportsJoin bool            `json:"supports_join"`
					Schema       *map[string]any `json:"schema,omitempty"`
					DocsMarkdown *string         `json:"docs_markdown,omitempty"`
				}
				desc := ""
				if action.ActionOptions.Description != nil {
					desc = *action.ActionOptions.Description
				}
				return registry.ResultFromJSON(ActionSchema{
					Name:         action.Name,
					Plugin:       plugin.DisplayName,
					Type:         string(action.Type),
					Description:  desc,
					SupportsJoin: action.SupportsJoin,
					Schema:       action.Schema,
					DocsMarkdown: action.DocsMarkdown,
				})
			}
		}
	}

	return registry.ErrorResult(fmt.Sprintf("action %s not found", actionName)), nil
}

func enableTestMode(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowTypeStr, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	var err error
	switch flowTypeStr {
	case "transform":
		_, err = graphql.EnableTransformTestMode(name)
	case "rest-data-source":
		_, err = graphql.EnableRestDataSourceTestMode(name)
	case "timed-data-source":
		_, err = graphql.EnableTimedDataSourceTestMode(name)
	case "on-error-data-source":
		_, err = graphql.EnableOnErrorDataSourceTestMode(name)
	case "data-sink":
		_, err = graphql.EnableDataSinkTestMode(name)
	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s", flowTypeStr)), nil
	}

	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Test mode enabled for %s %s", flowTypeStr, name)), nil
}

func disableTestMode(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowTypeStr, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	var err error
	switch flowTypeStr {
	case "transform":
		_, err = graphql.DisableTransformTestMode(name)
	case "rest-data-source":
		_, err = graphql.DisableRestDataSourceTestMode(name)
	case "timed-data-source":
		_, err = graphql.DisableTimedDataSourceTestMode(name)
	case "on-error-data-source":
		_, err = graphql.DisableOnErrorDataSourceTestMode(name)
	case "data-sink":
		_, err = graphql.DisableDataSinkTestMode(name)
	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s", flowTypeStr)), nil
	}

	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Test mode disabled for %s %s", flowTypeStr, name)), nil
}

func loadFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	configRaw, ok := args["config"]
	if !ok {
		return registry.ErrorResult("config parameter is required"), nil
	}

	// Convert config to JSON bytes for unmarshaling into the appropriate type
	configBytes, err := json.Marshal(configRaw)
	if err != nil {
		return registry.ErrorResult(fmt.Sprintf("invalid config: %v", err)), nil
	}

	// Extract the type field from the config
	var typeExtractor struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(configBytes, &typeExtractor); err != nil {
		return registry.ErrorResult(fmt.Sprintf("invalid config: %v", err)), nil
	}
	if typeExtractor.Type == "" {
		return registry.ErrorResult("config must include 'type' field (TRANSFORM, REST_DATA_SOURCE, TIMED_DATA_SOURCE, ON_ERROR_DATA_SOURCE, DATA_SINK)"), nil
	}

	switch typeExtractor.Type {
	case "TRANSFORM":
		var input graphql.TransformFlowPlanInput
		if err := decodeJSON(configRaw, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid transform config: %v", err)), nil
		}
		resp, err := saveTransformFlow(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveTransformFlowPlan)

	case "REST_DATA_SOURCE":
		var input graphql.RestDataSourcePlanInput
		if err := decodeJSON(configRaw, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid REST_DATA_SOURCE config: %v", err)), nil
		}
		resp, err := saveRestDataSource(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveRestDataSourcePlan)

	case "TIMED_DATA_SOURCE":
		var input graphql.TimedDataSourcePlanInput
		if err := decodeJSON(configRaw, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid TIMED_DATA_SOURCE config: %v", err)), nil
		}
		resp, err := saveTimedDataSource(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveTimedDataSourcePlan)

	case "ON_ERROR_DATA_SOURCE":
		var input graphql.OnErrorDataSourcePlanInput
		if err := decodeJSON(configRaw, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid ON_ERROR_DATA_SOURCE config: %v", err)), nil
		}
		resp, err := saveOnErrorDataSource(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveOnErrorDataSourcePlan)

	case "DATA_SINK":
		var input graphql.DataSinkPlanInput
		if err := decodeJSON(configRaw, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid DATA_SINK config: %v", err)), nil
		}
		resp, err := saveDataSink(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveDataSinkPlan)

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s (valid: TRANSFORM, REST_DATA_SOURCE, TIMED_DATA_SOURCE, ON_ERROR_DATA_SOURCE, DATA_SINK)", typeExtractor.Type)), nil
	}
}

func validateFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	flowType, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	switch flowType {
	case "transform":
		resp, err := graphql.ValidateTransform(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		// TransformFlowFields is embedded with json:"-", so access the inner struct
		return registry.ResultFromJSON(resp.ValidateTransformFlow.TransformFlowFields)

	case "rest-data-source":
		resp, err := graphql.ValidateRestDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.ValidateRestDataSource)

	case "timed-data-source":
		resp, err := graphql.ValidateTimedDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.ValidateTimedDataSource)

	case "on-error-data-source":
		resp, err := graphql.ValidateOnErrorDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.ValidateOnErrorDataSource)

	case "data-sink":
		resp, err := graphql.ValidateDataSink(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.ValidateDataSink)

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s (valid: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink)", flowType)), nil
	}
}

func importSystemPlans(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["plans"]
	if !ok {
		return registry.ErrorResult("plans parameter is required"), nil
	}
	if _, ok := raw.(map[string]any); !ok {
		return registry.ErrorResult("plans must be a JSON object"), nil
	}

	var plans graphql.SystemFlowPlansInput
	if err := decodeJSON(raw, &plans); err != nil {
		return registry.ErrorResult(fmt.Sprintf("invalid plans payload: %v", err)), nil
	}

	total := len(plans.DataSinkPlans) + len(plans.RestDataSources) + len(plans.TimedDataSources) + len(plans.OnErrorDataSources) + len(plans.TransformPlans)
	if total == 0 {
		return registry.ErrorResult("plans must include at least one plan in dataSinkPlans, restDataSources, timedDataSources, onErrorDataSources, or transformPlans"), nil
	}

	resp, err := graphql.ImportSystemPlans(plans)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.SaveSystemFlowPlans {
		return registry.ErrorResult("import was rejected by the server"), nil
	}
	return mcp.NewToolResultText(fmt.Sprintf("imported %d flow plans", total)), nil
}
