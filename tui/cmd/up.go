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

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var upCmd = &cobra.Command{
	Use:   "up",
	Short: "Start or upgrade DeltaFi cluster",
	Long: `Start or upgrade the DeltaFi cluster with the current configuration.

This command will:
- Create a new DeltaFi cluster if none exists
- Update an existing cluster with configuration changes
- Perform version upgrades with automatic snapshot creation
- Validate cluster health after startup

Examples:
  deltafi up                    # Start/update with confirmation prompts
  deltafi up --force           # Skip upgrade confirmation prompts`,
	SilenceUsage:  true,
	SilenceErrors: true,
	GroupID:       "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {

		force, _ := cmd.Flags().GetBool("force")

		return Up(force)
	},
}

func Up(force bool) error {
	// Get the TUI version
	tuiVersion := app.GetSemanticVersion()

	activeVersion := GetRunningVersion()

	upgrade := false
	if activeVersion != nil && activeVersion.Compare(tuiVersion) != 0 {

		if activeVersion.Compare(tuiVersion) == 1 {
			fmt.Println(styles.ErrorStyle.Render(fmt.Sprintf("Danger: DeltaFi will be DOWNGRADED from (%s) to (%s).  Downgrades to prior versions are not supported and void the warranty of the DeltaFi database integrity.", activeVersion, tuiVersion)))
		} else {
			fmt.Println(styles.WarningStyle.Render(fmt.Sprintf("Warning: DeltaFi will be upgraded from (%s) to (%s), which may have irreversable effects", activeVersion, tuiVersion)))
		}

		if !force {
			if !components.SimpleContinuePrompt("Continue with this upgrade?") {
				return fmt.Errorf("upgrade cancelled")
			}
		}
		upgrade = true
	} else {
		fmt.Println(styles.InfoStyle.Render(fmt.Sprintf("DeltaFi version: %s", tuiVersion)))
	}

	if upgrade {
		reason := fmt.Sprintf("Initiating upgrade from %s to %s", activeVersion, tuiVersion)
		if app.IsRunning() {
			app.SendEvent(api.NewEvent().Info().WithSummary(reason))

			err := writeSnapshot(activeVersion.String(), reason)
			if err != nil {
				fmt.Println(styles.WarningStyle.Render(fmt.Sprintf("System snapshot cannot be created: %s", err)))
				if !force {
					if !components.SimpleContinuePrompt("Continue with this upgrade?") {
						return fmt.Errorf("upgrade cancelled")
					}
				}
			}
		} else if !force {
			fmt.Println(styles.WarningStyle.Render("DeltaFi is not running - cannot create pre-upgrade event or snapshot"))
			if !components.SimpleContinuePrompt("Continue with this upgrade?") {
				return fmt.Errorf("upgrade cancelled")
			}
		}
		err := app.GetOrchestrator().Migrate(activeVersion)
		if err != nil {
			return fmt.Errorf(styles.ErrorStyle.Render(fmt.Sprintf("System migration failed: %s", err)))
		}
	} else {
		app.SendEvent(api.NewEvent().Info().WithSummary("Initiating orchestration update"))
	}

	orchestrator := app.GetOrchestrator()
	err := orchestrator.Up([]string{})
	if err != nil {
		app.SendEvent(api.NewEvent().Error().WithSummary("Failed to complete orchestration update").WithContent(err.Error()))
		return err
	}

	config := app.GetInstance().GetConfig()

	config.SetCoreVersion(tuiVersion)

	if app.GetOrchestrationMode() == orchestration.Kind {
		perr := app.SetAdminPassword("password")
		if perr != nil {
			fmt.Println(styles.FAIL("Failed to set admin password"))
		} else {
			fmt.Println(styles.INFO("Admin password set to 'password'"))
		}
	}

	if upgrade {
		fmt.Println(styles.OK(fmt.Sprintf("DeltaFi core upgraded from %s to %s", activeVersion, tuiVersion)))
	} else {
		fmt.Println(styles.OK("Orchestration update completed"))
	}

	event := api.NewEvent().Success()
	if upgrade {
		event.WithSummary(fmt.Sprintf("Orchestration upgrade from %s to %s completed", activeVersion, tuiVersion))
		event.Important()
	} else {
		event.WithSummary("Orchestration update completed")
	}

	app.SendEvent(event)

	return nil
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
	upCmd.Flags().BoolP("force", "f", false, "Skip upgrade confirmation prompts and proceed automatically")
}
