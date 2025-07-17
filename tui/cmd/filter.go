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

var filterCmd = &cobra.Command{
	Use:   "filtered",
	Short: "Manage and search filtered DeltaFiles",
	Long: `Manage and search filtered DeltaFiles in the DeltaFi system.

This command provides tools for searching, analyzing, and managing DeltaFiles
that have been filtered during processing. You can search for specific filter
patterns, view filter summaries, and perform bulk operations on filtered files.

Examples:
  deltafi filtered view                    # View filtered DeltaFiles`,
	GroupID:            "deltafile",
	SilenceUsage:       true,
	DisableSuggestions: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var filterViewCmd = &cobra.Command{
	Use:          "view",
	Short:        "View filtered DeltaFiles",
	Long:         `View filtered DeltaFiles using various criteria such as filter messages, flow names, or time ranges`,
	SilenceUsage: true,
	RunE:         runFilterView,
}

func runFilterView(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("filter view command does not accept positional arguments: %v", args)
	}

	searchParams.filtered.Set("yes")

	filter, err := searchParams.GetFilter()
	if err != nil {
		return err
	}

	return runSearchViewer(filter)
}

var filterListCmd = &cobra.Command{
	Use:          "list",
	Short:        "List filtered DeltaFiles",
	Long:         `List filtered DeltaFiles in a simple format.`,
	SilenceUsage: true,
	RunE:         runFilterList,
}

func runFilterList(cmd *cobra.Command, args []string) error {
	RequireRunningDeltaFi()
	if len(args) > 0 {
		return fmt.Errorf("list command does not accept positional arguments: %v", args)
	}

	searchParams.filtered.Set("yes")
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

func init() {
	rootCmd.AddCommand(filterCmd)
	filterCmd.AddCommand(filterViewCmd)
	filterCmd.AddCommand(filterListCmd)
	searchParams.addSearchFlagsAndCompletions(filterViewCmd)
	filterViewCmd.Flags().MarkHidden("stage")
	filterViewCmd.Flags().MarkHidden("filtered")
	filterViewCmd.Flags().MarkHidden("error-acknowledged")
	filterViewCmd.Flags().MarkHidden("error-cause")

	searchParams.addSearchFlagsAndCompletions(filterListCmd)
	filterListCmd.Flags().MarkHidden("stage")
	filterListCmd.Flags().MarkHidden("filtered")
	filterListCmd.Flags().MarkHidden("error-acknowledged")
	filterListCmd.Flags().MarkHidden("error-cause")
	filterListCmd.Flags().BoolP("plain", "p", false, "Show plain table output")
	filterListCmd.Flags().Bool("json", false, "Show JSON output")
	filterListCmd.Flags().Bool("yaml", false, "Show YAML output")
	filterListCmd.Flags().Int("limit", 100, "Limit the number of results")
	filterListCmd.Flags().Int("offset", 0, "Offset the results")
	filterListCmd.Flags().BoolP("verbose", "v", false, "Show verbose output (JSON and YAML only)")
	filterListCmd.Flags().Bool("terse", false, "Hide table header and footer")
}
