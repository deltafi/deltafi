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

	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var lookupCmd = &cobra.Command{
	Use:          "lookup",
	Short:        "Command line access to the running DeltaFi Postgres instance for the lookup service",
	Long:         `Command line access to the running DeltaFi Postgres instance for the lookup service`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var lookupCliCmd = &cobra.Command{
	Use:   "cli",
	Short: "Start an interactive Postgres CLI session for the lookup service",
	Long:  "Start an interactive Postgres CLI session for the lookup service",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, error := app.GetOrchestrator().GetPostgresLookupCmd(args)
		if error != nil {
			return error
		}

		return executeShellCommand(c)
	},
}

func init() {
	rootCmd.AddCommand(lookupCmd)
	lookupCmd.AddCommand(lookupCliCmd)
}
