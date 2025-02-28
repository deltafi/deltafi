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
	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var destroyCmd = &cobra.Command{
	Use:   "destroy",
	Short: "Destroy the DeltaFi cluster",
	Long: `Destroy the DeltaFi cluster.

	This is a destructive operation and will result in the loss of all persistent data.
	`,
	SilenceUsage:  true,
	SilenceErrors: true,
	GroupID:       "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		return app.GetOrchestrator().Destroy(args)

	},
}

func init() {
	rootCmd.AddCommand(destroyCmd)
}
