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
package cmd

import (
	"encoding/csv"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/spf13/cobra"
	"sigs.k8s.io/yaml"
)

var lookupCmd = &cobra.Command{
	Use:          "lookup",
	Short:        "Manage lookup tables",
	Long:         `Manage lookup tables`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var lookupCliCmd = &cobra.Command{
	Use:   "postgres",
	Short: "Start an interactive Postgres CLI session for the table lookup service",
	Long:  "Start an interactive Postgres CLI session for the table lookup service",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, err := app.GetOrchestrator().GetPostgresLookupCmd(args)
		if err != nil {
			return err
		}

		return executeShellCommand(c)
	},
}

var lookupTablesListCmd = &cobra.Command{
	Use:          "list",
	Short:        "List Lookup Tables",
	Long:         `List Lookup Tables in a simple format.`,
	SilenceUsage: true,
	RunE:         runLookupTablesList,
}

func runLookupTablesList(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()

	if len(args) > 0 {
		return fmt.Errorf("list command does not accept positional arguments: %v", args)
	}

	response, err := graphql.GetLookupTables()
	if err != nil {
		return fmt.Errorf("failed to fetch lookup tables: %v", err)
	}

	lookupTables := response.GetLookupTables
	if len(lookupTables) == 0 {
		fmt.Println("There are no lookup tables.")
		return nil
	}

	columns := []string{"Name", "Columns", "Rows", "Service Backed", "Refresh Duration", "Pull Through", "Last Refresh"}

	rows := make([][]string, len(lookupTables))
	for i, lookupTable := range lookupTables {
		lookupTableColumns := make([]string, len(lookupTable.Columns))
		for j, column := range lookupTable.Columns {
			if slices.Contains(lookupTable.KeyColumns, column) {
				lookupTableColumns[j] = "*"
			}
			lookupTableColumns[j] += column
		}
		refreshDuration := ""
		if lookupTable.RefreshDuration != nil {
			refreshDuration = *lookupTable.RefreshDuration
		}
		lastRefresh := ""
		if lookupTable.LastRefresh != nil {
			lastRefresh = formatTime(*lookupTable.LastRefresh)
		}
		rows[i] = []string{
			lookupTable.Name,
			strings.Join(lookupTableColumns, ", "),
			strconv.Itoa(*lookupTable.TotalRows),
			strconv.FormatBool(lookupTable.ServiceBacked),
			refreshDuration,
			strconv.FormatBool(lookupTable.PullThrough),
			lastRefresh}
	}

	table := api.NewTable(columns, rows)
	renderAsSimpleTable(table, false)

	return nil
}

var lookupTableImportCmd = &cobra.Command{
	Use:   "schema-import [schema-file]",
	Short: "Import a lookup table schema",
	Long:  `Import a lookup table schema from a JSON or YAML file. Creates the table structure without data.`,
	Args:  cobra.ExactArgs(1),
	RunE:  runLookupTableImport,
}

func runLookupTableImport(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()

	var lookupTableInput graphql.LookupTableInput

	if err := loadFile(args[0], &lookupTableInput); err != nil {
		printLoadError(args[0], err)
		return err
	}

	response, err := graphql.CreateLookupTable(lookupTableInput)
	if err != nil {
		printLoadError(args[0], err)
		return err
	}

	if !response.CreateLookupTable.Success {
		if len(response.CreateLookupTable.Errors) > 0 {
			return fmt.Errorf("Error loading %s: %s", args[0], *response.CreateLookupTable.Errors[0])
		}
		return fmt.Errorf("Error loading %s", args[0])
	}

	printLoadSuccess(args[0])

	return nil
}

var lookupTableDeleteCmd = &cobra.Command{
	Use:               "delete [name]",
	Short:             "Delete Lookup Table",
	Long:              `Delete Lookup Table by name.`,
	ValidArgsFunction: suggestLookupTableNames,
	Args:              cobra.ExactArgs(1),
	RunE:              runLookupTableDelete,
}

func suggestLookupTableNames(_ *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	if len(args) >= 1 {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}

	names, err := fetchLookupTableNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}

	return filterCompletions(names, toComplete), cobra.ShellCompDirectiveNoFileComp
}

func fetchLookupTableNames() ([]string, error) {
	response, err := graphql.GetLookupTables()
	if err != nil {
		return nil, err
	}

	names := make([]string, 0, len(response.GetLookupTables))
	for _, lookupTable := range response.GetLookupTables {
		names = append(names, lookupTable.Name)
	}
	return names, nil
}

func runLookupTableDelete(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()

	force, _ := cmd.Flags().GetBool("force")
	if !force {
		if !ConfirmPrompt(fmt.Sprintf("Delete lookup table %q?", args[0])) {
			return nil
		}
	}

	response, err := graphql.DeleteLookupTable(args[0])
	if err != nil {
		return wrapInError("Error deleting lookup table", err)
	}

	if !response.DeleteLookupTable.Success {
		if len(response.DeleteLookupTable.Errors) > 0 {
			return fmt.Errorf("Error deleting lookup table %s: %s", args[0], *response.DeleteLookupTable.Errors[0])
		}
		return fmt.Errorf("Error deleting lookup table %s", args[0])
	}

	fmt.Printf("Successfully deleted lookup table %s\n", args[0])

	return nil
}

var lookupTableExportCmd = &cobra.Command{
	Use:               "schema-export [name]",
	Short:             "Export a lookup table schema",
	Long:              `Export a lookup table schema by name. Outputs the table structure as JSON (without data).`,
	ValidArgsFunction: suggestLookupTableNames,
	Args:              cobra.ExactArgs(1),
	RunE:              runLookupTableExport,
}

func runLookupTableExport(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()

	response, err := graphql.GetLookupTableForExport(args[0])
	if err != nil {
		return wrapInError("Error exporting lookup table", err)
	}

	return prettyPrint(cmd, response.GetLookupTable)
}

var lookupTableQueryCmd = &cobra.Command{
	Use:               "query [name]",
	Short:             "Query Lookup Table",
	Long:              `Query Lookup Table by name.`,
	ValidArgsFunction: suggestLookupTableNames,
	Args:              cobra.ExactArgs(1),
	RunE:              runLookupTableQuery,
}

func runLookupTableQuery(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()

	// TODO: Use more parameters
	results, err := graphql.Lookup(args[0], nil, nil, nil, nil, nil, nil)
	if err != nil {
		return wrapInError("Error querying lookup table", err)
	}

	jsonFlag, _ := cmd.Flags().GetBool("json")
	if jsonFlag {
		return prettyPrint(cmd, convertToMaps(results.Lookup.Rows))
	}

	yamlFlag, _ := cmd.Flags().GetBool("yaml")
	if yamlFlag {
		return writeYAML(convertToMaps(results.Lookup.Rows))
	}

	lookupTable, err := graphql.GetLookupTable(args[0])
	if err != nil {
		return wrapInError("Error querying lookup table", err)
	}

	columns := append(lookupTable.GetLookupTable.Columns, "last_updated")
	rows := make([][]string, len(results.Lookup.Rows))
	for i, row := range results.Lookup.Rows {
		columnValueMap := make(map[string]string)
		for _, columnValue := range row {
			columnValueMap[columnValue.Column] = columnValue.Value
		}
		rows[i] = make([]string, len(columns))
		for j, column := range columns {
			rows[i][j] = columnValueMap[column]
		}
	}

	csvFlag, _ := cmd.Flags().GetBool("csv")
	noHeaderFlag, _ := cmd.Flags().GetBool("no-header")
	if csvFlag {
		return writeCSV(columns, rows, noHeaderFlag)
	}

	plainFlag, _ := cmd.Flags().GetBool("plain")
	t := api.NewTable(columns, rows)
	table := components.NewSimpleTable(&t).NoHeader(noHeaderFlag)
	if plainFlag {
		fmt.Println(table.RenderPlain())
	} else {
		fmt.Println(table.Render())
	}

	return nil
}

func writeCSV(columns []string, rows [][]string, noHeader bool) error {
	w := csv.NewWriter(os.Stdout)
	if !noHeader {
		if err := w.Write(columns); err != nil {
			return wrapInError("Error writing CSV header", err)
		}
	}
	for _, row := range rows {
		if err := w.Write(row); err != nil {
			return wrapInError("Error writing CSV row", err)
		}
	}
	w.Flush()
	return w.Error()
}

func writeYAML(data any) error {
	yamlData, err := yaml.Marshal(data)
	if err != nil {
		return wrapInError("Error writing YAML", err)
	}
	fmt.Print(string(yamlData))
	return nil
}

func convertToMaps(rows [][]graphql.LookupLookupLookupResultsRowsColumnValue) []map[string]string {
	result := make([]map[string]string, len(rows))
	for i, row := range rows {
		m := make(map[string]string)
		for _, cv := range row {
			m[cv.Column] = cv.Value
		}
		result[i] = m
	}
	return result
}

var lookupTableInsertRowsCmd = &cobra.Command{
	Use:   "insert [name] [rows-file]",
	Short: "Insert rows into Lookup Table",
	Long: `Insert rows into Lookup Table. Replaces existing entries with the same key columns.
If no file is specified, reads from stdin (use --format to specify input format).

Example JSON format:
  [
    {"title": "Hey Jude", "year": "1968", "artist": "Beatles"},
    {"title": "Let It Be", "year": "1970", "artist": "Beatles"}
  ]`,
	ValidArgsFunction: suggestLookupTableNames,
	Args:              cobra.RangeArgs(1, 2),
	RunE:              runLookupTableInsert,
}

func runLookupTableInsert(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()

	var rows [][]graphql.ColumnValueInput
	var err error

	if len(args) == 2 {
		rows, err = loadLookupRows(args[1])
		if err != nil {
			printLoadError(args[1], err)
			return err
		}
	} else {
		format, _ := cmd.Flags().GetString("format")
		rows, err = loadLookupRowsFromStdin(format)
		if err != nil {
			return wrapInError("Error reading from stdin", err)
		}
	}

	response, err := graphql.UpsertLookupTableRows(args[0], rows)
	if err != nil {
		return wrapInError("Error inserting lookup table rows", err)
	}

	if !response.UpsertLookupTableRows.Success {
		if len(response.UpsertLookupTableRows.Errors) > 0 {
			return fmt.Errorf("Error inserting lookup table rows into %s: %s", args[0], *response.UpsertLookupTableRows.Errors[0])
		}
		return fmt.Errorf("Error inserting lookup table rows into %s", args[0])
	}

	fmt.Printf("Successfully inserted lookup table rows into %s\n", args[0])

	return nil
}

func loadLookupRowsFromStdin(format string) ([][]graphql.ColumnValueInput, error) {
	content, err := io.ReadAll(os.Stdin)
	if err != nil {
		return nil, err
	}

	switch format {
	case "csv":
		return parseLookupRowsFromCSV(strings.NewReader(string(content)))
	case "yaml", "yml":
		return parseRowsFromContent(content, unmarshalYAML)
	default:
		return parseRowsFromContent(content, json.Unmarshal)
	}
}

func unmarshalYAML(data []byte, v any) error {
	return yaml.Unmarshal(data, v)
}

func parseRowsFromContent(content []byte, unmarshal func([]byte, any) error) ([][]graphql.ColumnValueInput, error) {
	// Try simple map format first: [{"col": "val", ...}, ...]
	var simpleMaps []map[string]string
	simpleErr := unmarshal(content, &simpleMaps)
	if simpleErr == nil && len(simpleMaps) > 0 && len(simpleMaps[0]) > 0 {
		// Verify it's actually the simple format by checking keys aren't "column"/"value"
		if _, hasColumn := simpleMaps[0]["column"]; !hasColumn {
			return convertMapsToRows(simpleMaps), nil
		}
	}

	// Fall back to original format: [[{"column": "col", "value": "val"}, ...], ...]
	var rows [][]graphql.ColumnValueInput
	originalErr := unmarshal(content, &rows)
	if originalErr == nil {
		return rows, nil
	}

	// Both formats failed - provide helpful error message
	errMsg := fmt.Sprintf(`Failed to parse input. Expected one of these formats:

Simple format (recommended):
  [{"column1": "value1", "column2": "value2"}, ...]

Original format:
  [[{"column": "column1", "value": "value1"}, ...], ...]

Parse errors:
  Simple format: %v
  Original format: %v`, simpleErr, originalErr)

	return nil, fmt.Errorf("%s", errMsg)
}

func convertMapsToRows(maps []map[string]string) [][]graphql.ColumnValueInput {
	rows := make([][]graphql.ColumnValueInput, len(maps))
	for i, m := range maps {
		row := make([]graphql.ColumnValueInput, 0, len(m))
		for col, val := range m {
			if val != "" {
				row = append(row, graphql.ColumnValueInput{Column: col, Value: val})
			}
		}
		rows[i] = row
	}
	return rows
}

func loadLookupRows(filename string) ([][]graphql.ColumnValueInput, error) {
	ext := strings.ToLower(filepath.Ext(filename))
	if ext == ".csv" {
		return loadLookupRowsFromCSV(filename)
	}

	content, err := os.ReadFile(filename)
	if err != nil {
		return nil, wrapInError("Could not read file", err)
	}

	switch ext {
	case ".yaml", ".yml":
		return parseRowsFromContent(content, unmarshalYAML)
	default:
		return parseRowsFromContent(content, json.Unmarshal)
	}
}

func loadLookupRowsFromCSV(filename string) ([][]graphql.ColumnValueInput, error) {
	file, err := os.Open(filename)
	if err != nil {
		return nil, wrapInError("Could not open file", err)
	}
	defer file.Close()

	return parseLookupRowsFromCSV(file)
}

func parseLookupRowsFromCSV(r io.Reader) ([][]graphql.ColumnValueInput, error) {
	reader := csv.NewReader(r)
	records, err := reader.ReadAll()
	if err != nil {
		return nil, wrapInError("Error reading CSV", err)
	}

	if len(records) < 1 {
		return nil, newError("CSV file is empty", "The file must have a header row")
	}

	columns := records[0]
	rows := make([][]graphql.ColumnValueInput, 0, len(records)-1)

	for _, record := range records[1:] {
		row := make([]graphql.ColumnValueInput, 0, len(columns))
		for j, value := range record {
			if j < len(columns) && value != "" {
				row = append(row, graphql.ColumnValueInput{
					Column: columns[j],
					Value:  value,
				})
			}
		}
		rows = append(rows, row)
	}

	return rows, nil
}

func init() {
	rootCmd.AddCommand(lookupCmd)
	lookupCmd.AddCommand(lookupCliCmd)
	lookupCmd.AddCommand(lookupTablesListCmd)
	lookupCmd.AddCommand(lookupTableImportCmd)
	lookupTableDeleteCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")
	lookupCmd.AddCommand(lookupTableDeleteCmd)
	lookupCmd.AddCommand(lookupTableExportCmd)
	lookupTableQueryCmd.Flags().BoolP("json", "j", false, "Output in JSON format")
	lookupTableQueryCmd.Flags().BoolP("yaml", "y", false, "Output in YAML format")
	lookupTableQueryCmd.Flags().BoolP("csv", "c", false, "Output in CSV format")
	lookupTableQueryCmd.Flags().BoolP("plain", "p", false, "Output as plain table (no styling)")
	lookupTableQueryCmd.Flags().BoolP("no-header", "n", false, "Omit header row (for table, plain, and csv output)")
	lookupTableQueryCmd.MarkFlagsMutuallyExclusive("json", "yaml", "csv", "plain")
	lookupCmd.AddCommand(lookupTableQueryCmd)
	lookupTableInsertRowsCmd.Flags().StringP("format", "f", "json", "Input format when reading from stdin (json, yaml, csv)")
	lookupCmd.AddCommand(lookupTableInsertRowsCmd)
}
