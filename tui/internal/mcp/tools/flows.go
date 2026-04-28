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

func init() {
	reg := registry.Get()

	// list_flows - List all flows
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_flows",
			mcp.WithDescription("List all flows (transforms, data sources, data sinks) with their status"),
		),
		Handler: listFlows,
		Tags:    []string{"flows"},
	})

	// get_flow - Get flow details
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_flow",
			mcp.WithDescription("Get detailed information about a specific flow"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: getFlow,
		Tags:    []string{"flows"},
	})

	// start_flow - Start a flow
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("start_flow",
			mcp.WithDescription("Start a stopped or paused flow"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: startFlow,
		Tags:    []string{"flows"},
	})

	// stop_flow - Stop a flow
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("stop_flow",
			mcp.WithDescription("Stop a running flow"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: stopFlow,
		Tags:    []string{"flows"},
	})

	// pause_flow - Pause a flow
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("pause_flow",
			mcp.WithDescription("Pause a running flow (stops accepting new data but completes in-flight)"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: pauseFlow,
		Tags:    []string{"flows"},
	})

	// list_topics - List all pub/sub topics
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_topics",
			mcp.WithDescription("List all pub/sub topics and their publishers/subscribers"),
		),
		Handler: listTopics,
		Tags:    []string{"flows"},
	})

	// get_action_descriptors - List available action types
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_action_descriptors",
			mcp.WithDescription("List all available action types from installed plugins. Use get_action_schema for parameter details."),
		),
		Handler: getActionDescriptors,
		Tags:    []string{"flows"},
	})

	// get_action_schema - Get parameter schema for a specific action
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_action_schema",
			mcp.WithDescription("Get the parameter schema and documentation for a specific action type"),
			mcp.WithString("action", mcp.Required(), mcp.Description("Action name (e.g., org.deltafi.core.action.FilterAction)")),
		),
		Handler: getActionSchema,
		Tags:    []string{"flows"},
	})

	// enable_test_mode - Enable test mode on a flow
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("enable_test_mode",
			mcp.WithDescription("Enable test mode on a flow (data is processed but not persisted)"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: enableTestMode,
		Tags:    []string{"flows"},
	})

	// disable_test_mode - Disable test mode
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("disable_test_mode",
			mcp.WithDescription("Disable test mode on a flow"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: disableTestMode,
		Tags:    []string{"flows"},
	})

	// load_flow - Create or update a flow from JSON configuration
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("load_flow",
			mcp.WithDescription("Create or update a flow from JSON configuration. If a flow with the same name exists, it will be replaced."),
			mcp.WithObject("config", mcp.Required(), mcp.Description(`Flow configuration object. Required fields:
- name: Flow name (string)
- type: Flow type (TRANSFORM, REST_DATA_SOURCE, TIMED_DATA_SOURCE, ON_ERROR_DATA_SOURCE, DATA_SINK)

For transforms, also include:
- subscribe: Array of {topic, condition?} objects
- transformActions: Array of {name, type, parameters} objects
- publish: Object with matchingPolicy, defaultRule, and rules array

Use get_flow to see an example of a complete flow structure.`)),
		),
		Handler: loadFlow,
		Tags:    []string{"flows"},
	})

	// validate_flow - Validate a flow configuration
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("validate_flow",
			mcp.WithDescription("Validate a flow's configuration and return any errors including action parameter validation"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Flow name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Flow type: transform, rest-data-source, timed-data-source, on-error-data-source, data-sink")),
		),
		Handler: validateFlow,
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
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	flowType, err := req.RequireString("type")
	if err != nil {
		return registry.ErrorResult("type parameter is required"), nil
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

func startFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	return setFlowState(req, graphql.FlowStateRunning, "started")
}

func stopFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	return setFlowState(req, graphql.FlowStateStopped, "stopped")
}

func pauseFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	return setFlowState(req, graphql.FlowStatePaused, "paused")
}

func setFlowState(req mcp.CallToolRequest, state graphql.FlowState, action string) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	flowTypeStr, err := req.RequireString("type")
	if err != nil {
		return registry.ErrorResult("type parameter is required"), nil
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
	actionName, err := req.RequireString("action")
	if err != nil {
		return registry.ErrorResult("action parameter is required"), nil
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
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	flowTypeStr, err := req.RequireString("type")
	if err != nil {
		return registry.ErrorResult("type parameter is required"), nil
	}

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
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	flowTypeStr, err := req.RequireString("type")
	if err != nil {
		return registry.ErrorResult("type parameter is required"), nil
	}

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
		if err := json.Unmarshal(configBytes, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid transform config: %v", err)), nil
		}
		resp, err := graphql.SaveTransform(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveTransformFlowPlan)

	case "REST_DATA_SOURCE":
		var input graphql.RestDataSourcePlanInput
		if err := json.Unmarshal(configBytes, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid REST_DATA_SOURCE config: %v", err)), nil
		}
		resp, err := graphql.SaveRestDataSource(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveRestDataSourcePlan)

	case "TIMED_DATA_SOURCE":
		var input graphql.TimedDataSourcePlanInput
		if err := json.Unmarshal(configBytes, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid TIMED_DATA_SOURCE config: %v", err)), nil
		}
		resp, err := graphql.SaveTimedDataSource(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveTimedDataSourcePlan)

	case "ON_ERROR_DATA_SOURCE":
		var input graphql.OnErrorDataSourcePlanInput
		if err := json.Unmarshal(configBytes, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid ON_ERROR_DATA_SOURCE config: %v", err)), nil
		}
		resp, err := graphql.SaveOnErrorDataSource(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveOnErrorDataSourcePlan)

	case "DATA_SINK":
		var input graphql.DataSinkPlanInput
		if err := json.Unmarshal(configBytes, &input); err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid DATA_SINK config: %v", err)), nil
		}
		resp, err := graphql.SaveDataSink(input)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.SaveDataSinkPlan)

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown flow type: %s (valid: TRANSFORM, REST_DATA_SOURCE, TIMED_DATA_SOURCE, ON_ERROR_DATA_SOURCE, DATA_SINK)", typeExtractor.Type)), nil
	}
}

func validateFlow(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	flowType, err := req.RequireString("type")
	if err != nil {
		return registry.ErrorResult("type parameter is required"), nil
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
