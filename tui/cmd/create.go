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

var CreateCmd = &cobra.Command{
	Use:     "create",
	Short:   "Creates a DeltaFi resource",
	GroupID: "flow",
	Long: `Creates a DeltaFi resource, such as a new event or snapshot

# Example creating a new event
deltafi create event "my event summary"
`,
	Args: cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		fmt.Println("There is no type " + args[0] + " to create")
		return cmd.Usage()
	},
}

func init() {
	rootCmd.AddCommand(CreateCmd)

	CreateCmd.PersistentFlags().StringP("format", "o", "json", "Output format (json or yaml)")
	_ = CreateCmd.RegisterFlagCompletionFunc("format", formatCompletion)
}
