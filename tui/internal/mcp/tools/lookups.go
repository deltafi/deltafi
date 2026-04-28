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
// ABOUTME: Lookup table MCP tools for managing key-value lookup tables
// ABOUTME: Provides CRUD operations and querying for lookup tables

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

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_lookup_tables",
			mcp.WithDescription("List all lookup tables with their metadata (columns, key columns, row count, service-backed status)"),
		),
		Handler: listLookupTables,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_lookup_table",
			mcp.WithDescription("Get details for a specific lookup table including columns, key columns, and metadata"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Lookup table name")),
		),
		Handler: getLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("query_lookup_table",
			mcp.WithDescription("Query rows from a lookup table with optional filters, column selection, sorting, and pagination"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Lookup table name")),
			mcp.WithObject("matching_columns", mcp.Description("Filter rows where column matches any of the given values. JSON object: {\"column_name\": [\"value1\", \"value2\"]}")),
			mcp.WithArray("result_columns", mcp.Description("Columns to return (default: all columns). Array of column name strings.")),
			mcp.WithString("sort_column", mcp.Description("Column to sort by")),
			mcp.WithString("sort_direction", mcp.Description("Sort direction: ASC or DESC (default: ASC)")),
			mcp.WithNumber("offset", mcp.Description("Number of rows to skip (default: 0)")),
			mcp.WithNumber("limit", mcp.Description("Maximum rows to return (default: 100)")),
		),
		Handler: queryLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("create_lookup_table",
			mcp.WithDescription("Create a new lookup table"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Table name")),
			mcp.WithArray("columns", mcp.Required(), mcp.Description("Column names (array of strings)")),
			mcp.WithArray("key_columns", mcp.Required(), mcp.Description("Key column names for upsert matching (array of strings)")),
		),
		Handler: createLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("delete_lookup_table",
			mcp.WithDescription("Delete a lookup table and all its data"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Table name to delete")),
		),
		Handler: deleteLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: mcp.NewTool("upsert_lookup_table_rows",
			mcp.WithDescription("Insert or update rows in a lookup table. Rows are matched by key columns for upsert."),
			mcp.WithString("name", mcp.Required(), mcp.Description("Table name")),
			mcp.WithArray("rows", mcp.Required(), mcp.Description("Array of rows. Each row is an array of {\"column\": \"col_name\", \"value\": \"val\"} objects.")),
		),
		Handler: upsertLookupTableRows,
		Tags:    []string{"admin"},
	})
}

func listLookupTables(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetLookupTables()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetLookupTables)
}

func getLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	resp, err := graphql.GetLookupTable(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetLookupTable)
}

func queryLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	args := req.GetArguments()

	// Parse matching columns filter
	var matchingCols []graphql.MatchingColumnValueInput
	if mc, ok := args["matching_columns"].(map[string]any); ok {
		for col, vals := range mc {
			var values []string
			switch v := vals.(type) {
			case []any:
				for _, item := range v {
					values = append(values, fmt.Sprintf("%v", item))
				}
			case string:
				values = []string{v}
			}
			matchingCols = append(matchingCols, graphql.MatchingColumnValueInput{
				Column: col,
				Value:  values,
			})
		}
	}

	// Parse result columns
	var resultColumns []string
	if rc, ok := args["result_columns"].([]any); ok {
		for _, c := range rc {
			if s, ok := c.(string); ok {
				resultColumns = append(resultColumns, s)
			}
		}
	}

	// Sort
	var sortColumn *string
	var sortDirection *graphql.SortDirection
	if sc, err := req.RequireString("sort_column"); err == nil && sc != "" {
		sortColumn = &sc
	}
	if sd, err := req.RequireString("sort_direction"); err == nil && sd != "" {
		dir := graphql.SortDirection(sd)
		sortDirection = &dir
	}

	// Pagination
	var offset, limit *int
	if o, err := req.RequireFloat("offset"); err == nil {
		oi := int(o)
		offset = &oi
	}
	defaultLimit := 100
	limit = &defaultLimit
	if l, err := req.RequireFloat("limit"); err == nil {
		li := int(l)
		limit = &li
	}

	resp, err := graphql.Lookup(name, matchingCols, resultColumns, sortColumn, sortDirection, offset, limit)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.Lookup)
}

func createLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	args := req.GetArguments()

	var columns []string
	if cols, ok := args["columns"].([]any); ok {
		for _, c := range cols {
			if s, ok := c.(string); ok {
				columns = append(columns, s)
			}
		}
	}
	if len(columns) == 0 {
		return registry.ErrorResult("columns parameter is required and must not be empty"), nil
	}

	var keyColumns []string
	if kc, ok := args["key_columns"].([]any); ok {
		for _, c := range kc {
			if s, ok := c.(string); ok {
				keyColumns = append(keyColumns, s)
			}
		}
	}
	if len(keyColumns) == 0 {
		return registry.ErrorResult("key_columns parameter is required and must not be empty"), nil
	}

	input := graphql.LookupTableInput{
		Name:       name,
		Columns:    columns,
		KeyColumns: keyColumns,
	}

	resp, err := graphql.CreateLookupTable(input)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.CreateLookupTable)
}

func deleteLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	resp, err := graphql.DeleteLookupTable(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.DeleteLookupTable)
}

func upsertLookupTableRows(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	args := req.GetArguments()
	rowsRaw, ok := args["rows"].([]any)
	if !ok || len(rowsRaw) == 0 {
		return registry.ErrorResult("rows parameter is required and must not be empty"), nil
	}

	var rows [][]graphql.ColumnValueInput
	for _, rowRaw := range rowsRaw {
		rowArray, ok := rowRaw.([]any)
		if !ok {
			return registry.ErrorResult("each row must be an array of {column, value} objects"), nil
		}

		var row []graphql.ColumnValueInput
		for _, cellRaw := range rowArray {
			cellBytes, err := json.Marshal(cellRaw)
			if err != nil {
				return registry.ErrorResult("invalid cell format"), nil
			}
			var cell graphql.ColumnValueInput
			if err := json.Unmarshal(cellBytes, &cell); err != nil {
				return registry.ErrorResult("each cell must be {\"column\": \"name\", \"value\": \"val\"}"), nil
			}
			row = append(row, cell)
		}
		rows = append(rows, row)
	}

	resp, err := graphql.UpsertLookupTableRows(name, rows)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.UpsertLookupTableRows)
}
