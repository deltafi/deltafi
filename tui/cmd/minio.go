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

	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var minioCmd = &cobra.Command{
	Use:          "minio",
	Short:        "Command line access to the running DeltaFi MinIO instance",
	Long:         `Command line access to the running DeltaFi MinIO instance`,
	GroupID:      "deltafi",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var minioCliCmd = &cobra.Command{
	Use:   "cli",
	Short: "Start an interactive MinIO CLI session",
	Long:  "Start an interactive MinIO CLI session",
	RunE: func(cmd *cobra.Command, args []string) error {
		return ExecMinioCommand([]string{"exec bash"})
	},
}

var minioExecCmd = &cobra.Command{
	Use:   "exec",
	Short: "Execute each argument as a MinIO CLI command",
	Long:  "Execute each argument as a MinIO CLI command.  Example: `deltafi minio exec \"ls --json foo\" \"ls --summarize bar\"`",
	RunE: func(cmd *cobra.Command, args []string) error {
		for _, arg := range args {
			error := ExecMinioCommand(append([]string{"mc"}, arg))
			if error != nil {
				return error
			}
		}
		fmt.Println(fmt.Sprintf("\nMinIO CLI commands executed successfully: %d", len(args)))
		return nil
	},
}

var minioMcCmd = &cobra.Command{
	Use:                "mc",
	Short:              "Execute a MinIO CLI command",
	Long:               "Execute a MinIO CLI command.  Example: `deltafi minio mc ls --summarize`",
	DisableFlagParsing: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		return ExecMinioCommand(append([]string{"mc"}, args...))
	},
}

var minioWatchCmd = &cobra.Command{
	Use:                "watch",
	Short:              "Watch a MinIO bucket",
	Long:               "Watch a MinIO bucket.  Example: `deltafi minio watch deltafi/storage`",
	DisableFlagParsing: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(args) == 0 {
			args = []string{"deltafi/storage"}
		}
		return ExecMinioCommand(append([]string{"mc", "watch"}, args...))
	},
}

func ExecMinioCommand(cmd []string) error {
	orchestrator := app.GetOrchestrator()
	return orchestrator.ExecuteMinioCommand(cmd)
}

func init() {
	rootCmd.AddCommand(minioCmd)
	minioCmd.AddCommand(minioCliCmd)
	minioCmd.AddCommand(minioExecCmd)
	minioCmd.AddCommand(minioMcCmd)
	minioCmd.AddCommand(minioWatchCmd)
}
