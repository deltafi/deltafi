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
// ABOUTME: Operations MCP tools for DeltaFile state management
// ABOUTME: Provides tools for listing and searching errored DeltaFiles

package tools

import (
	"context"
	"time"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// list_errors - List errored DeltaFiles
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_errors",
			"List DeltaFiles in ERROR state. Use get_deltafile for full details on specific errors.",
			mcp.WithString("acknowledged", mcp.Description("Filter: 'yes' for acknowledged, 'no' for unacknowledged, omit for all")),
			mcp.WithString("transform", mcp.Description("Filter by transform flow name")),
			mcp.WithString("data_sink", mcp.Description("Filter by data sink flow name")),
			mcp.WithString("data_source", mcp.Description("Filter by data source name")),
			mcp.WithString("error_cause", mcp.Description("Filter by error message pattern")),
			mcp.WithInteger("limit", mcp.Min(0), mcp.Max(500), mcp.Description("Maximum results (default 50, max 500)")),
		),
		Handler: listErrors,
		Tags:    []string{"operations"},
	})

	// search_errored_deltafiles - Search for errored DeltaFiles
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("search_errored_deltafiles",
			"Search for errored DeltaFiles with various filters",
			mcp.WithString("modified_after", mcp.Description("Filter by modified time (ISO8601 or relative like '1h', '24h', '7d')")),
			mcp.WithInteger("limit", mcp.Min(0), mcp.Max(500), mcp.Description("Maximum results (default 50, max 500)")),
		),
		Handler: searchErroredDeltaFiles,
		Tags:    []string{"operations"},
	})
}

func listErrors(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	filter := &graphql.DeltaFilesFilter{}

	// Set stage to ERROR
	errorStage := graphql.DeltaFileStageError
	filter.Stage = &errorStage

	// Acknowledged filter
	if ack, err := req.RequireString("acknowledged"); err == nil && ack != "" {
		if ack == "yes" {
			t := true
			filter.ErrorAcknowledged = &t
		} else if ack == "no" {
			f := false
			filter.ErrorAcknowledged = &f
		}
	}

	// Data source filter
	if ds, err := req.RequireString("data_source"); err == nil && ds != "" {
		filter.DataSources = []string{ds}
	}

	// Flow filters
	if transform, err := req.RequireString("transform"); err == nil && transform != "" {
		filter.Transforms = []string{transform}
	}

	if dataSink, err := req.RequireString("data_sink"); err == nil && dataSink != "" {
		filter.DataSinks = []string{dataSink}
	}

	// Error cause filter
	if cause, err := req.RequireString("error_cause"); err == nil && cause != "" {
		filter.ErrorCause = &cause
	}

	// Limit
	limit := 50
	if l, err := optionalNonNegativeInt(req, "limit"); err != nil {
		return registry.ErrorResult(err.Error()), nil
	} else if l != nil {
		limit = min(*l, 500)
	}
	offset := 0

	order := &graphql.DeltaFileOrder{
		Direction: graphql.DeltaFileDirectionDesc,
		Field:     "modified",
	}

	resp, err := graphql.DeltaFiles(&offset, &limit, filter, order)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type ErrorSummary struct {
		DID        string `json:"did"`
		Name       string `json:"name"`
		DataSource string `json:"data_source"`
		Modified   string `json:"modified"`
	}

	results := make([]ErrorSummary, 0, len(resp.DeltaFiles.DeltaFiles))
	for _, df := range resp.DeltaFiles.DeltaFiles {
		name := ""
		if df.Name != nil {
			name = *df.Name
		}
		results = append(results, ErrorSummary{
			DID:        df.Did.String(),
			Name:       name,
			DataSource: df.DataSource,
			Modified:   df.Modified.Format(time.RFC3339),
		})
	}

	totalCount := 0
	if resp.DeltaFiles.TotalCount != nil {
		totalCount = *resp.DeltaFiles.TotalCount
	}

	type SearchResult struct {
		TotalCount int            `json:"total_count"`
		Returned   int            `json:"returned"`
		Errors     []ErrorSummary `json:"errors"`
	}

	return registry.ResultFromJSON(SearchResult{
		TotalCount: totalCount,
		Returned:   len(results),
		Errors:     results,
	})
}

func searchErroredDeltaFiles(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	filter := &graphql.DeltaFilesFilter{}

	// Set stage to ERROR
	errorStage := graphql.DeltaFileStageError
	filter.Stage = &errorStage

	// Modified after filter
	if after, err := req.RequireString("modified_after"); err == nil && after != "" {
		t, err := parseTime(after)
		if err != nil {
			return registry.ErrorResult("invalid modified_after: " + err.Error()), nil
		}
		filter.ModifiedAfter = &t
	}

	// Limit
	limit := 50
	if l, err := optionalNonNegativeInt(req, "limit"); err != nil {
		return registry.ErrorResult(err.Error()), nil
	} else if l != nil {
		limit = min(*l, 500)
	}
	offset := 0

	order := &graphql.DeltaFileOrder{
		Direction: graphql.DeltaFileDirectionDesc,
		Field:     "modified",
	}

	resp, err := graphql.VerboseDeltaFiles(&offset, &limit, filter, order)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type ErrorDetail struct {
		DID        string `json:"did"`
		Name       string `json:"name"`
		DataSource string `json:"data_source"`
		Flow       string `json:"flow,omitempty"`
		Action     string `json:"action,omitempty"`
		ErrorCause string `json:"error_cause,omitempty"`
		Modified   string `json:"modified"`
	}

	results := make([]ErrorDetail, 0)
	for _, df := range resp.DeltaFiles.DeltaFiles {
		name := ""
		if df.Name != nil {
			name = *df.Name
		}

		// Find the errored flow/action
		for _, flow := range df.Flows {
			if flow.State == graphql.DeltaFileFlowStateError {
				for _, action := range flow.Actions {
					if action.State == graphql.ActionStateError {
						errorCause := ""
						if action.ErrorCause != nil {
							errorCause = *action.ErrorCause
						}
						results = append(results, ErrorDetail{
							DID:        df.Did.String(),
							Name:       name,
							DataSource: df.DataSource,
							Flow:       flow.Name,
							Action:     action.Name,
							ErrorCause: errorCause,
							Modified:   df.Modified.Format(time.RFC3339),
						})
					}
				}
			}
		}
	}

	return registry.ResultFromJSON(results)
}
