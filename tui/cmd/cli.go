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
	"os/exec"
	"path/filepath"

	// "github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var deprecatedCliCmd = &cobra.Command{
	Use:                "cli",
	Short:              styles.WarningStyle.Render("[DEPRECATED]") + " Invoke the original DeltaFi CLI commands",
	Long:               "[DEPRECATED] Invoke the original DeltaFi CLI commands",
	SilenceUsage:       true,
	SilenceErrors:      true,
	DisableFlagParsing: true,
	GroupID:            "legacy",
	Run: func(cmd *cobra.Command, args []string) {

		env := os.Environ()
		mode := "CLUSTER"
		if app.GetOrchestrationMode() == orchestration.Compose {
			mode = "STANDALONE"
		}
		env = append(env, "DELTAFI_MODE="+mode)

		executable := filepath.Join(app.GetDistroPath(), "deltafi-cli", "deltafi")
		if app.GetOrchestrationMode() == orchestration.Kind {
			os.Setenv("DELTAFI_WRAPPER", "true")
			env = append(env, "DELTAFI_WRAPPER=true")
			executable = filepath.Join(app.GetDistroPath(), "orchestration", "kind", "cluster")
		}

		c := *exec.Command(executable, args...)
		c.Env = env
		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		c.Run()
	},
}

var deprecatedClusterCmd = &cobra.Command{
	Use:                "cluster",
	Short:              styles.WarningStyle.Render("[DEPRECATED]") + " Invoke the original DeltaFi KinD cluster controls",
	Long:               "[DEPRECATED] Invoke the original DeltaFi KinD cluster controls",
	SilenceUsage:       true,
	SilenceErrors:      true,
	DisableFlagParsing: true,
	GroupID:            "legacy",
	Run: func(cmd *cobra.Command, args []string) {

		env := os.Environ()

		if app.GetOrchestrationMode() != orchestration.Kind {
			fmt.Println("This command is only available in KinD orchestration mode")
			return
		}

		mode := "CLUSTER"
		env = append(env, "DELTAFI_MODE="+mode)

		executable := filepath.Join(app.GetDistroPath(), "orchestration", "kind", "cluster")

		c := *exec.Command(executable, args...)
		c.Env = env
		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		c.Run()
	},
}

var deprecatedComposeCmd = &cobra.Command{
	Use:                "compose",
	Short:              styles.WarningStyle.Render("[DEPRECATED]") + " Invoke the original DeltaFi compose controls",
	Long:               "[DEPRECATED] Invoke the original DeltaFi compose controls",
	SilenceUsage:       true,
	SilenceErrors:      true,
	DisableFlagParsing: true,
	GroupID:            "legacy",
	Run: func(cmd *cobra.Command, args []string) {

		env := os.Environ()

		if app.GetOrchestrationMode() != orchestration.Compose {
			fmt.Println("This command is only available in Compose orchestration mode")
			return
		}

		mode := "STANDALONE"
		env = append(env, "DELTAFI_MODE="+mode)

		executable := filepath.Join(app.GetDistroPath(), "orchestration", "compose", "compose")

		c := *exec.Command(executable, args...)
		c.Env = env
		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		c.Run()
	},
}

func init() {
	rootCmd.AddCommand(deprecatedCliCmd)
	rootCmd.AddCommand(deprecatedClusterCmd)
	rootCmd.AddCommand(deprecatedComposeCmd)
}
