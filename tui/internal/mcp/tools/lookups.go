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
	"fmt"
	"strings"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

// Seams over the generated GraphQL functions so handlers can be tested without
// a live server.
var (
	lookupQuery           = graphql.Lookup
	createLookupTableCall = graphql.CreateLookupTable
	upsertLookupRows      = graphql.UpsertLookupTableRows
)

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_lookup_tables",
			"List all lookup tables with their metadata (columns, key columns, row count, service-backed status)",
		),
		Handler: listLookupTables,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_lookup_table",
			"Get details for a specific lookup table including columns, key columns, and metadata",
			mcp.WithString("name", mcp.Required(), mcp.Description("Lookup table name")),
		),
		Handler: getLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("query_lookup_table",
			"Query rows from a lookup table with optional filters, column selection, sorting, and pagination",
			mcp.WithString("name", mcp.Required(), mcp.Description("Lookup table name")),
			mcp.WithObject("matching_columns", mcp.AdditionalProperties(map[string]any{"type": "array", "items": map[string]any{"type": "string"}}), mcp.Description("Filter rows where a column matches any supplied value")),
			mcp.WithArray("result_columns", mcp.WithStringItems(), mcp.Description("Columns to return (default: all columns)")),
			mcp.WithString("sort_column", mcp.Description("Column to sort by")),
			mcp.WithString("sort_direction", mcp.Description("Sort direction: ASC or DESC (default: ASC)")),
			mcp.WithInteger("offset", mcp.Min(0), mcp.Description("Number of rows to skip (default: 0)")),
			mcp.WithInteger("limit", mcp.Min(0), mcp.Description("Maximum rows to return (default: 100)")),
		),
		Handler: queryLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: registry.SafeWriteTool("create_lookup_table",
			"Create a new lookup table",
			mcp.WithString("name", mcp.Required(), mcp.Description("Table name")),
			mcp.WithArray("columns", mcp.Required(), mcp.WithStringItems(mcp.MinLength(1)), mcp.MinItems(1), mcp.Description("Column names")),
			mcp.WithArray("key_columns", mcp.Required(), mcp.WithStringItems(mcp.MinLength(1)), mcp.MinItems(1), mcp.Description("Key column names for upsert matching")),
		),
		Handler: createLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("delete_lookup_table",
			"Delete a lookup table and all its data",
			mcp.WithString("name", mcp.Required(), mcp.Description("Table name to delete")),
		),
		Handler: deleteLookupTable,
		Tags:    []string{"admin"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("upsert_lookup_table_rows",
			"Insert or update rows in a lookup table. Rows are matched by key columns for upsert.",
			mcp.WithString("name", mcp.Required(), mcp.Description("Table name")),
			mcp.WithArray("rows", mcp.Required(), mcp.MinItems(1), mcp.Items(map[string]any{
				"type": "array", "minItems": 1, "items": closedObjectSchema(map[string]any{
					"column": map[string]any{"type": "string", "minLength": 1},
					"value":  map[string]any{"type": "string"},
				}, "column", "value"),
			}), mcp.Description("Rows represented as column/value pairs")),
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
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.GetLookupTable(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetLookupTable)
}

func queryLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	args := req.GetArguments()

	// Parse matching columns filter
	var matchingCols []graphql.MatchingColumnValueInput
	if mcRaw, present := args["matching_columns"]; present {
		mc, ok := mcRaw.(map[string]any)
		if !ok {
			return registry.ErrorResult("matching_columns must be a JSON object"), nil
		}
		for col, vals := range mc {
			var values []string
			switch v := vals.(type) {
			case []any:
				vs, err := toStringSlice(v)
				if err != nil {
					return registry.ErrorResult(fmt.Sprintf("matching_columns[%q]: %v", col, err)), nil
				}
				values = vs
			case string:
				values = []string{v}
			default:
				return registry.ErrorResult(fmt.Sprintf("matching_columns[%q] must be a string or array of strings", col)), nil
			}
			matchingCols = append(matchingCols, graphql.MatchingColumnValueInput{
				Column: col,
				Value:  values,
			})
		}
	}

	// Parse result columns
	resultColumns, errResult := stringArrayArg(args, "result_columns")
	if errResult != nil {
		return errResult, nil
	}

	// Sort
	var sortColumn *string
	var sortDirection *graphql.SortDirection
	if sc, err := req.RequireString("sort_column"); err == nil && sc != "" {
		sortColumn = &sc
	}
	if sd, err := req.RequireString("sort_direction"); err == nil && sd != "" {
		dir := graphql.SortDirection(strings.ToUpper(sd))
		if dir != graphql.SortDirectionAsc && dir != graphql.SortDirectionDesc {
			return registry.ErrorResult("sort_direction must be ASC or DESC"), nil
		}
		sortDirection = &dir
	}

	// Pagination
	offset, err := optionalNonNegativeInt(req, "offset")
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}
	defaultLimit := 100
	limit := &defaultLimit
	if l, err := optionalNonNegativeInt(req, "limit"); err != nil {
		return registry.ErrorResult(err.Error()), nil
	} else if l != nil {
		limit = l
	}

	resp, err := lookupQuery(name, matchingCols, resultColumns, sortColumn, sortDirection, offset, limit)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.Lookup)
}

func createLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	args := req.GetArguments()

	columns, errResult := stringArrayArg(args, "columns")
	if errResult != nil {
		return errResult, nil
	}
	if len(columns) == 0 {
		return registry.ErrorResult("columns parameter is required and must not be empty"), nil
	}

	keyColumns, errResult := stringArrayArg(args, "key_columns")
	if errResult != nil {
		return errResult, nil
	}
	if len(keyColumns) == 0 {
		return registry.ErrorResult("key_columns parameter is required and must not be empty"), nil
	}

	input := graphql.LookupTableInput{
		Name:       name,
		Columns:    columns,
		KeyColumns: keyColumns,
	}

	resp, err := createLookupTableCall(input)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.CreateLookupTable)
}

func deleteLookupTable(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.DeleteLookupTable(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.DeleteLookupTable)
}

func upsertLookupTableRows(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
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
			cellMap, ok := cellRaw.(map[string]any)
			if !ok {
				return registry.ErrorResult("each cell must be a {\"column\": \"name\", \"value\": \"val\"} object"), nil
			}
			if _, ok := cellMap["column"]; !ok {
				return registry.ErrorResult("each cell must include a column"), nil
			}
			if _, ok := cellMap["value"]; !ok {
				return registry.ErrorResult("each cell must include a value"), nil
			}
			var cell graphql.ColumnValueInput
			if err := decodeJSON(cellRaw, &cell); err != nil {
				return registry.ErrorResult("each cell must be {\"column\": \"name\", \"value\": \"val\"}"), nil
			}
			if cell.Column == "" {
				return registry.ErrorResult("each cell must include a non-empty column"), nil
			}
			row = append(row, cell)
		}
		rows = append(rows, row)
	}

	resp, err := upsertLookupRows(name, rows)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.UpsertLookupTableRows)
}
