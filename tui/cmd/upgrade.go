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

var upgradeCmd = &cobra.Command{
	Use:   "upgrade [version]",
	Short: "Upgrade DeltaFi system to a specific version",
	Long: `Upgrade DeltaFi system to the latest version.
	
	Use upgrade list command to see available versions.`,
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
}
