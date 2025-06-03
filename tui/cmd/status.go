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
		watch, _ := cmd.Flags().GetBool("watch")
		status := NewStatusCommand(watch)

		if watch {
			runProgram(status)
		} else {
			client := app.GetInstance().GetAPIClient()
			var result api.StatusResponse
			err := client.Get("/api/v2/status", &result, nil)
			if err != nil {
				cmd.PrintErrf("Error fetching status: %v\n", err)
				return
			}
			cmd.Print(RenderStatus(result, false, nil))
		}
	},
}

func init() {
	rootCmd.AddCommand(statusCmd)
	statusCmd.Flags().BoolP("watch", "w", false, "Watch status updates continuously")

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
	status      api.StatusResponse
	spinner     spinner.Model
	points      string
	width       int
	height      int
	ready       bool
	lastUpdate  time.Time
	renderer    *glamour.TermRenderer
	loading     bool
	interactive bool
}

var (
	iconColumn     = lipgloss.NewStyle().Width(2).Align(lipgloss.Center)
	updateInterval = time.Second
)

func NewStatusCommand(interactive bool) *StatusCommand {
	RequireRunningDeltaFi()
	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(lipgloss.Color("205"))

	cmd := &StatusCommand{
		BaseCommand: NewBaseCommand(),
		spinner:     s,
		loading:     true,
		points:      "⋅",
		interactive: interactive,
	}

	// Initialize renderer for both interactive and non-interactive modes
	width := 80
	if !interactive {
		width = 80
	}
	r, _ := glamour.NewTermRenderer(
		GetMarkdownStyle(),
		glamour.WithWordWrap(width),
	)
	cmd.renderer = r

	return cmd
}

func (c *StatusCommand) waitThenFetch() tea.Cmd {
	return tea.Tick(updateInterval, func(t time.Time) tea.Msg {
		return tickMsg(t)
	})
}

func (c *StatusCommand) Init() tea.Cmd {
	if !c.interactive {
		return nil
	}
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
		r, _ := glamour.NewTermRenderer(
			GetMarkdownStyle(),
			glamour.WithWordWrap(max(c.width-8, 20)),
		)
		c.renderer = r

	case tea.KeyMsg:
		if msg.Type == tea.KeyCtrlC || msg.Type == tea.KeyEsc {
			c.interactive = false // Prevent final render
			return c, tea.Batch(
				tea.ExitAltScreen,
				tea.Quit,
			)
		}
		if handled, cmd := c.HandleKeyMsg(msg); handled {
			c.interactive = false // Prevent final render
			return c, tea.Batch(cmd, tea.ExitAltScreen, tea.Quit)
		}

	case spinner.TickMsg:
		if c.loading {
			var cmd tea.Cmd
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
		c.points = nextPoints(c.points)
		return c, c.waitThenFetch()
	}

	return c, nil
}

func getStatusStyle(color string) lipgloss.Style {
	style := lipgloss.NewStyle().Bold(true)
	switch color {
	case "green":
		return style.Foreground(styles.SuccessStyle.GetForeground())
	case "yellow":
		return style.Foreground(styles.WarningStyle.GetForeground())
	case "red":
		return style.Foreground(styles.ErrorStyle.GetForeground())
	default:
		return style
	}
}

func getStatusIcon(code int) string {
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

func getIconStyle(code int) lipgloss.Style {
	style := lipgloss.NewStyle()
	switch code {
	case 0:
		return style.Foreground(styles.SuccessStyle.GetForeground())
	case 1:
		return style.Foreground(styles.WarningStyle.GetForeground())
	case 2:
		return style.Foreground(styles.ErrorStyle.GetForeground())
	default:
		return style.Foreground(styles.Blue)
	}
}

func RenderStatus(status api.StatusResponse, interactive bool, renderer *glamour.TermRenderer) string {
	var sb strings.Builder

	// Title and overall status
	titleStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Bold(true)

	sb.WriteString(titleStyle.Render("DeltaFi System Status: "))

	// Status state with color
	stateStyle := getStatusStyle(status.Status.Color)
	sb.WriteString(stateStyle.Render(status.Status.State))
	sb.WriteString("\n\n")

	// Status checks
	for _, check := range status.Status.Checks {
		iconStyle := getIconStyle(check.Code)
		icon := getStatusIcon(check.Code)

		// Render check with icon
		checkLine := lipgloss.JoinHorizontal(lipgloss.Left,
			iconColumn.Render(iconStyle.Render(icon)),
			check.Description,
		)
		sb.WriteString(checkLine)
		sb.WriteString("\n")

		if check.Message != "" {
			sb.WriteString(renderGlamourMarkdown(check.Message, interactive, renderer))
			sb.WriteString("\n")
		}
	}

	return sb.String()
}

func (c *StatusCommand) View() string {
	if !c.interactive {
		return ""
	}

	if !c.ready {
		return "\n  Initializing..."
	}

	if c.loading {
		return c.spinner.View() + " Loading..."
	}

	contentWidth := max(c.width-4, 20)

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
		Width(contentWidth - 2).
		Align(lipgloss.Center)

	spinnerStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Align(lipgloss.Right).
		Width(contentWidth - 2)

	stateStyle := getStatusStyle(c.status.Status.Color).
		Width(contentWidth - 2).
		Align(lipgloss.Center)

	header := lipgloss.JoinVertical(lipgloss.Center,
		spinnerStyle.Render(c.points),
		titleStyle.Render("DeltaFi System Status"),
		stateStyle.Render(c.status.Status.State),
		"",
	)

	var checks []string
	for _, check := range c.status.Status.Checks {
		iconStyle := getIconStyle(check.Code)
		icon := getStatusIcon(check.Code)

		checkLine := lipgloss.JoinHorizontal(lipgloss.Left,
			iconColumn.Render(iconStyle.Render(icon)),
			check.Description,
		)
		checks = append(checks, checkLine)

		if check.Message != "" {
			checks = append(checks, renderGlamourMarkdown(check.Message, c.interactive, c.renderer))
		}
	}

	content := lipgloss.JoinVertical(lipgloss.Center,
		header,
		lipgloss.JoinVertical(lipgloss.Left, checks...),
	)

	return mainStyle.Render(contentStyle.Render(content))
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
