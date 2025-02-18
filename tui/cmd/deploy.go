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

var deployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Create or update the DeltaFi cluster",
	Long: `Create or update the DeltaFi cluster.

	If there is no cluster running, a new cluster will be created according to the provisioned orchestration mode.

	If a cluster is already running, the cluster will be updated with the latest configuration changes.  This operation is idempotent.`,
	SilenceUsage:  true,
	SilenceErrors: true,
	GroupID:       "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {

		return app.GetOrchestrator().Deploy(args)

	},
}

func init() {
	rootCmd.AddCommand(deployCmd)
}
