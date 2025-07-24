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
	"strings"

	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

type downCommand struct {
	BaseCommand
	spinner spinner.Model
	done    bool
	err     error
}

type cleanupDoneMsg struct{ err error }

var downCmd = &cobra.Command{
	Use:   "down",
	Short: "Stop and optionally destroy DeltaFi cluster",
	Long: `Stop the DeltaFi cluster and optionally destroy all data.

This is a destructive operation that will:
- Stop all DeltaFi services
- Remove containers and network resources
- Optionally delete all persistent data (with --destroy flag)

WARNING: Using --destroy will permanently delete all DeltaFiles, 
configurations, and snapshots.

Examples:
  deltafi down                 # Stop cluster, preserve data
  deltafi down --destroy       # Stop cluster and delete all data
  deltafi down --force         # Skip confirmation prompts`,
	SilenceUsage:  true,
	SilenceErrors: true,
	GroupID:       "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {
		// Add confirmation prompt
		force, err := cmd.Flags().GetBool("force")
		if err != nil {
			return err
		}

		destroyData, err := cmd.Flags().GetBool("destroy")
		if err != nil {
			return err
		}

		destroyData = destroyData || app.GetOrchestrationMode() == orchestration.Kind

		if !force {
			if destroyData {
				cmd.Printf("☠️  WARNING: This command will take down the DeltaFi cluster and all persistent data!  ☠️\n")
			} else {
				cmd.Printf("⚠️  WARNING: This command will take down the DeltaFi cluster!  Persistent data will not be removed.\n")
			}
			cmd.Printf("Are you sure you want to continue? [y/N]: ")

			var response string
			if _, err := fmt.Scanln(&response); err != nil {
				return err
			}

			if !strings.EqualFold(response, "y") {
				return fmt.Errorf("operation cancelled by user")
			}
		}

		return down(destroyData)
	},
}

func down(destroyData bool) error {
	if err := app.GetOrchestrator().Down([]string{}); err != nil {
		return err
	}

	if destroyData {
		dataDir := app.GetDataDir()

		// Create and run the spinner program
		s := spinner.New()
		s.Spinner = spinner.Dot
		s.Style = lipgloss.NewStyle().Foreground(styles.Yellow)

		downCmd := &downCommand{
			BaseCommand: NewBaseCommand(),
			spinner:     s,
		}

		p := tea.NewProgram(downCmd, tea.WithOutput(os.Stderr))

		// Run the cleanup in a goroutine
		go func() {
			err := os.RemoveAll(dataDir)
			p.Send(cleanupDoneMsg{err: err})
		}()

		// Run the program and wait for completion
		if _, err := p.Run(); err != nil {
			return err
		}

		// Check if there was an error during cleanup
		if downCmd.err != nil {
			fmt.Println(" " + styles.ErrorStyle.Render("✗") + " Data directory cleanup failed")
			return fmt.Errorf("failed to clean up data directory: %w", downCmd.err)
		}

		fmt.Println(" " + styles.SuccessStyle.Bold(true).Render("✔") + " Data directory cleanup complete")
	}

	return nil
}

func (c *downCommand) Init() tea.Cmd {
	return c.spinner.Tick
}

func (c *downCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c", "esc":
			return c, tea.Quit
		}
	case spinner.TickMsg:
		var cmd tea.Cmd
		c.spinner, cmd = c.spinner.Update(msg)
		return c, cmd
	case cleanupDoneMsg:
		c.done = true
		c.err = msg.err
		return c, tea.Quit
	}
	return c, nil
}

func (c *downCommand) View() string {
	if c.err != nil {
		return c.RenderError()
	}
	if c.done {
		return " " + styles.SuccessStyle.Render("✓") + " Data directory cleanup complete"
	}
	return " " + c.spinner.View() + "Cleaning up data directory..."
}

func init() {
	rootCmd.AddCommand(downCmd)
	downCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompts and proceed automatically")
	downCmd.Flags().BoolP("destroy", "d", false, "Remove data directory contents after successful down")
}
