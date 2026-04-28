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
// ABOUTME: Metrics MCP tools for system health and statistics
// ABOUTME: Provides tools for querying system status, queue depths, and flow metrics

package tools

import (
	"context"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// get_system_status - Overall system health
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_system_status",
			mcp.WithDescription("Get overall system health status including service checks, version, and state"),
		),
		Handler: getSystemStatus,
		Tags:    []string{"metrics"},
	})

	// get_version - DeltaFi version info
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_version",
			mcp.WithDescription("Get DeltaFi version information"),
		),
		Handler: getVersion,
		Tags:    []string{"metrics"},
	})

	// get_deltafile_stats - Processing statistics
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_deltafile_stats",
			mcp.WithDescription("Get DeltaFile processing statistics including counts by state"),
		),
		Handler: getDeltaFileStats,
		Tags:    []string{"metrics"},
	})

	// list_running_flows - Currently active flows
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_running_flows",
			mcp.WithDescription("List all currently running flows (transforms, data sources, data sinks)"),
		),
		Handler: listRunningFlows,
		Tags:    []string{"metrics"},
	})

	// get_node_metrics - CPU/memory/disk per node
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_node_metrics",
			mcp.WithDescription("Get resource metrics (CPU, memory, disk) for system nodes"),
		),
		Handler: getNodeMetrics,
		Tags:    []string{"metrics"},
	})
}

func getSystemStatus(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var result api.StatusResponse
	err = client.Get("/api/v2/status", &result, nil)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(result)
}

func getVersion(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var result map[string]any
	err = client.Get("/api/v2/versions", &result, nil)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(result)
}

func getDeltaFileStats(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetDeltaFileStats()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.DeltaFileStats)
}

func listRunningFlows(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	// Get all flow types and filter to running ones
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

	type RunningFlows struct {
		Transforms  []string `json:"transforms"`
		DataSources []string `json:"data_sources"`
		DataSinks   []string `json:"data_sinks"`
	}

	result := RunningFlows{
		Transforms:  make([]string, 0),
		DataSources: make([]string, 0),
		DataSinks:   make([]string, 0),
	}

	for _, t := range transforms.GetAllFlows.GetTransform() {
		if t.GetFlowStatus().State == graphql.FlowStateRunning {
			result.Transforms = append(result.Transforms, t.GetName())
		}
	}

	for _, ds := range sources.GetAllFlows.GetRestDataSource() {
		if ds.GetFlowStatus().State == graphql.FlowStateRunning {
			result.DataSources = append(result.DataSources, ds.GetName())
		}
	}
	for _, ds := range sources.GetAllFlows.GetTimedDataSource() {
		if ds.GetFlowStatus().State == graphql.FlowStateRunning {
			result.DataSources = append(result.DataSources, ds.GetName())
		}
	}
	for _, ds := range sources.GetAllFlows.GetOnErrorDataSource() {
		if ds.GetFlowStatus().State == graphql.FlowStateRunning {
			result.DataSources = append(result.DataSources, ds.GetName())
		}
	}

	for _, sink := range sinks.GetAllFlows.GetDataSink() {
		if sink.GetFlowStatus().State == graphql.FlowStateRunning {
			result.DataSinks = append(result.DataSinks, sink.GetName())
		}
	}

	return registry.ResultFromJSON(result)
}

func getNodeMetrics(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var result any
	err = client.Get("/api/v2/metrics/system/nodes", &result, nil)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(result)
}
