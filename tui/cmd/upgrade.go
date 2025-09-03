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
	"context"
	"fmt"
	"os"
	"os/exec"
	"sort"
	"strings"
	"syscall"
	"time"

	"github.com/Masterminds/semver/v3"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/containers"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

const (
	distroRepository = "docker.io/deltafi/distro"
)

var listVersionsCmd = &cobra.Command{
	Use:   "list",
	Short: "List available newer versions",
	Long:  `List available newer versions of DeltaFi`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return listUpgradeVersions()
	},
}

var changelogCmd = &cobra.Command{
	Use:   "changelog [version]",
	Short: "Show changelog for a specific version or all newer versions",
	Long:  `Show the changelog for a specific DeltaFi version. If no version is specified, shows changelogs for all newer versions.`,
	Args:  cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(args) == 0 {
			return showAllChangelogs()
		}
		return showChangelog(args[0])
	},
}

func listUpgradeVersions() error {
	currentVersion := GetRunningVersion()
	if currentVersion == nil {
		return fmt.Errorf("could not determine current version")
	}

	versions, err := containers.ListNewerVersions(context.Background(), distroRepository, currentVersion, 20)
	if err != nil {
		return fmt.Errorf("error listing upgrade versions: %w", err)
	}

	if len(versions) == 0 {
		fmt.Println("No newer versions available")
		return nil
	}

	// Sort versions in descending order
	sort.Slice(versions, func(i, j int) bool {
		return versions[i].Compare(versions[j]) > 0
	})

	fmt.Println("Available newer versions:")
	for _, v := range versions {
		fmt.Printf("  %s\n", v.String())
	}
	return nil
}

func showChangelog(version string) error {
	// Try to validate as semantic version, but don't fail if it's not
	// This allows for tags like "latest", "main", etc.
	_, err := semver.NewVersion(version)
	if err != nil {
		// Not a semantic version, but that's okay for tags like "latest"
		fmt.Printf("Note: %s is not a semantic version, but will try to fetch changelog anyway\n", version)
	}

	// Get annotations for the specified version
	annotations, err := containers.GetImageAnnotations(context.Background(), distroRepository, version)
	if err != nil {
		return fmt.Errorf("error getting image annotations: %w", err)
	}

	// Look for the changelog annotation
	changelog, exists := annotations["org.opencontainers.image.description"]
	if !exists || changelog == "" {
		return fmt.Errorf("no changelog found for version %s", version)
	}

	// Render the changelog markdown
	renderedChangelog := renderMarkdown(changelog, getTerminalWidth()-8)
	fmt.Println(renderedChangelog)

	return nil
}

func showAllChangelogs() error {
	currentVersion := GetRunningVersion()
	if currentVersion == nil {
		return fmt.Errorf("could not determine current version")
	}

	// Get all newer versions
	versions, err := containers.ListNewerVersions(context.Background(), distroRepository, currentVersion, 0) // 0 means no limit
	if err != nil {
		return fmt.Errorf("error listing newer versions: %w", err)
	}

	if len(versions) == 0 {
		fmt.Println("No newer versions available")
		return nil
	}

	// Sort versions in descending order (newest first)
	sort.Slice(versions, func(i, j int) bool {
		return versions[i].Compare(versions[j]) > 0
	})

	// Show changelog for each version
	for i, version := range versions {
		versionStr := version.String()

		// Add separator between versions (except before the first one)
		if i > 0 {
			fmt.Println()
			fmt.Println(strings.Repeat("─", getTerminalWidth()))
			fmt.Println()
		}

		fmt.Printf("Changelog for version %s:\n", versionStr)
		fmt.Println()

		// Get and display changelog for this version
		annotations, err := containers.GetImageAnnotations(context.Background(), distroRepository, versionStr)
		if err != nil {
			fmt.Printf("Error getting changelog for version %s: %v\n", versionStr, err)
			continue
		}

		changelog, exists := annotations["org.opencontainers.image.description"]
		if !exists || changelog == "" {
			fmt.Printf("No changelog found for version %s\n", versionStr)
			continue
		}

		// Render the changelog markdown
		renderedChangelog := renderMarkdown(changelog, getTerminalWidth()-8)
		fmt.Println(renderedChangelog)
	}

	return nil
}

// Simple prompt model for upgrade confirmation
type upgradePromptModel struct {
	upgradeVersion string
	confirmed      bool
}

func initialUpgradePromptModel(version string) upgradePromptModel {
	return upgradePromptModel{
		upgradeVersion: version,
		confirmed:      false,
	}
}

func (m upgradePromptModel) Init() tea.Cmd {
	return nil
}

func (m upgradePromptModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "y", "Y":
			m.confirmed = true
			return m, tea.Quit
		case "n", "N", "ctrl+c", "q", "Q", "esc":
			m.confirmed = false
			return m, tea.Quit
		}
	}
	return m, nil
}

func (m upgradePromptModel) View() string {
	return fmt.Sprintf("\nProceed with upgrade to version %s? (Y/n): ", styles.SuccessStyle.Render(m.upgradeVersion))
}

var upgradeCmd = &cobra.Command{
	Use:   "upgrade [version]",
	Short: "Upgrade DeltaFi system to a specific version",
	Long: `Upgrade DeltaFi system to the latest version.
	
	Use upgrade list command to see available versions.
	
	Use --safe flag to perform a safe upgrade that temporarily disables ingress
	and shows a dashboard for monitoring before proceeding.`,
	GroupID: "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {
		safeMode, _ := cmd.Flags().GetBool("safe")

		if len(args) == 0 {
			return fmt.Errorf("please specify a version to upgrade to")
		}

		if len(args) == 1 {
			copyFileWithPerms(app.GetInstallDir()+"/deltafi", app.GetInstallDir()+"/.deltafi.old")
			execPath := app.GetInstallDir() + "/.deltafi.old"

			command := []string{".deltafi.old", "upgrade", args[0], "shadow"}
			if safeMode {
				command = append(command, "--safe")
			}

			err := syscall.Exec(execPath, command, os.Environ())
			if err != nil {
				return fmt.Errorf("error executing upgrade: %w", err)
			}
			fmt.Println("exeunt...shant be seen")
		}

		if len(args) > 2 || args[1] != "shadow" {
			return fmt.Errorf("please specify only one version to upgrade to")
		}

		version := args[0]

		// Handle safe upgrade mode
		if safeMode {
			return performSafeUpgrade(version)
		}

		return performUpgrade(version)
	},
}

func performSafeUpgrade(version string) error {
	// Check if DeltaFi is running
	if !IsDeltafiRunning() {
		fmt.Println("DeltaFi is not running. Safe mode has no effect.")
		return performUpgrade(version)
	}

	// Get current ingressEnabled value
	originalIngressEnabled, err := getIngressEnabled()
	if err != nil {
		return fmt.Errorf("error getting ingressEnabled property: %w", err)
	}

	// If ingress is enabled, disable it
	if originalIngressEnabled {
		err = setIngressEnabled(false)
		if err != nil {
			fmt.Println(styles.FAIL("Error disabling ingress"))
			return fmt.Errorf("error disabling ingress: %w", err)
		}
		fmt.Println(styles.OK("Ingress disabled for safe upgrade"))
	} else {
		fmt.Println(styles.OK("Ingress is already disabled for safe upgrade"))
	}

	// Show dashboard for monitoring
	fmt.Println("Starting dashboard for pre-upgrade monitoring...")
	time.Sleep(3 * time.Second)

	// Run the dashboard programmatically
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		// Restore ingress if we disabled it
		if originalIngressEnabled {
			setIngressEnabled(true)
		}
		return clientError(err)
	}

	p := tea.NewProgram(initialModel(client, 1*time.Second, "continue upgrade"), tea.WithAltScreen())
	if _, err := p.Run(); err != nil {
		// Restore ingress if we disabled it
		if originalIngressEnabled {
			setIngressEnabled(true)
		}
		return fmt.Errorf("dashboard exited with error: %w", err)
	}

	// Show confirmation prompt
	prompt := tea.NewProgram(initialUpgradePromptModel(version))
	finalModel, err := prompt.Run()
	if err != nil {
		// Restore ingress if we disabled it
		if originalIngressEnabled {
			setIngressEnabled(true)
		}
		return fmt.Errorf("error showing upgrade prompt: %w", err)
	}

	// Check if user confirmed the upgrade
	promptModel, ok := finalModel.(upgradePromptModel)
	if !ok || !promptModel.confirmed {
		// Restore ingress if we disabled it
		if originalIngressEnabled {
			setIngressEnabled(true)
		}
		return fmt.Errorf("upgrade aborted by user")
	}

	// Perform the actual upgrade
	fmt.Println("Proceeding with upgrade...")
	err = performUpgrade(version)
	if err != nil {
		// Restore ingress if we disabled it
		if originalIngressEnabled {
			setIngressEnabled(true)
		}
		return err
	}

	// Restore ingress to original value
	if originalIngressEnabled {
		fmt.Println("Restoring ingress to original state...")
		err = setIngressEnabled(true)
		if err != nil {
			return fmt.Errorf("error restoring ingress: %w", err)
		}
		fmt.Println(styles.OK("Ingress restored to original state"))
	}

	fmt.Println(styles.OK("Safe upgrade completed successfully"))
	return nil
}

func performUpgrade(version string) error {
	platform := fmt.Sprintf("%s/%s", app.GetOS(), app.GetArch())
	extractor := containers.NewImageExtractor(app.GetInstallDir())

	err := extractor.ExtractImage(context.Background(), distroRepository, version, platform)
	if err != nil {
		return fmt.Errorf("error extracting image: %w", err)
	}

	fmt.Println(styles.OK("Upgrade downloaded and staged"))

	upper := exec.Command("deltafi", "up")
	upper.Dir = app.GetInstallDir()
	upper.Path = app.GetInstallDir() + "/deltafi"
	upper.Stdout = os.Stdout
	upper.Stderr = os.Stderr
	upper.Stdin = os.Stdin

	err = upper.Run()
	if err != nil {
		return fmt.Errorf("error running upgrade: %w", err)
	}

	fmt.Println(styles.OK("Upgrade complete"))
	return nil
}

func getIngressEnabled() (bool, error) {
	value, err := getProperty("ingressEnabled")
	if err != nil {
		return false, err
	}
	return value == "true", nil
}

func setIngressEnabled(enabled bool) error {
	value := "false"
	if enabled {
		value = "true"
	}
	return setProperty("ingressEnabled", value)
}

func init() {
	rootCmd.AddCommand(upgradeCmd)
	upgradeCmd.AddCommand(listVersionsCmd)
	upgradeCmd.AddCommand(changelogCmd)

	// Add the safe flag
	upgradeCmd.Flags().BoolP("safe", "s", false, "Perform safe upgrade with ingress management and dashboard monitoring")
}
