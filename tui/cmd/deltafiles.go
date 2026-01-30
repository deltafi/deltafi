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
	"fmt"
	"io"
	"os"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var deltafilesCmd = &cobra.Command{
	Use:   "deltafiles",
	Short: "Manage and search DeltaFiles",
	Long: `Manage and search DeltaFiles in the DeltaFi system.

This command provides tools for searching, analyzing, and managing DeltaFiles
in the system. You can search for specific patterns, view summaries, and
perform bulk operations on files.

Examples:
  deltafi deltafiles view                    # Interactive DeltaFiles viewer
  deltafi deltafiles list                    # List DeltaFiles`,
	GroupID:            "deltafile",
	SilenceUsage:       true,
	DisableSuggestions: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var deltafilesViewCmd = &cobra.Command{
	Use:          "view",
	Short:        "View DeltaFiles",
	Long:         `View DeltaFiles using various criteria such as flow names, time ranges, or other filters`,
	SilenceUsage: true,
	RunE:         runDeltafilesView,
}

func runDeltafilesView(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("deltafiles view command does not accept positional arguments: %v", args)
	}

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	return runSearchViewer(filter)
}

var deltafilesListCmd = &cobra.Command{
	Use:          "list",
	Short:        "List DeltaFiles",
	Long:         `List DeltaFiles in a simple format.`,
	SilenceUsage: true,
	RunE:         runDeltafilesList,
}

var deltafilesExportCmd = &cobra.Command{
	Use:   "export",
	Short: "Export multiple DeltaFiles",
	Long: `Export multiple DeltaFiles to a tar archive based on filter criteria.

The exported file contains the metadata and content of all matching DeltaFiles.
The export can also be configured to include only metadata.
Use the same filter flags as the list/view commands to select which DeltaFiles to export.

Examples:
  deltafi deltafiles export --from "yesterday" --to "now"
  deltafi deltafiles export --data-source "file-watcher" --limit 50
  deltafi deltafiles export --stage "COMPLETE" -o my-export.tar`,
	SilenceUsage: true,
	RunE:         runDeltafilesExport,
}

func runDeltafilesList(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("list command does not accept positional arguments: %v", args)
	}

	plain, _ := cmd.Flags().GetBool("plain")
	json, _ := cmd.Flags().GetBool("json")
	yaml, _ := cmd.Flags().GetBool("yaml")
	limit, _ := cmd.Flags().GetInt("limit")
	offset, _ := cmd.Flags().GetInt("offset")
	verbose, _ := cmd.Flags().GetBool("verbose")
	terse, _ := cmd.Flags().GetBool("terse")

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	if json {
		return searchResultJSON(filter, limit, offset, plain, verbose)
	} else if yaml {
		return searchResultYAML(filter, limit, offset, verbose)
	} else {
		return searchResultTable(filter, limit, offset, plain, terse)
	}
}

var exportsOutputPath string
var exportsIncludeContent bool

func runDeltafilesExport(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("exports command does not accept positional arguments: %v", args)
	}

	limit, _ := cmd.Flags().GetInt("limit")
	offset, _ := cmd.Flags().GetInt("offset")

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	return executeExportDeltaFiles(filter, limit, offset, exportsIncludeContent, exportsOutputPath)
}

func executeExportDeltaFiles(filter searchFilter, limit int, offset int, includeContent bool, outputPath string) error {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return clientError(err)
	}

	// Build the export request
	request := api.ExportDeltaFilesRequest{
		DeltaFilesFilter: filter.toGraphQLFilter(),
		IncludeContent:   includeContent,
		Limit:            limit,
		Offset:           offset,
		Order:            filter.order,
	}

	body, suggestedFilename, err := client.ExportDeltaFiles(request)
	if err != nil {
		return fmt.Errorf("failed to export deltafiles: %w", err)
	}
	defer func(body io.ReadCloser) {
		_ = body.Close()
	}(body)

	// Use suggested filename if no output path specified
	filename := outputPath
	if filename == "" {
		filename = suggestedFilename
	}

	file, err := os.Create(filename)
	if err != nil {
		return fmt.Errorf("failed to create file: %w", err)
	}
	defer func(file *os.File) {
		_ = file.Close()
	}(file)

	bytesWritten, err := io.Copy(file, body)
	if err != nil {
		return fmt.Errorf("failed to write file: %w", err)
	}

	fmt.Printf("Exported DeltaFiles (%d bytes) to %s\n", bytesWritten, filename)
	return nil
}

func init() {
	rootCmd.AddCommand(deltafilesCmd)
	deltafilesCmd.AddCommand(deltafilesViewCmd)
	deltafilesCmd.AddCommand(deltafilesListCmd)
	deltafilesCmd.AddCommand(deltafilesExportCmd)
	searchParams.addSearchFlagsAndCompletions(deltafilesViewCmd)

	searchParams.addSearchFlagsAndCompletions(deltafilesListCmd)
	deltafilesListCmd.Flags().BoolP("plain", "p", false, "Show plain table output")
	deltafilesListCmd.Flags().Bool("json", false, "Show JSON output")
	deltafilesListCmd.Flags().Bool("yaml", false, "Show YAML output")
	deltafilesListCmd.Flags().Int("limit", 100, "Limit the number of results")
	deltafilesListCmd.Flags().Int("offset", 0, "Offset the results")
	deltafilesListCmd.Flags().BoolP("verbose", "v", false, "Show verbose output (JSON and YAML only)")
	deltafilesListCmd.Flags().Bool("terse", false, "Hide table header and footer")

	searchParams.addSearchFlagsAndCompletionsWithDisplayFlags(deltafilesExportCmd, false)
	deltafilesExportCmd.Flags().StringVarP(&exportsOutputPath, "output", "o", "", "Output file name (default: auto-generated)")
	deltafilesExportCmd.Flags().BoolVar(&exportsIncludeContent, "include-content", true, "Include content in the export")
	deltafilesExportCmd.Flags().Int("limit", 100, "Limit the number of DeltaFiles to export")
	deltafilesExportCmd.Flags().Int("offset", 0, "Offset the results")
}
