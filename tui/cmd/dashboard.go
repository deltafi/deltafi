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
	"time"

	"github.com/charmbracelet/bubbles/progress"
	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/glamour"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

type model struct {
	progress progress.Model
	spinner  spinner.Model
	nodes    []api.NodeMetrics
	err      error
	client   *api.Client
	width    int
	interval time.Duration
	title    string
	stats    *graphql.GetDeltaFileStatsResponse
	ticks    int
	status   api.StatusResponse
	renderer *glamour.TermRenderer
	styles   struct {
		panelStyle      lipgloss.Style
		metricNameStyle lipgloss.Style
		nodeNameStyle   lipgloss.Style
		valueStyle      lipgloss.Style
		titleStyle      lipgloss.Style
	}
}

type nodesMsg []api.NodeMetrics
type statsMsg *graphql.GetDeltaFileStatsResponse
type statusMsg api.StatusResponse
type errMsg struct{ error }
type dashboardTickMsg struct{}

func tick(interval time.Duration) tea.Cmd {
	return tea.Tick(interval, func(t time.Time) tea.Msg {
		return dashboardTickMsg{}
	})
}

func initialModel(interval time.Duration) model {
	if interval < 1 {
		interval = 1
	}
	s := spinner.New()
	s.Spinner = spinner.Points
	s.Spinner.Frames = []string{
		"▁  █",
		"▂  ▇",
		"▃  ▆",
		"▄  ▅",
		"▅  ▄",
		"▆  ▃",
		"▇  ▂",
		"█  ▁",
		"▇  ▂",
		"▆  ▃",
		"▅  ▄",
		"▄  ▅",
		"▃  ▆",
		"▂  ▇",
	}
	s.Style = lipgloss.NewStyle().Foreground(styles.DarkGray)

	return model{
		progress: progress.New(progress.WithGradient("#40a02b", "#f38ba8")),
		spinner:  s,
		client:   app.GetInstance().GetAPIClient(),
		interval: interval,
		title:    "DeltaFi System Dashboard",
		styles: struct {
			panelStyle      lipgloss.Style
			metricNameStyle lipgloss.Style
			nodeNameStyle   lipgloss.Style
			valueStyle      lipgloss.Style
			titleStyle      lipgloss.Style
		}{
			panelStyle: lipgloss.NewStyle().
				BorderStyle(lipgloss.RoundedBorder()).
				BorderForeground(styles.Surface2).
				Padding(0, 2),
			metricNameStyle: lipgloss.NewStyle().
				Width(9).
				Align(lipgloss.Left),
			nodeNameStyle: styles.BaseStyle.Bold(true),
			valueStyle: styles.InfoStyle.
				Width(20).
				Align(lipgloss.Left),
			titleStyle: styles.HeaderStyle.
				Bold(true).
				Align(lipgloss.Center),
		},
	}
}

func (m model) Init() tea.Cmd {
	return tea.Batch(tick(m.interval), m.spinner.Tick, m.fetchMetrics())
}

func (m model) fetchMetrics() tea.Cmd {
	return tea.Batch(
		func() tea.Msg {
			nodes, err := m.client.Nodes()
			if err != nil {
				return errMsg{err}
			}
			return nodesMsg(nodes)
		},
		func() tea.Msg {
			stats, err := graphql.GetDeltaFileStats()
			if err != nil {
				return errMsg{err}
			}
			return statsMsg(stats)
		},
		func() tea.Msg {
			return spinner.TickMsg{}
		},
		func() tea.Msg {
			status, err := m.client.Status()
			if err != nil {
				return errMsg{err}
			}
			return statusMsg(*status)
		},
	)
}

func (m model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		if msg.String() == "ctrl+c" {
			return m, tea.Quit
		}
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.progress.Width = m.width - 37
		r, _ := glamour.NewTermRenderer(
			GetMarkdownStyle(),
			glamour.WithWordWrap(max(m.width-8, 20)),
		)
		m.renderer = r
		return m, nil
	case errMsg:
		m.err = msg
		return m, nil
	case nodesMsg:
		m.nodes = msg
		m.err = nil
		return m, nil
	case statusMsg:
		m.status = api.StatusResponse(msg)
		return m, nil
	case statsMsg:
		m.stats = msg
		m.err = nil
		return m, nil
	case dashboardTickMsg:
		m.ticks++
		return m, tea.Batch(tick(m.interval), m.fetchMetrics())
	case spinner.TickMsg:
		m.spinner, _ = m.spinner.Update(msg)
		return m, nil
	}
	return m, nil
}

func (m model) View() string {
	if m.err != nil {
		return fmt.Sprintf("Error: %v", m.err)
	}

	r, _ := glamour.NewTermRenderer(
		GetMarkdownStyle(),
		glamour.WithWordWrap(max(m.width-8, 20)),
	)
	m.renderer = r

	var s string
	s += m.styles.titleStyle.Width(m.width).Render(m.title) + "\n"
	s += m.styles.titleStyle.Width(m.width).Render(m.spinner.View()) + "\n"

	var totalCount, inFlightCount string
	if m.stats != nil {
		totalCount = fmt.Sprintf("%d", m.stats.DeltaFileStats.TotalCount)
		inFlightCount = fmt.Sprintf("%d", m.stats.DeltaFileStats.InFlightCount)
	} else {
		totalCount = "--"
		inFlightCount = "--"
	}

	statsRow := lipgloss.JoinHorizontal(
		lipgloss.Top,
		m.styles.panelStyle.Align(lipgloss.Center).Width(m.width/2-2).Render(fmt.Sprintf("Total DeltaFiles\n%s", styles.AccentStyle.Render(totalCount))),
		m.styles.panelStyle.Align(lipgloss.Center).Width(m.width-m.width/2-2).Render(fmt.Sprintf("In Flight DeltaFiles\n%s", styles.AccentStyle.Render(inFlightCount))),
	)

	s += statsRow + "\n"

	for _, node := range m.nodes {
		s += m.styles.panelStyle.Render(m.renderNode(node))
		s += "\n"
	}
	// s += m.styles.panelStyle.Width(m.width - 2).Render(fmt.Sprintf("Ticks: %d", m.ticks))
	// s += "\n"
	s += m.styles.panelStyle.PaddingTop(1).Width(m.width - 2).Render(RenderStatus(m.status, true, m.renderer))

	helpStyle := lipgloss.NewStyle().
		Foreground(styles.Surface2)
	s += helpStyle.Width(m.width).Align(lipgloss.Center).Render("\nPress Ctrl+C to exit")

	return s
}

var dashboardCmd = &cobra.Command{
	Use:     "dashboard",
	Short:   "Display system metrics dashboard",
	GroupID: "deltafi",
	RunE: func(cmd *cobra.Command, args []string) error {
		interval, _ := cmd.Flags().GetInt("interval")
		p := tea.NewProgram(initialModel(time.Duration(interval)*time.Second), tea.WithAltScreen())
		if _, err := p.Run(); err != nil {
			return fmt.Errorf("failed to run dashboard: %v", err)
		}
		return nil
	},
}

func init() {
	dashboardCmd.Flags().IntP("interval", "n", 5, "Refresh interval in seconds")
	rootCmd.AddCommand(dashboardCmd)
}

func (m model) renderNode(node api.NodeMetrics) string {
	nodeContent := fmt.Sprintf("%s %s\n", m.styles.metricNameStyle.Render("Node:"), m.styles.nodeNameStyle.Render(node.Name))

	// Memory usage
	memPercent := float64(node.Resources.Memory.Usage) / float64(node.Resources.Memory.Limit)
	memUsage := formatBytes(node.Resources.Memory.Usage)
	memLimit := formatBytes(node.Resources.Memory.Limit)
	memSummary := fmt.Sprintf("(%s/%s)", memUsage, memLimit)
	nodeContent += fmt.Sprintf("%s %s %s\n",
		m.styles.metricNameStyle.Render("Memory:"),
		m.styles.valueStyle.Render(memSummary),
		m.progress.ViewAs(memPercent))

	// CPU usage
	cpuPercent := float64(node.Resources.CPU.Usage) / float64(node.Resources.CPU.Limit)
	cpuUsage := fmt.Sprintf("%.1f", float64(node.Resources.CPU.Usage)/1000) // Convert millicores to cores
	cpuLimit := fmt.Sprintf("%.1f", float64(node.Resources.CPU.Limit)/1000)
	cpuSummary := fmt.Sprintf("(%s/%s cores)", cpuUsage, cpuLimit)
	nodeContent += fmt.Sprintf("%s %s %s\n",
		m.styles.metricNameStyle.Render("CPU:"),
		m.styles.valueStyle.Render(cpuSummary),
		m.progress.ViewAs(cpuPercent))
	// Minio disk usage
	minioPercent := float64(node.Resources.DiskMinio.Usage) / float64(node.Resources.DiskMinio.Limit)
	minioUsage := formatBytes(node.Resources.DiskMinio.Usage)
	minioLimit := formatBytes(node.Resources.DiskMinio.Limit)
	minioSummary := fmt.Sprintf("(%s/%s)", minioUsage, minioLimit)
	nodeContent += fmt.Sprintf("%s %s %s\n",
		m.styles.metricNameStyle.Render("Minio:"),
		m.styles.valueStyle.Render(minioSummary),
		m.progress.ViewAs(minioPercent))
	// Postgres disk usage
	pgPercent := float64(node.Resources.DiskPostgres.Usage) / float64(node.Resources.DiskPostgres.Limit)
	pgUsage := formatBytes(node.Resources.DiskPostgres.Usage)
	pgLimit := formatBytes(node.Resources.DiskPostgres.Limit)
	pgSummary := fmt.Sprintf("(%s/%s)", pgUsage, pgLimit)
	nodeContent += fmt.Sprintf("%s %s %s",
		m.styles.metricNameStyle.Render("Postgres:"),
		m.styles.valueStyle.Render(pgSummary),
		m.progress.ViewAs(pgPercent))

	return nodeContent
}
