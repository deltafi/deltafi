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

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/spf13/cobra"
)

var kindCmd = &cobra.Command{
	Use:   "kind",
	Short: "Manage KinD cluster",
	Long: `Manage the KinD (Kubernetes in Docker) cluster.

This command is only available when the orchestration mode is set to KinD.
It provides subcommands to start and stop the KinD cluster.`,
	GroupID: "orchestration",
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		if app.GetOrchestrationMode() != orchestration.Kind {
			return fmt.Errorf("kind command is only available in KinD orchestration mode")
		}
		return nil
	},
}

var kindUpCmd = &cobra.Command{
	Use:   "up",
	Short: "Start the KinD cluster",
	Long: `Start the KinD cluster.

This command will:
- Create and start a new KinD cluster if none exists
- Configure the cluster for DeltaFi deployment
- Set up necessary prerequisites`,
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		orchestrator := app.GetOrchestrator()

		// error if the orchestrator is not a KindOrchestrator
		kindOrchestrator, ok := orchestrator.(*orchestration.KindOrchestrator)
		if !ok {
			return fmt.Errorf("orchestrator is not a KindOrchestrator")
		}

		return kindOrchestrator.StartKind()
	},
}

var kindDownCmd = &cobra.Command{
	Use:   "down",
	Short: "Stop and destroy the KinD cluster",
	Long: `Stop and destroy the KinD cluster.

This is a destructive operation that will:
- Stop all services running in the KinD cluster
- Destroy the cluster and all its resources
- Remove all persistent data

WARNING: This will permanently delete all data in the KinD cluster.`,
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		orchestrator := app.GetOrchestrator()
		// error if the orchestrator is not a KindOrchestrator
		kindOrchestrator, ok := orchestrator.(*orchestration.KindOrchestrator)
		if !ok {
			return fmt.Errorf("orchestrator is not a KindOrchestrator")
		}

		if err := kindOrchestrator.StopKind(); err != nil {
			return fmt.Errorf("failed to stop KinD cluster: %w", err)
		}

		// delete data directory if it exists
		if _, err := os.Stat(filepath.Join(app.GetDataDir())); err == nil {
			if err := os.RemoveAll(filepath.Join(app.GetDataDir())); err != nil {
				return fmt.Errorf("failed to delete data directory: %w", err)
			}
		}

		return nil
	},
}

var kindDestroyCmd = &cobra.Command{
	Use:   "destroy",
	Short: "Destroy the KinD cluster",
	Long: `Destroy the KinD cluster.

This is a destructive operation that does everything down does plus:
- removes images
- optionally removes all registries
- Stop all services running in the KinD cluster
- Destroy the cluster and all its resources
- Remove all persistent data

WARNING: This will permanently delete all data in the KinD cluster.`,
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		orchestrator := app.GetOrchestrator()
		// error if the orchestrator is not a KindOrchestrator
		kindOrchestrator, ok := orchestrator.(*orchestration.KindOrchestrator)
		if !ok {
			return fmt.Errorf("orchestrator is not a KindOrchestrator")
		}

		return kindOrchestrator.DestroyKind()
	},
}

var kindShellCmd = &cobra.Command{
	Use:   "shell",
	Short: "Open an interactive shell in the KinD control plane",
	Long: `Open an interactive shell in the KinD control plane.

This command will:
- Connect to the KinD control plane container
- Open a tmux session in the /usr/dev directory
- Provide an interactive development environment

Use Ctrl+B then D to detach from the tmux session.`,
	SilenceUsage:  true,
	SilenceErrors: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Create the docker exec command
		dockerCmd := exec.Command("docker", "exec", "-w", "/usr/dev", "-it", "deltafi-control-plane", "tmux", "-2", "new-session", "-A", "-s", "KinD")
		return executeShellCommand(*dockerCmd)
	},
}

func init() {
	rootCmd.AddCommand(kindCmd)
	kindCmd.AddCommand(kindUpCmd)
	kindCmd.AddCommand(kindDownCmd)
	kindCmd.AddCommand(kindDestroyCmd)
	kindCmd.AddCommand(kindShellCmd)
}
