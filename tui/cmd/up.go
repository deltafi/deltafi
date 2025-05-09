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
	"os"

	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var upCmd = &cobra.Command{
	Use:   "up",
	Short: "Start up or update the DeltaFi cluster",
	Long: `Start up or update the DeltaFi cluster.

	This command will create or update the DeltaFi cluster with the current configuration.
	`,
	SilenceUsage:  true,
	SilenceErrors: true,
	GroupID:       "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {
		// Get the TUI version
		tuiVersion := app.Version

		// Get the running DeltaFi version
		resp, err := graphql.Version()
		coreVersion := ""
		if err == nil {
			coreVersion = resp.Version
		}

		upgrade := false
		if coreVersion != "" && coreVersion != tuiVersion {
			fmt.Println(app.GetInstance().FormatWarning(fmt.Sprintf("Warning: DeltaFi will be upgraded from (%s) to (%s), which may have irreversable effects", coreVersion, tuiVersion)))

			// Create a styled prompt
			promptStyle := lipgloss.NewStyle().
				Foreground(lipgloss.Color("205")).
				Bold(true)

			fmt.Print(promptStyle.Render("Continue with deployment? [y/N] "))

			var response string
			fmt.Scanln(&response)

			if response != "y" && response != "Y" {
				return fmt.Errorf("deployment cancelled")
			}
			upgrade = true
		} else {
			fmt.Println(app.GetInstance().FormatInfo(fmt.Sprintf("DeltaFi version: %s", tuiVersion)))
		}

		if upgrade {
			reason := fmt.Sprintf("Initiating upgrade from %s to %s", coreVersion, tuiVersion)
			app.SendEvent(api.NewEvent().Info().WithSummary(reason))

			err = writeSnapshot(coreVersion, reason)
			if err != nil {
				return wrapInError("Error writing snapshot", err)
			}
		} else {
			app.SendEvent(api.NewEvent().Info().WithSummary("Initiating orchestration update"))
		}

		err = app.GetOrchestrator().Up(args)
		if err != nil {
			app.SendEvent(api.NewEvent().Error().WithSummary("Failed to complete orchestration update").WithContent(err.Error()))
			return err
		}

		event := api.NewEvent().Success()
		if upgrade {
			event.WithSummary(fmt.Sprintf("Orchestration upgrade from %s to %s completed", coreVersion, tuiVersion))
			event.Important()
		} else {
			event.WithSummary("Orchestration update completed")
		}

		app.SendEvent(event)

		return nil
	},
}

func writeSnapshot(version string, reason string) error {
	// initiate snapshot
	resp, err := graphql.SnapshotSystem(&reason)
	if err != nil {
		return wrapInError("Error creating system snapshot", err)
	}

	fullResp, err := graphql.GetSystemSnapshot(resp.SnapshotSystem.GetId())
	if err != nil {
		return err
	}

	sitePath := app.GetInstance().GetConfig().SiteDirectory
	fileName := fmt.Sprintf("%s/snapshot-%s-%s.json", sitePath, version, resp.SnapshotSystem.GetId())
	content, err := asJSON(fullResp)
	if err != nil {
		return err
	}

	err = os.WriteFile(fileName, []byte(content), 0644)

	return err
}

func init() {
	rootCmd.AddCommand(upCmd)
}
