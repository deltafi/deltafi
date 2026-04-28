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
// ABOUTME: Tests for error operations MCP tool filter construction
// ABOUTME: Verifies that request parameters are correctly mapped to DeltaFilesFilter fields

package tools

import (
	"testing"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
)

func newCallToolRequest(args map[string]any) mcp.CallToolRequest {
	return mcp.CallToolRequest{
		Params: mcp.CallToolParams{
			Arguments: args,
		},
	}
}

func TestBuildErrorFilter_Defaults(t *testing.T) {
	req := newCallToolRequest(map[string]any{})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if filter.Stage == nil || *filter.Stage != graphql.DeltaFileStageError {
		t.Error("expected stage to be ERROR")
	}
	if filter.ErrorAcknowledged == nil || *filter.ErrorAcknowledged != false {
		t.Error("expected error_acknowledged to be false")
	}
	if len(filter.DataSources) != 0 {
		t.Error("expected no data source filter")
	}
	if len(filter.Transforms) != 0 {
		t.Error("expected no transform filter")
	}
	if len(filter.DataSinks) != 0 {
		t.Error("expected no data sink filter")
	}
	if filter.ErrorCause != nil {
		t.Error("expected no error cause filter")
	}
	if filter.ModifiedAfter != nil {
		t.Error("expected no modified_after filter")
	}
}

func TestBuildErrorFilter_DataSource(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"data_source": "my-source",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(filter.DataSources) != 1 || filter.DataSources[0] != "my-source" {
		t.Errorf("expected data source filter [my-source], got %v", filter.DataSources)
	}
}

func TestBuildErrorFilter_Transform(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"transform": "my-transform",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(filter.Transforms) != 1 || filter.Transforms[0] != "my-transform" {
		t.Errorf("expected transform filter [my-transform], got %v", filter.Transforms)
	}
	if len(filter.DataSinks) != 0 {
		t.Errorf("expected no data sink filter when transform is set, got %v", filter.DataSinks)
	}
}

func TestBuildErrorFilter_DataSink(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"data_sink": "my-sink",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(filter.DataSinks) != 1 || filter.DataSinks[0] != "my-sink" {
		t.Errorf("expected data sink filter [my-sink], got %v", filter.DataSinks)
	}
	if len(filter.Transforms) != 0 {
		t.Errorf("expected no transform filter when data_sink is set, got %v", filter.Transforms)
	}
}

func TestBuildErrorFilter_TransformAndDataSinkIndependent(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"transform": "my-transform",
		"data_sink": "my-sink",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(filter.Transforms) != 1 || filter.Transforms[0] != "my-transform" {
		t.Errorf("expected transform filter [my-transform], got %v", filter.Transforms)
	}
	if len(filter.DataSinks) != 1 || filter.DataSinks[0] != "my-sink" {
		t.Errorf("expected data sink filter [my-sink], got %v", filter.DataSinks)
	}
}

func TestBuildErrorFilter_ErrorCause(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"error_cause": "NullPointerException",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if filter.ErrorCause == nil || *filter.ErrorCause != "NullPointerException" {
		t.Errorf("expected error cause NullPointerException, got %v", filter.ErrorCause)
	}
}

func TestBuildErrorFilter_ModifiedAfter(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"modified_after": "1h",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if filter.ModifiedAfter == nil {
		t.Error("expected modified_after to be set")
	}
}

func TestBuildErrorFilter_InvalidModifiedAfter(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"modified_after": "not-a-time",
	})
	_, err := buildErrorFilter(req)
	if err == nil {
		t.Error("expected error for invalid modified_after")
	}
}

func TestBuildErrorFilter_AllFilters(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"data_source":    "source-1",
		"transform":      "transform-1",
		"data_sink":      "sink-1",
		"error_cause":    "timeout",
		"modified_after": "24h",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(filter.DataSources) != 1 || filter.DataSources[0] != "source-1" {
		t.Errorf("data source filter: got %v", filter.DataSources)
	}
	if len(filter.Transforms) != 1 || filter.Transforms[0] != "transform-1" {
		t.Errorf("transform filter: got %v", filter.Transforms)
	}
	if len(filter.DataSinks) != 1 || filter.DataSinks[0] != "sink-1" {
		t.Errorf("data sink filter: got %v", filter.DataSinks)
	}
	if filter.ErrorCause == nil || *filter.ErrorCause != "timeout" {
		t.Errorf("error cause filter: got %v", filter.ErrorCause)
	}
	if filter.ModifiedAfter == nil {
		t.Error("modified_after filter not set")
	}
}

func TestBuildErrorFilter_EmptyStringsIgnored(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"data_source": "",
		"transform":   "",
		"data_sink":   "",
		"error_cause": "",
	})
	filter, err := buildErrorFilter(req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(filter.DataSources) != 0 {
		t.Error("empty data_source should not produce a filter")
	}
	if len(filter.Transforms) != 0 {
		t.Error("empty transform should not produce a filter")
	}
	if len(filter.DataSinks) != 0 {
		t.Error("empty data_sink should not produce a filter")
	}
	if filter.ErrorCause != nil {
		t.Error("empty error_cause should not produce a filter")
	}
}
