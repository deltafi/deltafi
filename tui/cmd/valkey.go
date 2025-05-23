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
	"errors"
	"fmt"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/types"
	"github.com/spf13/cobra"
	"os"
	"os/exec"
)

var valkeyCmd = &cobra.Command{
	Use:          "valkey",
	Short:        "Command line access to the running DeltaFi Valkey instance",
	Long:         `Command line access to the running DeltaFi Valkey instance`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var valkeyCliCmd = &cobra.Command{
	Use:   "cli",
	Short: "Start an interactive Valkey CLI session",
	Long:  "Start an interactive Valkey CLI session",
	RunE: func(cmd *cobra.Command, args []string) error {
		return execCommand([]string{})
	},
}

var valkeyLatencyCmd = &cobra.Command{
	Use:   "latency",
	Short: "Start the Valkey CLI latency command",
	Long:  "Start the Valkey CLI latency command",
	RunE: func(cmd *cobra.Command, args []string) error {
		return execCommand([]string{"--latency"})
	},
}

var valkeyStatCmd = &cobra.Command{
	Use:   "stat",
	Short: "Start the Valkey CLI stat command",
	Long:  "Start the Valkey CLI stat command",
	RunE: func(cmd *cobra.Command, args []string) error {
		return execCommand([]string{"--stat"})
	},
}

var valkeyMonitorCmd = &cobra.Command{
	Use:     "monitor",
	Aliases: []string{"watch"},
	Short:   "Start the Valkey CLI monitor command",
	Long:    "Start the Valkey CLI monitor command",
	RunE: func(cmd *cobra.Command, args []string) error {
		return execCommand([]string{"monitor", "2>&1", "|", "grep", "-v", "\"^OK$\\|AUTH\\|ping\""})
	},
}

func execCommand(cmd []string) error {
	valkeyCliArgs := []string{"valkey-cli", "--no-auth-warning"}
	config := app.GetInstance().GetConfig()
	if config.DeploymentMode == types.Deployment || config.OrchestrationMode != orchestration.Compose {
		valkeyCliArgs = append(valkeyCliArgs, "-a $REDIS_PASSWORD")
	}

	orchestrator := app.GetOrchestrator()
	c, err := orchestrator.GetExecCmd(orchestrator.GetValkeyName(), true, append(valkeyCliArgs, cmd...))
	if err != nil {
		return err
	}

	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr

	err = c.Run()
	if err == nil {
		return nil
	}

	var exitError *exec.ExitError
	if errors.As(err, &exitError) {
		// Ignore common interrupt exit codes
		if exitError.ExitCode() == 130 || exitError.ExitCode() == 2 {
			return nil
		}
	}

	return fmt.Errorf("command execution failed: %w", err)
}

func init() {
	rootCmd.AddCommand(valkeyCmd)
	valkeyCmd.AddCommand(valkeyCliCmd)
	valkeyCmd.AddCommand(valkeyLatencyCmd)
	valkeyCmd.AddCommand(valkeyMonitorCmd)
	valkeyCmd.AddCommand(valkeyStatCmd)
}
