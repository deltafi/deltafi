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

	"github.com/deltafi/tui/internal/app"
)

// statusCmd represents the status command
var versionsCmd = &cobra.Command{
	Use:     "versions",
	Short:   "List version information for all running containers",
	Long:    `List version information for all running containers`,
	GroupID: "orchestration",
	Run: func(cmd *cobra.Command, args []string) {

		RequireRunningDeltaFi()

		app := app.GetInstance()
		client := app.GetAPIClient()

		versions, err := client.Versions()

		if err != nil {
			fmt.Println(err)
			return
		}

		plain := cmd.Flags().Lookup("plain").Value.String() == "true"

		if val, _ := cmd.Flags().GetBool("brief"); val {
			renderAsSimpleTable(versions.ToJoinedTable(), plain)
		} else {
			renderAsSimpleTable(versions.ToTable(), plain)
		}
	},
}

func init() {
	rootCmd.AddCommand(versionsCmd)

	versionsCmd.Flags().BoolP("brief", "b", false, "Brief output omitting container name and joining image and tag")
	versionsCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
}
