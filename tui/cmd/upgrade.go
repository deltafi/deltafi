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

var upgradeCmd = &cobra.Command{
	Use:   "upgrade [version]",
	Short: "Upgrade DeltaFi system to a specific version",
	Long: `Upgrade DeltaFi system to the latest version.
	
	Use upgrade list command to see available versions.`,
	GroupID: "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {

		if len(args) == 0 {
			return fmt.Errorf("please specify a version to upgrade to")
		}

		if len(args) == 1 {
			copyFileWithPerms(app.GetInstallDir()+"/deltafi", app.GetInstallDir()+"/.deltafi.old")
			execPath := app.GetInstallDir() + "/.deltafi.old"
			err := syscall.Exec(execPath, []string{".deltafi.old", "upgrade", args[0], "shadow"}, os.Environ())
			if err != nil {
				return fmt.Errorf("error executing upgrade: %w", err)
			}
			fmt.Println("exeunt...shant be seen")
		}

		if len(args) > 2 || args[1] != "shadow" {
			return fmt.Errorf("please specify only one version to upgrade to")
		}

		time.Sleep(1 * time.Second)

		version := args[0]
		semVersion, err := semver.NewVersion(version)
		if err != nil {
			return fmt.Errorf("invalid version (%s): %w", version, err)
		}

		platform := fmt.Sprintf("%s/%s", app.GetOS(), app.GetArch())
		extractor := containers.NewImageExtractor(app.GetInstallDir())

		err = extractor.ExtractImage(context.Background(), distroRepository, semVersion.String(), platform)
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
	},
}

func init() {
	rootCmd.AddCommand(upgradeCmd)
	upgradeCmd.AddCommand(listVersionsCmd)
	upgradeCmd.AddCommand(changelogCmd)
}
