/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var (
	username      string
	password      string
	passwordStdin bool
)

var dockerLoginCmd = &cobra.Command{
	Use:   "docker-login [registry]",
	Short: "Configure Docker registry authentication",
	Long:  `Configure Docker registry authentication for pulling private images in DeltaFi core services.`,
	Example: `  # Login with password from stdin (recommended for AWS ECR)
  aws ecr get-login-password --region us-east-1 | \
    deltafi docker-login --username AWS --password-stdin \
    123456789012.dkr.ecr.us-east-1.amazonaws.com

  # Login with password from environment variable
  deltafi docker-login --username myuser --password "$REGISTRY_PASSWORD" \
    registry.example.com

  # Standard docker registry login
  echo "$DOCKER_PASSWORD" | deltafi docker-login --username myuser \
    --password-stdin docker.io`,
	Args:    cobra.ExactArgs(1),
	GroupID: "orchestration",
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		if app.GetOrchestrationMode() != orchestration.Compose {
			return fmt.Errorf("docker-login command is only available in Compose orchestration mode")
		}
		return nil
	},
	RunE: runDockerLogin,
}

func init() {
	if !isWebTerminal() {
		rootCmd.AddCommand(dockerLoginCmd)
	}
	dockerLoginCmd.Flags().StringVarP(&username, "username", "u", "", "Registry username")
	dockerLoginCmd.Flags().StringVar(&password, "password", "", "Registry password (use --password-stdin instead for better security)")
	dockerLoginCmd.Flags().BoolP("password-stdin", "p", false, "Read password from stdin")
	dockerLoginCmd.MarkFlagsMutuallyExclusive("password", "password-stdin")
	_ = dockerLoginCmd.MarkFlagRequired("username")
}

// normalizeRegistry removes http:// or https:// prefix and path from registry URL
// to ensure consistent storage and matching
func normalizeRegistry(registry string) string {
	registry = strings.TrimSpace(registry)
	registry = strings.TrimPrefix(registry, "https://")
	registry = strings.TrimPrefix(registry, "http://")

	// Remove path component if present (e.g., index.docker.io/v1/ -> index.docker.io)
	if idx := strings.Index(registry, "/"); idx != -1 {
		registry = registry[:idx]
	}

	return registry
}

func runDockerLogin(cmd *cobra.Command, args []string) error {
	registry := normalizeRegistry(args[0])
	passwordStdin, _ = cmd.Flags().GetBool("password-stdin")

	if username == "" {
		return fmt.Errorf("username is required")
	}

	var finalPassword string
	if passwordStdin {
		scanner := bufio.NewScanner(os.Stdin)
		if scanner.Scan() {
			finalPassword = strings.TrimSpace(scanner.Text())
		}
		if err := scanner.Err(); err != nil {
			return fmt.Errorf("failed to read password from stdin: %w", err)
		}
		if finalPassword == "" {
			return fmt.Errorf("no password provided on stdin")
		}
	} else if password != "" {
		finalPassword = password
	} else {
		return fmt.Errorf("password is required: use --password or --password-stdin")
	}

	// Save credentials via orchestrator
	orchestrator := app.GetOrchestrator()
	if err := orchestrator.SaveDockerAuth(registry, username, finalPassword); err != nil {
		return fmt.Errorf("failed to save docker credentials: %w", err)
	}

	fmt.Fprintln(cmd.OutOrStdout(), styles.OK("Successfully configured Docker authentication for registry: "+registry))
	fmt.Fprintln(cmd.OutOrStdout(), styles.INFO("Restart core services for changes to take effect: deltafi down && deltafi up"))

	return nil
}
