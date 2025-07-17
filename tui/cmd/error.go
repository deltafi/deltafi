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
	"fmt"

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
  deltafi errored view                    # View unacknowledged errors
  deltafi errored view --all              # View all errors (acknowledged and unacknowledged)
  deltafi errored view --error-acknowledged yes  # View all errors including acknowledged
  deltafi errored summary                 # Show error summary by flow
  deltafi errored list                    # List recent errors`,
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
	Short:        "View DeltaFile errors",
	Long:         `View DeltaFile errors using various criteria such as error messages, flow names, or time ranges. By default shows only unacknowledged errors. Use --all to view all errors.`,
	SilenceUsage: true,
	RunE:         runErrorView,
}

func runErrorView(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("search command does not accept positional arguments: %v", args)
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

func init() {
	rootCmd.AddCommand(errorCmd)
	errorCmd.AddCommand(errorViewCmd)
	errorCmd.AddCommand(errorListCmd)

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
}
