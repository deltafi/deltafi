/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
	"errors"
	"fmt"
	"io"
	"os"
	"regexp"
	"strconv"
	"strings"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var errorCmd = &cobra.Command{
	Use:   "errored",
	Short: "Manage and search DeltaFile errors",
	Long: `Manage and search DeltaFile errors in the DeltaFi system.

This command provides tools for searching, analyzing, and managing errors
that occur during DeltaFile processing. You can search for specific error
patterns, view error summaries, and perform bulk operations on errored files.

Examples:
  deltafi errored view                    # Interactive Errored DeltaFiles viewer (unacknowledged only)
  deltafi errored list                    # List Errored DeltaFiles (unacknowledged only)
  deltafi errored view --all              # View all errors (acknowledged and unacknowledged)
  deltafi errored list --acknowledged     # List only acknowledged errors
  deltafi errored  view --error-cause POST
                                          # View errors containing 'post' (case insensitive) in the error cause
  deltafi errored  list --data-source smoke-timed-data-source
                                          # List/search for errors from the smoke-timed-data-source data source`,
	GroupID:            "deltafile",
	SilenceUsage:       true,
	DisableSuggestions: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var errorViewCmd = &cobra.Command{
	Use:          "view",
	Short:        "View DeltaFile errors interactively",
	Long:         `View DeltaFile errors using various criteria such as error messages, flow names, or time ranges. By default shows only unacknowledged errors. Use --all to view all errors.`,
	SilenceUsage: true,
	RunE:         runErrorView,
}

func runErrorView(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("view command does not accept positional arguments: %v", args)
	}

	searchParams.stage = "ERROR"
	showAll, _ := cmd.Flags().GetBool("all")
	showAcknowledged, _ := cmd.Flags().GetBool("acknowledged")
	if showAll {
		searchParams.errorAcknowledged.Unset()
	} else if showAcknowledged {
		searchParams.errorAcknowledged.Set("yes")
	} else {
		searchParams.errorAcknowledged.Set("no")
	}

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	return runSearchViewer(filter)
}

var errorListCmd = &cobra.Command{
	Use:          "list",
	Short:        "List DeltaFile errors",
	Long:         `List DeltaFile errors in a simple format. By default shows only unacknowledged errors. Use --all to list all errors.`,
	SilenceUsage: true,
	RunE:         runErrorList,
}

func runErrorList(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("list command does not accept positional arguments: %v", args)
	}

	searchParams.stage = "ERROR"
	showAll, _ := cmd.Flags().GetBool("all")
	showAcknowledged, _ := cmd.Flags().GetBool("acknowledged")
	plain, _ := cmd.Flags().GetBool("plain")
	json, _ := cmd.Flags().GetBool("json")
	yaml, _ := cmd.Flags().GetBool("yaml")
	limit, _ := cmd.Flags().GetInt("limit")
	offset, _ := cmd.Flags().GetInt("offset")
	verbose, _ := cmd.Flags().GetBool("verbose")
	terse, _ := cmd.Flags().GetBool("terse")

	if showAll {
		searchParams.errorAcknowledged.Unset()
	} else if showAcknowledged {
		searchParams.errorAcknowledged.Set("yes")
	} else {
		searchParams.errorAcknowledged.Set("no")
	}

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

var exportErrorsCmd = &cobra.Command{
	Use:          "export",
	Short:        "Export unacknowledged errored DeltaFile",
	Long:         `Export unacknowledged errored DeltaFile. This will mark all exported DeltaFiles as acknowledged by default. The result set is limited to 100 errored DeltaFiles by default.`,
	SilenceUsage: true,
	RunE:         executeExportErrors,
}

var exportedErrorsPath, acknowledgeReason string

func executeExportErrors(cmd *cobra.Command, _ []string) error {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return clientError(err)
	}
	skipAcknowledged, _ := cmd.Flags().GetBool("no-acknowledge")
	limit, _ := cmd.Flags().GetInt("limit")
	maxSize, _ := cmd.Flags().GetString("max-size")
	err = validateDataSize(maxSize)
	if err != nil {
		return wrapInError("invalid max-size", err)
	}

	req := api.ExportErrorsRequest{
		Acknowledge: !skipAcknowledged,
		Limit:       limit,
		Reason:      acknowledgeReason,
		MaxSize:     maxSize,
	}
	body, suggestedFilename, err := client.ExportErroredDeltaFile(req)
	if err != nil {
		if errors.Is(err, api.ErrNoneFound) {
			return err
		}
		return fmt.Errorf("failed to export errored DeltaFiles: %w", err)
	}
	defer func(body io.ReadCloser) {
		_ = body.Close()
	}(body)

	// Use suggested filename if no output path specified
	if exportedErrorsPath == "" {
		exportedErrorsPath = suggestedFilename
	}

	file, err := os.Create(exportedErrorsPath)
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

	fmt.Printf("Exported up to %d errored DeltaFiles (total size of %d bytes) to %s\n", limit, bytesWritten, exportedErrorsPath)
	return nil
}

var errorResumeCmd = &cobra.Command{
	Use:   "resume",
	Short: "Resume errored DeltaFiles",
	Long: `Resume errored DeltaFiles matching the filter criteria.
Shows count and prompts for confirmation before executing.`,
	SilenceUsage: true,
	RunE:         runErrorResume,
}

func runErrorResume(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	searchParams.stage = "ERROR"
	searchParams.errorAcknowledged.Set("no")

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	force, _ := cmd.Flags().GetBool("force")

	count, err := countMatchingDeltaFiles(filter)
	if err != nil {
		return wrapInError("Error counting errored DeltaFiles", err)
	}

	if count == 0 {
		fmt.Println("No errored DeltaFiles match the criteria.")
		return nil
	}

	if !force {
		prompt := fmt.Sprintf("Resume %d errored DeltaFile(s)?", count)
		if !ConfirmPrompt(prompt) {
			return nil
		}
	}

	return executeResume(filter)
}

var errorReplayCmd = &cobra.Command{
	Use:   "replay",
	Short: "Replay errored DeltaFiles",
	Long: `Replay errored DeltaFiles matching the filter criteria.
Use -A/--acknowledge to also acknowledge the original errors.`,
	SilenceUsage: true,
	RunE:         runErrorReplay,
}

func runErrorReplay(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	searchParams.stage = "ERROR"
	searchParams.replayable.Set("yes")
	searchParams.errorAcknowledged.Set("no")

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	force, _ := cmd.Flags().GetBool("force")
	acknowledgeAll, _ := cmd.Flags().GetBool("acknowledge")
	reason, _ := cmd.Flags().GetString("reason")

	if acknowledgeAll && reason == "" {
		return fmt.Errorf("--reason is required when using --acknowledge")
	}

	count, err := countMatchingDeltaFiles(filter)
	if err != nil {
		return wrapInError("Error counting errored DeltaFiles", err)
	}

	if count == 0 {
		fmt.Println("No replayable errored DeltaFiles match the criteria.")
		return nil
	}

	action := "Replay"
	if acknowledgeAll {
		action = "Replay and acknowledge"
	}

	if !force {
		prompt := fmt.Sprintf("%s %d errored DeltaFile(s)?", action, count)
		if !ConfirmPrompt(prompt) {
			return nil
		}
	}

	if acknowledgeAll {
		return executeReplayAndAcknowledge(filter, reason)
	}
	return executeReplay(filter)
}

var errorAcknowledgeCmd = &cobra.Command{
	Use:   "acknowledge",
	Short: "Acknowledge errored DeltaFiles",
	Long: `Acknowledge errors on DeltaFiles matching the filter criteria.
Requires -r/--reason to specify the acknowledgement reason.`,
	SilenceUsage: true,
	RunE:         runErrorAcknowledge,
}

func runErrorAcknowledge(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	searchParams.stage = "ERROR"
	searchParams.errorAcknowledged.Set("no")

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	force, _ := cmd.Flags().GetBool("force")
	reason, _ := cmd.Flags().GetString("reason")

	if reason == "" {
		return fmt.Errorf("--reason is required for acknowledge command")
	}

	count, err := countMatchingDeltaFiles(filter)
	if err != nil {
		return wrapInError("Error counting errored DeltaFiles", err)
	}

	if count == 0 {
		fmt.Println("No unacknowledged errored DeltaFiles match the criteria.")
		return nil
	}

	if !force {
		prompt := fmt.Sprintf("Acknowledge %d errored DeltaFile(s)?", count)
		if !ConfirmPrompt(prompt) {
			return nil
		}
	}

	return executeAcknowledge(filter, reason)
}

func countMatchingDeltaFiles(filter searchFilter) (int, error) {
	gqlFilter := filter.toGraphQLFilter()
	limit := 0
	offset := 0
	response, err := graphql.DeltaFiles(&offset, &limit, gqlFilter, filter.order)
	if err != nil {
		return 0, err
	}
	return *response.DeltaFiles.TotalCount, nil
}

func executeResume(filter searchFilter) error {
	gqlFilter := filter.toGraphQLFilter()
	response, err := graphql.ResumeMatching(*gqlFilter, nil)
	if err != nil {
		return wrapInError("Resume failed", err)
	}

	successCount := 0
	failCount := 0
	for _, result := range response.ResumeMatching {
		if result.Success {
			successCount++
		} else {
			failCount++
			fmt.Printf("Failed to resume %s: %s\n", result.Did, *result.Error)
		}
	}

	fmt.Printf("Resumed %d DeltaFile(s)", successCount)
	if failCount > 0 {
		fmt.Printf(", %d failed", failCount)
	}
	fmt.Println()
	return nil
}

func executeReplay(filter searchFilter) error {
	gqlFilter := filter.toGraphQLFilter()
	response, err := graphql.ReplayMatching(*gqlFilter, nil, nil)
	if err != nil {
		return wrapInError("Replay failed", err)
	}

	successCount := 0
	failCount := 0
	for _, result := range response.ReplayMatching {
		if result.Success {
			successCount++
		} else {
			failCount++
			fmt.Printf("Failed to replay %s: %s\n", result.Did, *result.Error)
		}
	}

	fmt.Printf("Replayed %d DeltaFile(s)", successCount)
	if failCount > 0 {
		fmt.Printf(", %d failed", failCount)
	}
	fmt.Println()
	return nil
}

func executeAcknowledge(filter searchFilter, reason string) error {
	gqlFilter := filter.toGraphQLFilter()
	response, err := graphql.AcknowledgeMatching(*gqlFilter, &reason)
	if err != nil {
		return wrapInError("Acknowledge failed", err)
	}

	successCount := 0
	failCount := 0
	for _, result := range response.AcknowledgeMatching {
		if result.Success {
			successCount++
		} else {
			failCount++
			fmt.Printf("Failed to acknowledge %s: %s\n", result.Did, *result.Error)
		}
	}

	fmt.Printf("Acknowledged %d DeltaFile(s)", successCount)
	if failCount > 0 {
		fmt.Printf(", %d failed", failCount)
	}
	fmt.Println()
	return nil
}

func executeReplayAndAcknowledge(filter searchFilter, reason string) error {
	gqlFilter := filter.toGraphQLFilter()
	response, err := graphql.ReplayAndAcknowledgeMatching(*gqlFilter, reason, nil, nil)
	if err != nil {
		return wrapInError("Replay and acknowledge failed", err)
	}

	successCount := 0
	failCount := 0
	for _, result := range response.ReplayAndAcknowledgeMatching {
		if result.Success {
			successCount++
		} else {
			failCount++
			fmt.Printf("Failed to replay and acknowledge %s: %s\n", result.Did, *result.Error)
		}
	}

	fmt.Printf("Replayed and acknowledged %d DeltaFile(s)", successCount)
	if failCount > 0 {
		fmt.Printf(", %d failed", failCount)
	}
	fmt.Println()
	return nil
}

var dataSizePattern = regexp.MustCompile(`^([+\-]?\d+)([a-zA-Z]{0,2})$`)

var validDataUnits = map[string]bool{
	"":   true, // bytes (default)
	"B":  true,
	"KB": true,
	"MB": true,
	"GB": true,
	"TB": true,
	"PB": true,
}

func validateDataSize(size string) error {
	trimmed := strings.ReplaceAll(size, " ", "")

	matches := dataSizePattern.FindStringSubmatch(trimmed)
	if matches == nil {
		return fmt.Errorf("'%s' does not match data size pattern (expected format: number with optional unit like 100MB, 1GB)", size)
	}

	// matches[1] is the number, matches[2] is the unit suffix
	amount := matches[1]
	unit := strings.ToUpper(matches[2])

	// Validate the number can be parsed
	num, err := strconv.ParseInt(amount, 10, 64)
	if err != nil {
		return fmt.Errorf("invalid number in size '%s': %v", size, err)
	}

	if num < 1 {
		return fmt.Errorf("max size '%s' is invalid, it must be greater than 0", size)
	}

	if !validDataUnits[unit] {
		return fmt.Errorf("invalid data unit '%s' in size '%s' (valid units: B, KB, MB, GB, TB, PB)", unit, size)
	}

	return nil
}

func init() {
	rootCmd.AddCommand(errorCmd)
	errorCmd.AddCommand(errorViewCmd, errorListCmd, exportErrorsCmd, errorResumeCmd, errorReplayCmd, errorAcknowledgeCmd)

	searchParams.addSearchFlagsAndCompletions(errorViewCmd)
	errorViewCmd.Flags().MarkHidden("stage")
	errorViewCmd.Flags().MarkHidden("error-acknowledged")
	errorViewCmd.Flags().MarkHidden("filtered")
	errorViewCmd.Flags().MarkHidden("filtered-cause")
	errorViewCmd.Flags().Bool("all", false, "Show all errors (acknowledged and unacknowledged)")
	errorViewCmd.Flags().Bool("acknowledged", false, "Show only acknowledged errors")

	searchParams.addSearchFlagsAndCompletions(errorListCmd)
	errorListCmd.Flags().MarkHidden("stage")
	errorListCmd.Flags().MarkHidden("error-acknowledged")
	errorListCmd.Flags().MarkHidden("filtered")
	errorListCmd.Flags().MarkHidden("filtered-cause")
	errorListCmd.Flags().Bool("all", false, "Show all errors (acknowledged and unacknowledged)")
	errorListCmd.Flags().Bool("acknowledged", false, "Show only acknowledged errors")
	errorListCmd.Flags().BoolP("plain", "p", false, "Show plain table output")
	errorListCmd.Flags().Bool("json", false, "Show JSON output")
	errorListCmd.Flags().Bool("yaml", false, "Show YAML output")
	errorListCmd.Flags().Int("limit", 100, "Limit the number of results")
	errorListCmd.Flags().Int("offset", 0, "Offset the results")
	errorListCmd.Flags().BoolP("verbose", "v", false, "Show verbose output (JSON and YAML only)")
	errorListCmd.Flags().Bool("terse", false, "Hide table header and footer")

	exportErrorsCmd.Flags().IntP("limit", "l", 100, "Maximum number of errors to export")
	exportErrorsCmd.Flags().StringP("max-size", "s", "100MB", "Maximum total size of exported data")
	exportErrorsCmd.Flags().BoolP("no-acknowledge", "n", false, "Do not acknowledge the errors for exported DeltaFiles")
	exportErrorsCmd.Flags().StringVarP(&exportedErrorsPath, "output", "o", "", "output file name")
	exportErrorsCmd.Flags().StringVarP(&acknowledgeReason, "reason", "r", "", "acknowledge reason")

	// Resume subcommand flags
	searchParams.addSearchFlagsAndCompletions(errorResumeCmd)
	errorResumeCmd.Flags().MarkHidden("stage")
	errorResumeCmd.Flags().MarkHidden("error-acknowledged")
	errorResumeCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")

	// Replay subcommand flags
	searchParams.addSearchFlagsAndCompletions(errorReplayCmd)
	errorReplayCmd.Flags().MarkHidden("stage")
	errorReplayCmd.Flags().MarkHidden("replayable")
	errorReplayCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")
	errorReplayCmd.Flags().BoolP("acknowledge", "A", false, "Also acknowledge errors after replay")
	errorReplayCmd.Flags().StringP("reason", "r", "", "Acknowledge reason (required with -A)")

	// Acknowledge subcommand flags
	searchParams.addSearchFlagsAndCompletions(errorAcknowledgeCmd)
	errorAcknowledgeCmd.Flags().MarkHidden("stage")
	errorAcknowledgeCmd.Flags().MarkHidden("error-acknowledged")
	errorAcknowledgeCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")
	errorAcknowledgeCmd.Flags().StringP("reason", "r", "", "Acknowledge reason (required)")
	errorAcknowledgeCmd.MarkFlagRequired("reason")
}
