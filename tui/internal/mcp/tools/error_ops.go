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
// ABOUTME: Error management MCP tools for resume, acknowledge, and replay operations
// ABOUTME: Provides bulk operations on errored DeltaFiles via GraphQL mutations

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

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("resume_errored_deltafiles",
			"Resume errored DeltaFiles matching a filter. Retries the failed action.",
			mcp.WithString("data_source", mcp.Description("Filter by data source name")),
			mcp.WithString("transform", mcp.Description("Filter by transform flow name")),
			mcp.WithString("data_sink", mcp.Description("Filter by data sink flow name")),
			mcp.WithString("error_cause", mcp.Description("Filter by error message pattern")),
			mcp.WithString("modified_after", mcp.Description("Only resume errors modified after this time (ISO8601 or relative like '1h', '24h', '7d')")),
		),
		Handler: resumeErroredDeltaFiles,
		Tags:    []string{"operations"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("acknowledge_errored_deltafiles",
			"Acknowledge errored DeltaFiles matching a filter. Acknowledged errors are hidden from default error views.",
			mcp.WithString("reason", mcp.Required(), mcp.Description("Reason for acknowledging the errors")),
			mcp.WithString("data_source", mcp.Description("Filter by data source name")),
			mcp.WithString("transform", mcp.Description("Filter by transform flow name")),
			mcp.WithString("data_sink", mcp.Description("Filter by data sink flow name")),
			mcp.WithString("error_cause", mcp.Description("Filter by error message pattern")),
			mcp.WithString("modified_after", mcp.Description("Only acknowledge errors modified after this time (ISO8601 or relative like '1h', '24h', '7d')")),
		),
		Handler: acknowledgeErroredDeltaFiles,
		Tags:    []string{"operations"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("replay_errored_deltafiles",
			"Replay errored DeltaFiles matching a filter. Creates new DeltaFiles that re-ingest the original data.",
			mcp.WithString("data_source", mcp.Description("Filter by data source name")),
			mcp.WithString("transform", mcp.Description("Filter by transform flow name")),
			mcp.WithString("data_sink", mcp.Description("Filter by data sink flow name")),
			mcp.WithString("error_cause", mcp.Description("Filter by error message pattern")),
			mcp.WithString("modified_after", mcp.Description("Only replay errors modified after this time (ISO8601 or relative like '1h', '24h', '7d')")),
		),
		Handler: replayErroredDeltaFiles,
		Tags:    []string{"operations"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("replay_and_acknowledge_errored_deltafiles",
			"Replay errored DeltaFiles and acknowledge the originals in one operation.",
			mcp.WithString("reason", mcp.Required(), mcp.Description("Reason for acknowledging the original errors")),
			mcp.WithString("data_source", mcp.Description("Filter by data source name")),
			mcp.WithString("transform", mcp.Description("Filter by transform flow name")),
			mcp.WithString("data_sink", mcp.Description("Filter by data sink flow name")),
			mcp.WithString("error_cause", mcp.Description("Filter by error message pattern")),
			mcp.WithString("modified_after", mcp.Description("Only affect errors modified after this time (ISO8601 or relative like '1h', '24h', '7d')")),
		),
		Handler: replayAndAcknowledgeErroredDeltaFiles,
		Tags:    []string{"operations"},
	})
}

func buildErrorFilter(req mcp.CallToolRequest) (*graphql.DeltaFilesFilter, error) {
	filter := &graphql.DeltaFilesFilter{}
	errorStage := graphql.DeltaFileStageError
	filter.Stage = &errorStage

	// Only unacknowledged errors
	unack := false
	filter.ErrorAcknowledged = &unack

	if ds, err := req.RequireString("data_source"); err == nil && ds != "" {
		filter.DataSources = []string{ds}
	}

	if transform, err := req.RequireString("transform"); err == nil && transform != "" {
		filter.Transforms = []string{transform}
	}

	if dataSink, err := req.RequireString("data_sink"); err == nil && dataSink != "" {
		filter.DataSinks = []string{dataSink}
	}

	if cause, err := req.RequireString("error_cause"); err == nil && cause != "" {
		filter.ErrorCause = &cause
	}

	if after, err := req.RequireString("modified_after"); err == nil && after != "" {
		t, err := parseTime(after)
		if err != nil {
			return nil, fmt.Errorf("invalid modified_after: %s", err)
		}
		filter.ModifiedAfter = &t
	}

	return filter, nil
}

func resumeErroredDeltaFiles(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	filter, err := buildErrorFilter(req)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.ResumeMatching(*filter, nil)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	succeeded := 0
	failed := 0
	for _, r := range resp.ResumeMatching {
		if r.Success {
			succeeded++
		} else {
			failed++
		}
	}

	return registry.ResultFromJSON(map[string]any{
		"total":     len(resp.ResumeMatching),
		"succeeded": succeeded,
		"failed":    failed,
	})
}

func acknowledgeErroredDeltaFiles(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	reason, errResult := requireNonEmptyString(req, "reason")
	if errResult != nil {
		return errResult, nil
	}

	filter, err := buildErrorFilter(req)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.AcknowledgeMatching(*filter, &reason)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	succeeded := 0
	failed := 0
	for _, r := range resp.AcknowledgeMatching {
		if r.Success {
			succeeded++
		} else {
			failed++
		}
	}

	return registry.ResultFromJSON(map[string]any{
		"total":     len(resp.AcknowledgeMatching),
		"succeeded": succeeded,
		"failed":    failed,
	})
}

func replayErroredDeltaFiles(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	filter, err := buildErrorFilter(req)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.ReplayMatching(*filter, nil, nil)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	succeeded := 0
	failed := 0
	for _, r := range resp.ReplayMatching {
		if r.Success {
			succeeded++
		} else {
			failed++
		}
	}

	return registry.ResultFromJSON(map[string]any{
		"total":     len(resp.ReplayMatching),
		"succeeded": succeeded,
		"failed":    failed,
	})
}

func replayAndAcknowledgeErroredDeltaFiles(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	reason, errResult := requireNonEmptyString(req, "reason")
	if errResult != nil {
		return errResult, nil
	}

	filter, err := buildErrorFilter(req)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.ReplayAndAcknowledgeMatching(*filter, reason, nil, nil)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	succeeded := 0
	failed := 0
	for _, r := range resp.ReplayAndAcknowledgeMatching {
		if r.Success {
			succeeded++
		} else {
			failed++
		}
	}

	return registry.ResultFromJSON(map[string]any{
		"total":     len(resp.ReplayAndAcknowledgeMatching),
		"succeeded": succeeded,
		"failed":    failed,
	})
}
