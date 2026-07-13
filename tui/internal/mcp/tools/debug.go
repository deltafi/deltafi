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
// ABOUTME: Debug MCP tools for DeltaFile inspection and tracing
// ABOUTME: Provides tools for querying, searching, and debugging DeltaFiles

package tools

import (
	"context"
	"fmt"
	"time"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// get_deltafile - Get DeltaFile by ID
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_deltafile",
			"Get detailed information about a DeltaFile by its DID (DeltaFile ID), including flows, actions, and full provenance",
			mcp.WithString("did", mcp.Required(), mcp.Description("DeltaFile ID (UUID)")),
		),
		Handler: getDeltaFile,
		Tags:    []string{"debug"},
	})

	// search_deltafiles - Search DeltaFiles with filters
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("search_deltafiles",
			"Search for DeltaFiles with various filters. Returns summary info - use get_deltafile for full details.",
			mcp.WithString("name", mcp.Description("Filter by filename pattern (case-insensitive)")),
			mcp.WithString("data_source", mcp.Description("Filter by data source name")),
			mcp.WithString("stage", mcp.Description("Filter by stage: IN_FLIGHT, COMPLETE, ERROR, CANCELLED")),
			mcp.WithString("modified_after", mcp.Description("Filter by modified time (ISO8601 or relative like '1h', '24h', '7d')")),
			mcp.WithString("modified_before", mcp.Description("Filter by modified time")),
			mcp.WithInteger("limit", mcp.Min(0), mcp.Max(500), mcp.Description("Maximum results to return (default 50, max 500)")),
		),
		Handler: searchDeltaFiles,
		Tags:    []string{"debug"},
	})

	// get_error_summary - Error summary by flow or message
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_error_summary",
			"Get a summary of errors grouped by flow or error message",
			mcp.WithString("group_by", mcp.Description("Group by: flow (default) or message")),
			mcp.WithString("modified_after", mcp.Description("Filter by modified time (ISO8601 or relative like '1h', '24h', '7d')")),
		),
		Handler: getErrorSummary,
		Tags:    []string{"debug"},
	})

	// count_errors - Count unacknowledged errors
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("count_errors",
			"Count total unacknowledged errors in the system",
		),
		Handler: countErrors,
		Tags:    []string{"debug"},
	})

	// get_annotation_keys - Available annotation keys
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_annotation_keys",
			"List all annotation keys that have been used across DeltaFiles",
		),
		Handler: getAnnotationKeys,
		Tags:    []string{"debug"},
	})

	// export_config - Export system config as YAML
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("export_config",
			"Export the current system configuration as YAML",
		),
		Handler: exportConfig,
		Tags:    []string{"debug"},
	})
}

func getDeltaFile(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	did, errResult := requireUUID(req, "did", "did")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.DeltaFile(did)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	if resp == nil || resp.DeltaFile == nil {
		return registry.ErrorResult(fmt.Sprintf("no DeltaFile found with ID: %s", did)), nil
	}

	return registry.ResultFromJSON(resp.DeltaFile)
}

func searchDeltaFiles(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	filter := &graphql.DeltaFilesFilter{}

	// Name filter
	if name, err := req.RequireString("name"); err == nil && name != "" {
		falseVal := false
		filter.NameFilter = &graphql.NameFilter{
			Name:          name,
			CaseSensitive: &falseVal,
		}
	}

	// Data source filter
	if ds, err := req.RequireString("data_source"); err == nil && ds != "" {
		filter.DataSources = []string{ds}
	}

	// Stage filter
	if stageStr, err := req.RequireString("stage"); err == nil && stageStr != "" {
		stage := graphql.DeltaFileStage(stageStr)
		filter.Stage = &stage
	}

	// Modified after filter
	if after, err := req.RequireString("modified_after"); err == nil && after != "" {
		t, err := parseTime(after)
		if err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid modified_after: %v", err)), nil
		}
		filter.ModifiedAfter = &t
	}

	// Modified before filter
	if before, err := req.RequireString("modified_before"); err == nil && before != "" {
		t, err := parseTime(before)
		if err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid modified_before: %v", err)), nil
		}
		filter.ModifiedBefore = &t
	}

	// Limit
	limit := 50
	if l, err := optionalNonNegativeInt(req, "limit"); err != nil {
		return registry.ErrorResult(err.Error()), nil
	} else if l != nil {
		limit = min(*l, 500)
	}
	offset := 0

	// Default sort by modified descending
	order := &graphql.DeltaFileOrder{
		Direction: graphql.DeltaFileDirectionDesc,
		Field:     "modified",
	}

	resp, err := graphql.DeltaFiles(&offset, &limit, filter, order)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	// Return summary info
	type DeltaFileSummary struct {
		DID        string `json:"did"`
		Name       string `json:"name"`
		DataSource string `json:"data_source"`
		Stage      string `json:"stage"`
		Modified   string `json:"modified"`
	}

	results := make([]DeltaFileSummary, 0, len(resp.DeltaFiles.DeltaFiles))
	for _, df := range resp.DeltaFiles.DeltaFiles {
		name := ""
		if df.Name != nil {
			name = *df.Name
		}
		results = append(results, DeltaFileSummary{
			DID:        df.Did.String(),
			Name:       name,
			DataSource: df.DataSource,
			Stage:      string(df.Stage),
			Modified:   df.Modified.Format(time.RFC3339),
		})
	}

	type SearchResult struct {
		TotalCount int                `json:"total_count"`
		Returned   int                `json:"returned"`
		DeltaFiles []DeltaFileSummary `json:"deltafiles"`
	}

	totalCount := 0
	if resp.DeltaFiles.TotalCount != nil {
		totalCount = *resp.DeltaFiles.TotalCount
	}

	return registry.ResultFromJSON(SearchResult{
		TotalCount: totalCount,
		Returned:   len(results),
		DeltaFiles: results,
	})
}

func getErrorSummary(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	filter := &graphql.DeltaFilesFilter{}

	// Modified after filter
	if after, err := req.RequireString("modified_after"); err == nil && after != "" {
		t, err := parseTime(after)
		if err != nil {
			return registry.ErrorResult(fmt.Sprintf("invalid modified_after: %v", err)), nil
		}
		filter.ModifiedAfter = &t
	}

	groupBy := "flow"
	if gb, err := req.RequireString("group_by"); err == nil && gb != "" {
		groupBy = gb
	}

	// Query error summary
	offset := 0
	limit := 100
	order := &graphql.DeltaFileOrder{
		Direction: graphql.DeltaFileDirectionDesc,
		Field:     "modified",
	}

	if groupBy == "message" {
		resp, err := graphql.VerboseDeltaFiles(&offset, &limit, filter, order)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}

		// Group errors by message
		errorCounts := make(map[string]int)
		for _, df := range resp.DeltaFiles.DeltaFiles {
			if df.Stage == graphql.DeltaFileStageError {
				for _, flow := range df.Flows {
					for _, action := range flow.Actions {
						if action.State == graphql.ActionStateError && action.ErrorCause != nil {
							errorCounts[*action.ErrorCause]++
						}
					}
				}
			}
		}

		type ErrorByMessage struct {
			Message string `json:"message"`
			Count   int    `json:"count"`
		}

		results := make([]ErrorByMessage, 0, len(errorCounts))
		for msg, count := range errorCounts {
			results = append(results, ErrorByMessage{
				Message: msg,
				Count:   count,
			})
		}

		return registry.ResultFromJSON(results)
	}

	// Default: group by flow - use the error stage filter
	stage := graphql.DeltaFileStageError
	filter.Stage = &stage

	resp, err := graphql.VerboseDeltaFiles(&offset, &limit, filter, order)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	// Group by flow
	flowCounts := make(map[string]int)
	for _, df := range resp.DeltaFiles.DeltaFiles {
		for _, flow := range df.Flows {
			if flow.State == graphql.DeltaFileFlowStateError {
				flowCounts[flow.Name]++
			}
		}
	}

	type ErrorByFlow struct {
		Flow  string `json:"flow"`
		Count int    `json:"count"`
	}

	results := make([]ErrorByFlow, 0, len(flowCounts))
	for flow, count := range flowCounts {
		results = append(results, ErrorByFlow{
			Flow:  flow,
			Count: count,
		})
	}

	return registry.ResultFromJSON(results)
}

func countErrors(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	stats, err := graphql.GetDeltaFileStats()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type ErrorCount struct {
		TotalCount    int64 `json:"total_count"`
		InFlightCount int64 `json:"in_flight_count"`
	}

	return registry.ResultFromJSON(ErrorCount{
		TotalCount:    stats.DeltaFileStats.TotalCount,
		InFlightCount: stats.DeltaFileStats.InFlightCount,
	})
}

func getAnnotationKeys(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetAnnotationKeys()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.AnnotationKeys)
}

func exportConfig(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	// Get all system flow plans which represent the configuration
	resp, err := graphql.GetSystemFlowPlans()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp)
}

// parseTime parses a time string that can be:
// - ISO8601 format (2024-01-15T10:30:00Z)
// - Relative duration (1h, 24h, 7d)
func parseTime(s string) (time.Time, error) {
	// Try ISO8601 first
	t, err := time.Parse(time.RFC3339, s)
	if err == nil {
		return t, nil
	}

	// Try relative duration
	if len(s) < 2 {
		return time.Time{}, fmt.Errorf("invalid time format: %s", s)
	}

	unit := s[len(s)-1]
	valueStr := s[:len(s)-1]

	var value int
	_, err = fmt.Sscanf(valueStr, "%d", &value)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid time format: %s", s)
	}

	now := time.Now()
	switch unit {
	case 'h':
		return now.Add(-time.Duration(value) * time.Hour), nil
	case 'd':
		return now.Add(-time.Duration(value) * 24 * time.Hour), nil
	case 'm':
		return now.Add(-time.Duration(value) * time.Minute), nil
	default:
		return time.Time{}, fmt.Errorf("invalid time unit: %c (use h, d, or m)", unit)
	}
}
