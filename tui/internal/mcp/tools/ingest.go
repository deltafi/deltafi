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
// ABOUTME: Ingest MCP tools for data source management
// ABOUTME: Provides tools for listing, viewing, and managing data sources

package tools

import (
	"context"
	"fmt"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// list_data_sources - List all data sources
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_data_sources",
			"List all data sources (REST, timed, and on-error) with their status",
		),
		Handler: listDataSources,
		Tags:    []string{"ingest"},
	})

	// get_data_source - Get data source details
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_data_source",
			"Get detailed information about a specific data source",
			mcp.WithString("name", mcp.Required(), mcp.Description("Data source name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Data source type: rest, timed, on-error")),
		),
		Handler: getDataSource,
		Tags:    []string{"ingest"},
	})

	// get_timed_source_status - Get timed source schedule and status
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_timed_source_status",
			"Get schedule, last/next run, and ingress status for a timed data source",
			mcp.WithString("name", mcp.Required(), mcp.Description("Timed data source name")),
		),
		Handler: getTimedSourceStatus,
		Tags:    []string{"ingest"},
	})

	// delete_data_source - Delete a data source
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("delete_data_source",
			"Delete a data source by name and type",
			mcp.WithString("name", mcp.Required(), mcp.Description("Data source name")),
			mcp.WithString("type", mcp.Required(), mcp.Description("Data source type: rest, timed, on-error")),
		),
		Handler: deleteDataSource,
		Tags:    []string{"ingest"},
	})

	// set_timed_source_schedule - Update cron schedule for a timed data source
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("set_timed_source_schedule",
			"Update the cron schedule for a timed data source",
			mcp.WithString("name", mcp.Required(), mcp.Description("Timed data source name")),
			mcp.WithString("cron_schedule", mcp.Required(), mcp.Description("Cron expression (e.g., '0 */5 * * * *' for every 5 minutes)")),
		),
		Handler: setTimedSourceSchedule,
		Tags:    []string{"ingest"},
	})
}

func listDataSources(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.ListDataSources()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type DataSourceInfo struct {
		Name     string `json:"name"`
		Type     string `json:"type"`
		State    string `json:"state"`
		Valid    bool   `json:"valid"`
		TestMode bool   `json:"test_mode"`
		Topic    string `json:"topic"`
	}

	sources := make([]DataSourceInfo, 0)

	for _, ds := range resp.GetAllFlows.GetRestDataSource() {
		sources = append(sources, DataSourceInfo{
			Name:     ds.GetName(),
			Type:     "rest",
			State:    string(ds.GetFlowStatus().State),
			Valid:    ds.GetFlowStatus().Valid,
			TestMode: ds.GetFlowStatus().TestMode,
			Topic:    ds.GetTopic(),
		})
	}

	for _, ds := range resp.GetAllFlows.GetTimedDataSource() {
		sources = append(sources, DataSourceInfo{
			Name:     ds.GetName(),
			Type:     "timed",
			State:    string(ds.GetFlowStatus().State),
			Valid:    ds.GetFlowStatus().Valid,
			TestMode: ds.GetFlowStatus().TestMode,
			Topic:    ds.GetTopic(),
		})
	}

	for _, ds := range resp.GetAllFlows.GetOnErrorDataSource() {
		sources = append(sources, DataSourceInfo{
			Name:     ds.GetName(),
			Type:     "on-error",
			State:    string(ds.GetFlowStatus().State),
			Valid:    ds.GetFlowStatus().Valid,
			TestMode: ds.GetFlowStatus().TestMode,
			Topic:    ds.GetTopic(),
		})
	}

	return registry.ResultFromJSON(sources)
}

func getDataSource(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	dsType, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	switch dsType {
	case "rest":
		resp, err := graphql.GetRestDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetRestDataSource)

	case "timed":
		resp, err := graphql.GetTimedDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetTimedDataSource)

	case "on-error":
		resp, err := graphql.GetOnErrorDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.GetOnErrorDataSource)

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown data source type: %s (valid: rest, timed, on-error)", dsType)), nil
	}
}

func getTimedSourceStatus(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.GetTimedDataSource(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	ds := resp.GetTimedDataSource

	type TimedSourceStatus struct {
		Name                 string  `json:"name"`
		State                string  `json:"state"`
		CronSchedule         *string `json:"cron_schedule,omitempty"`
		LastRun              *string `json:"last_run,omitempty"`
		NextRun              *string `json:"next_run,omitempty"`
		Memo                 *string `json:"memo,omitempty"`
		MaxErrors            int     `json:"max_errors"`
		IngressStatus        *string `json:"ingress_status,omitempty"`
		IngressStatusMessage *string `json:"ingress_status_message,omitempty"`
		ExecuteImmediate     *bool   `json:"execute_immediate,omitempty"`
	}

	status := TimedSourceStatus{
		Name:             ds.Name,
		State:            string(ds.FlowStatus.State),
		CronSchedule:     ds.CronSchedule,
		MaxErrors:        ds.MaxErrors,
		Memo:             ds.Memo,
		ExecuteImmediate: ds.ExecuteImmediate,
	}

	if ds.LastRun != nil {
		lastRun := ds.LastRun.Format("2006-01-02T15:04:05Z07:00")
		status.LastRun = &lastRun
	}
	if ds.NextRun != nil {
		nextRun := ds.NextRun.Format("2006-01-02T15:04:05Z07:00")
		status.NextRun = &nextRun
	}
	if ds.IngressStatus != nil {
		ingressStatus := string(*ds.IngressStatus)
		status.IngressStatus = &ingressStatus
	}
	status.IngressStatusMessage = ds.IngressStatusMessage

	return registry.ResultFromJSON(status)
}

func deleteDataSource(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	dsType, errResult := requireNonEmptyString(req, "type")
	if errResult != nil {
		return errResult, nil
	}

	switch dsType {
	case "rest":
		resp, err := graphql.DeleteRestDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		if resp.RemoveRestDataSourcePlan {
			return mcp.NewToolResultText(fmt.Sprintf("REST data source %s deleted", name)), nil
		}
		return registry.ErrorResult(fmt.Sprintf("failed to delete REST data source %s", name)), nil

	case "timed":
		resp, err := graphql.DeleteTimedDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		if resp.RemoveTimedDataSourcePlan {
			return mcp.NewToolResultText(fmt.Sprintf("timed data source %s deleted", name)), nil
		}
		return registry.ErrorResult(fmt.Sprintf("failed to delete timed data source %s", name)), nil

	case "on-error":
		resp, err := graphql.DeleteOnErrorDataSource(name)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		if resp.RemoveOnErrorDataSourcePlan {
			return mcp.NewToolResultText(fmt.Sprintf("on-error data source %s deleted", name)), nil
		}
		return registry.ErrorResult(fmt.Sprintf("failed to delete on-error data source %s", name)), nil

	default:
		return registry.ErrorResult(fmt.Sprintf("unknown data source type: %s (valid: rest, timed, on-error)", dsType)), nil
	}
}

func setTimedSourceSchedule(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	cronSchedule, errResult := requireNonEmptyString(req, "cron_schedule")
	if errResult != nil {
		return errResult, nil
	}

	// Fetch the existing plan
	planResp, err := graphql.GetTimedDataSourcePlan(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	plan := planResp.GetTimedDataSourcePlan

	// Convert annotation config if present
	var annotationConfig *graphql.AnnotationConfigInput
	if plan.AnnotationConfig != nil {
		annotationConfig = &graphql.AnnotationConfigInput{
			Annotations:      plan.AnnotationConfig.Annotations,
			MetadataPatterns: plan.AnnotationConfig.MetadataPatterns,
			DiscardPrefix:    plan.AnnotationConfig.DiscardPrefix,
		}
	}

	// Build the input with updated cron schedule
	input := graphql.TimedDataSourcePlanInput{
		Name:        plan.Name,
		Type:        plan.Type,
		Description: plan.Description,
		Metadata:    plan.Metadata,
		Topic:       plan.Topic,
		TimedIngressAction: graphql.ActionConfigurationInput{
			Name:       plan.TimedIngressAction.Name,
			Type:       plan.TimedIngressAction.Type,
			Parameters: plan.TimedIngressAction.Parameters,
		},
		AnnotationConfig: annotationConfig,
		CronSchedule:     &cronSchedule,
	}

	// Save with updated schedule
	_, err = graphql.SaveTimedDataSource(input)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Cron schedule for %s updated to: %s", name, cronSchedule)), nil
}
