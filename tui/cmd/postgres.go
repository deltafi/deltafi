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
/*
Copyright © 2024 DeltaFi Contributors <deltafi@deltafi.org>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package cmd

import (
	"fmt"
	"os"

	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
)

var postgresCmd = &cobra.Command{
	Use:   "postgres",
	Short: "Command line access to the running DeltaFi Postgres instance",
	Long:  `Command line access to the running DeltaFi Postgres instance`,
}

var cliCmd = &cobra.Command{
	Use:   "cli",
	Short: "Start an interactive Postgres CLI session",
	Long:  "Start an interactive Postgres CLI session",
	RunE: func(cmd *cobra.Command, args []string) error {
		c, error := app.GetOrchestrator().GetPostgresCmd(args)
		if error != nil {
			return error
		}

		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		if err := c.Run(); err != nil {
			return fmt.Errorf("command execution failed: %w", err)
		}

		return nil
	},
}

var execCmd = &cobra.Command{
	Use:   "eval",
	Short: "Pipe commands to Postgres from stdin",
	Long:  "Opens a non-interactive session with postgres and pipes commands to it from stdin.",
	Example: `
	cat query.sql | deltafi postgres eval
	deltafi postgres eval < query.sql
	deltafi postgres eval -- -e < query.sql`,
	RunE: func(cmd *cobra.Command, args []string) error {
		c, error := app.GetOrchestrator().GetPostgresExecCmd(args)
		if error != nil {
			return error
		}

		c.Stdin = os.Stdin
		c.Stdout = os.Stdout
		c.Stderr = os.Stderr

		if err := c.Run(); err != nil {
			return fmt.Errorf("command execution failed: %w", err)
		}

		return nil
	},
}

func init() {
	rootCmd.AddCommand(postgresCmd)
	postgresCmd.AddCommand(cliCmd)
	postgresCmd.AddCommand(execCmd)
}
