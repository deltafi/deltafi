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
	"github.com/deltafi/tui/graphql"
	"github.com/spf13/cobra"
)

var systemPlansCmd = &cobra.Command{
	Use:     "system-flow-plans",
	Short:   "Import and export system flow plans",
	Long:    `Import and export system flow plans`,
	GroupID: "flow",
}

var exportSystemPlans = &cobra.Command{
	Use:   "export",
	Short: "Export system flow plans",
	Long:  `Export all the system flow plans`,
	Args:  cobra.ExactArgs(0),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		resp, err := graphql.GetSystemFlowPlans()
		if err != nil {
			return wrapInError("Error fetching system flow plans", err)
		}

		return prettyPrint(cmd, resp.GetAllSystemFlowPlans)
	},
}

var importSystemPlans = &cobra.Command{
	Use:   "import",
	Short: "Import system flow plans",
	Long:  `Import all the system flow plans from the given input file`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		var systemFlowPlans graphql.SystemFlowPlansInput
		var err = parseFile(cmd, &systemFlowPlans)

		if err != nil {
			return err
		}

		resp, err := graphql.ImportSystemPlans(systemFlowPlans)

		if err != nil {
			return wrapInError("Error importing the system flow plans", err)
		}

		return prettyPrint(cmd, resp.SaveSystemFlowPlans)
	},
}

func init() {
	rootCmd.AddCommand(systemPlansCmd)
	systemPlansCmd.AddCommand(exportSystemPlans)
	systemPlansCmd.AddCommand(importSystemPlans)

	AddFormatFlag(exportSystemPlans)
	AddLoadFlags(importSystemPlans)
}
