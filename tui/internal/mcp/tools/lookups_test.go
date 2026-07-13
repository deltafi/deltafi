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
// ABOUTME: Request-level tests for lookup-table MCP handlers
// ABOUTME: Verifies malformed query/create/upsert input is rejected, not silently coerced

package tools

import (
	"context"
	"strings"
	"testing"

	"github.com/deltafi/tui/graphql"
)

// stubLookupQuery installs a recording stub over the Lookup mutation and reports
// whether it was invoked.
func stubLookupQuery(t *testing.T) *bool {
	t.Helper()
	called := false
	original := lookupQuery
	lookupQuery = func(name string, mcv []graphql.MatchingColumnValueInput, resultColumns []string, sortColumn *string, sortDirection *graphql.SortDirection, offset, limit *int) (*graphql.LookupResponse, error) {
		called = true
		return &graphql.LookupResponse{}, nil
	}
	t.Cleanup(func() { lookupQuery = original })
	return &called
}

func TestQueryLookupTable_RejectsInvalidSortDirection(t *testing.T) {
	called := stubLookupQuery(t)
	result, err := queryLookupTable(context.Background(), callToolRequest(map[string]any{
		"name":           "t",
		"sort_direction": "sideways",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if *called {
		t.Fatal("query ran with an invalid sort_direction; it was not validated")
	}
	if !result.IsError || !strings.Contains(resultText(t, result), "sort_direction") {
		t.Fatalf("expected sort_direction validation error, got %q", resultText(t, result))
	}
}

func TestQueryLookupTable_RejectsNonStringFilterValue(t *testing.T) {
	called := stubLookupQuery(t)
	result, err := queryLookupTable(context.Background(), callToolRequest(map[string]any{
		"name":             "t",
		"matching_columns": map[string]any{"color": float64(7)},
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if *called {
		t.Fatal("query ran with a non-string filter value; it was silently coerced")
	}
	if !result.IsError {
		t.Fatalf("expected error for non-string filter value, got %q", resultText(t, result))
	}
}

func TestQueryLookupTable_RejectsNonStringResultColumn(t *testing.T) {
	called := stubLookupQuery(t)
	result, err := queryLookupTable(context.Background(), callToolRequest(map[string]any{
		"name":           "t",
		"result_columns": []any{"a", float64(5)},
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if *called {
		t.Fatal("query ran with a non-string result column; it was silently dropped")
	}
	if !result.IsError {
		t.Fatalf("expected error for non-string result column, got %q", resultText(t, result))
	}
}

func TestQueryLookupTable_Success(t *testing.T) {
	var gotDir *graphql.SortDirection
	original := lookupQuery
	lookupQuery = func(name string, mcv []graphql.MatchingColumnValueInput, resultColumns []string, sortColumn *string, sortDirection *graphql.SortDirection, offset, limit *int) (*graphql.LookupResponse, error) {
		gotDir = sortDirection
		return &graphql.LookupResponse{}, nil
	}
	t.Cleanup(func() { lookupQuery = original })

	result, err := queryLookupTable(context.Background(), callToolRequest(map[string]any{
		"name":           "t",
		"sort_direction": "asc",
		"result_columns": []any{"a", "b"},
	}))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", resultText(t, result), err)
	}
	if gotDir == nil || *gotDir != graphql.SortDirectionAsc {
		t.Fatalf("sort_direction not normalized to ASC: %v", gotDir)
	}
}

func TestCreateLookupTable_RejectsNonStringColumn(t *testing.T) {
	called := false
	original := createLookupTableCall
	createLookupTableCall = func(input graphql.LookupTableInput) (*graphql.CreateLookupTableResponse, error) {
		called = true
		return &graphql.CreateLookupTableResponse{}, nil
	}
	t.Cleanup(func() { createLookupTableCall = original })

	result, err := createLookupTable(context.Background(), callToolRequest(map[string]any{
		"name":        "t",
		"columns":     []any{"a", float64(2)},
		"key_columns": []any{"a"},
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if called {
		t.Fatal("create ran with a non-string column; it was silently dropped")
	}
	if !result.IsError {
		t.Fatalf("expected error for non-string column, got %q", resultText(t, result))
	}
}

func TestUpsertLookupTableRows_RejectsCellMissingValue(t *testing.T) {
	called := false
	original := upsertLookupRows
	upsertLookupRows = func(name string, rows [][]graphql.ColumnValueInput) (*graphql.UpsertLookupTableRowsResponse, error) {
		called = true
		return &graphql.UpsertLookupTableRowsResponse{}, nil
	}
	t.Cleanup(func() { upsertLookupRows = original })

	result, err := upsertLookupTableRows(context.Background(), callToolRequest(map[string]any{
		"name": "t",
		"rows": []any{
			[]any{map[string]any{"column": "a"}}, // missing "value"
		},
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if called {
		t.Fatal("upsert ran with a cell missing its value; it was silently accepted")
	}
	if !result.IsError {
		t.Fatalf("expected error for cell missing value, got %q", resultText(t, result))
	}
}
