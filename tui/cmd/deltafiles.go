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

func init() {
	rootCmd.AddCommand(deltafilesCmd)
	deltafilesCmd.AddCommand(deltafilesViewCmd)
	deltafilesCmd.AddCommand(deltafilesListCmd)
	searchParams.addSearchFlagsAndCompletions(deltafilesViewCmd)

	searchParams.addSearchFlagsAndCompletions(deltafilesListCmd)
	deltafilesListCmd.Flags().BoolP("plain", "p", false, "Show plain table output")
	deltafilesListCmd.Flags().Bool("json", false, "Show JSON output")
	deltafilesListCmd.Flags().Bool("yaml", false, "Show YAML output")
	deltafilesListCmd.Flags().Int("limit", 100, "Limit the number of results")
	deltafilesListCmd.Flags().Int("offset", 0, "Offset the results")
	deltafilesListCmd.Flags().BoolP("verbose", "v", false, "Show verbose output (JSON and YAML only)")
	deltafilesListCmd.Flags().Bool("terse", false, "Hide table header and footer")
}
