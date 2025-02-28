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
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/glamour"
	"github.com/charmbracelet/lipgloss"
	"github.com/spf13/cobra"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
)

// statusCmd represents the status command
var statusCmd = &cobra.Command{
	Use:     "status",
	Short:   "Display current health and status for DeltaFi system",
	Long:    `Display current health and status for DeltaFi system`,
	GroupID: "orchestration",
	Run: func(cmd *cobra.Command, args []string) {

		runProgram(NewStatusCommand())
	},
}

func init() {
	rootCmd.AddCommand(statusCmd)
	versionsCmd.Flags().BoolP("follow", "f", false, "Get status updates continuously")

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// statusCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	// statusCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")
}

type tickMsg time.Time

type StatusCommand struct {
	BaseCommand
	status     api.StatusResponse
	spinner    spinner.Model
	points     string
	width      int
	height     int
	ready      bool
	lastUpdate time.Time
	renderer   *glamour.TermRenderer
	loading    bool
}

var (
	// primaryBlue   = lipgloss.AdaptiveColor{Light: "#0366d6", Dark: "#58a6ff"}
	// secondaryBlue = lipgloss.AdaptiveColor{Light: "#0969da", Dark: "#79c0ff"}

	iconColumn   = lipgloss.NewStyle().Width(2).Align(lipgloss.Center)
	messageStyle = lipgloss.NewStyle().
			MarginLeft(4).
			Foreground(styles.Crust)

	updateInterval = time.Second
)

func NewStatusCommand() *StatusCommand {
	RequireRunningDeltaFi()
	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(lipgloss.Color("205"))

	return &StatusCommand{
		BaseCommand: NewBaseCommand(),
		spinner:     s,
		loading:     true,
		points:      "⋅",
	}
}

func (c *StatusCommand) waitThenFetch() tea.Cmd {
	return tea.Tick(updateInterval, func(t time.Time) tea.Msg {
		return tickMsg(t)
	})
}

func (c *StatusCommand) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		c.spinner.Tick,
		c.fetchStatus,
	)
}

func (c *StatusCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height
		c.ready = true

		// Initialize or reinitialize glamour renderer with new width
		r, _ := glamour.NewTermRenderer(
			glamour.WithAutoStyle(),
			glamour.WithWordWrap(max(c.width-8, 20)), // Account for borders and minimum width
		)
		c.renderer = r

	case tea.KeyMsg:
		if handled, cmd := c.HandleKeyMsg(msg); handled {
			return c, tea.Batch(cmd, tea.ExitAltScreen)
		}

	case spinner.TickMsg:
		var cmd tea.Cmd
		if c.loading {
			c.spinner, cmd = c.spinner.Update(msg)
			return c, cmd
		}

	case tickMsg:
		if time.Since(c.lastUpdate) >= updateInterval {
			return c, c.fetchStatus
		}
		return c, c.waitThenFetch()

	case error:
		c.err = msg
		c.loading = false
		c.lastUpdate = time.Now()
		return c, c.waitThenFetch()

	case api.StatusResponse:
		c.status = msg
		c.loading = false
		c.lastUpdate = time.Now()
		// Update points spinner
		c.points = nextPoints(c.points)
		return c, c.waitThenFetch()
	}

	return c, nil
}

func (c *StatusCommand) renderMarkdown(text string) string {
	if c.renderer == nil {
		return text
	}
	rendered, err := c.renderer.Render(text)
	if err != nil {
		return text
	}
	return strings.TrimSpace(rendered)
}

func (c *StatusCommand) View() string {
	if !c.ready {
		return "\n  Initializing..."
	}

	if c.loading {
		return c.spinner.View() + " Loading..."
	}

	// if c.err != nil {
	// 	return components.NewErrorBox("Status Error", c.err.Error()).Render()
	// }

	// Calculate available width for content
	contentWidth := max(c.width-4, 20) // Minimum 20 columns

	mainStyle := lipgloss.NewStyle().
		Width(c.width).
		Height(c.height).
		Align(lipgloss.Center)

	contentStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(styles.Crust).
		Padding(1).
		Width(contentWidth)

	titleStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Bold(true).
		Width(contentWidth - 2). // Account for padding
		Align(lipgloss.Center)

	stateStyle := lipgloss.NewStyle().
		Bold(true).
		Width(contentWidth - 2). // Account for padding
		Align(lipgloss.Center)

	spinnerStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Align(lipgloss.Right).
		Width(contentWidth - 2)

	switch c.status.Status.Color {
	case "green":
		stateStyle = styles.SuccessStyle
	case "yellow":
		stateStyle = styles.WarningStyle
	case "red":
		stateStyle = styles.ErrorStyle
	}

	header := lipgloss.JoinVertical(lipgloss.Center,
		spinnerStyle.Render(c.points),
		titleStyle.Render("DeltaFi System Status"),
		stateStyle.Render(c.status.Status.State),
		"",
	)

	var checks []string
	for _, check := range c.status.Status.Checks {
		style := lipgloss.NewStyle()
		icon := c.getStatusIcon(check.Code)

		switch check.Code {
		case 0:
			style = styles.SuccessStyle
		case 1:
			style = styles.WarningStyle
		case 2:
			style = styles.ErrorStyle
		}

		// Render the description with the icon
		checkLine := lipgloss.JoinHorizontal(lipgloss.Left,
			iconColumn.Render(style.Render(icon)),
			c.renderMarkdown(check.Description),
		)
		checks = append(checks, checkLine)

		// Add message on a new line if present
		if check.Message != "" {
			checks = append(checks, messageStyle.Render(check.Message))
		}
	}

	content := lipgloss.JoinVertical(lipgloss.Left,
		header,
		lipgloss.JoinVertical(lipgloss.Left, checks...),
	)

	return mainStyle.Render(
		contentStyle.Render(content),
	)
}

func (c *StatusCommand) getStatusIcon(code int) string {
	switch code {
	case 0:
		return "✓"
	case 1:
		return "!"
	case 2:
		return "✗"
	default:
		return "•"
	}
}

func (c *StatusCommand) fetchStatus() tea.Msg {
	client := app.GetInstance().GetAPIClient()
	var result api.StatusResponse
	err := client.Get("/api/v2/status", &result, nil)
	if err != nil {
		return err
	}
	return result
}

// Helper functions

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func nextPoints(current string) string {
	switch current {
	case "⋅":
		return "⋅⋅"
	case "⋅⋅":
		return "⋅⋅⋅"
	default:
		return "⋅"
	}
}
