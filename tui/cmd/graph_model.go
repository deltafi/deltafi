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
// ABOUTME: Interactive TUI model for time-series graph display
// ABOUTME: Handles keyboard controls, auto-refresh, and series selection
package cmd

import (
	"fmt"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
)

// graphModel is the Bubble Tea model for the graph TUI
type graphModel struct {
	client      *api.MetricsClient
	metric      *api.MetricDefinition // nil if custom query or no selection
	customQuery string                // non-empty for custom queries
	from        time.Duration
	timeOffset  time.Duration // How far back from "now" the window ends (0 = now)
	interval    time.Duration
	filter      dataSourceFilter
	chartStyle  components.ChartStyle

	series  []api.TimeSeries
	chart   *components.LineChart
	legend  *components.Legend
	spinner spinner.Model

	width       int
	height      int
	loading     bool
	err         error
	quitting    bool
	lastUpdated time.Time

	// Filter modal state
	filterModalOpen  bool
	filterModalIndex int

	// Help modal state
	helpModalOpen bool

	// Query change modal state
	queryModalOpen     bool
	queryModalIndex    int    // Index into queryModalFiltered
	queryModalScroll   int    // First visible item in queryModalFiltered
	queryModalSearch   string // Search text for filtering metrics
	queryModalFiltered []int  // Indices into full metrics list matching search

	// Per-metric visibility state: metric key → series name → visible
	metricVisibility map[string]map[string]bool
	pendingRestore   bool // true after a metric switch, cleared after first data load
	legendVisible    bool
}

// Messages
type graphDataMsg []api.TimeSeries
type graphErrorMsg struct{ error }
type graphTickMsg struct{}

func newGraphModel(client *api.MetricsClient, metric *api.MetricDefinition, customQuery string, from, interval time.Duration, filter dataSourceFilter, chartStyle components.ChartStyle, legendVisible bool) graphModel {
	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(styles.Blue)

	// If no metric specified and no custom query, show selection modal on startup
	showModal := metric == nil && customQuery == ""
	loading := !showModal

	m := graphModel{
		client:           client,
		metric:           metric,
		customQuery:      customQuery,
		from:             from,
		interval:         interval,
		filter:           filter,
		chartStyle:       chartStyle,
		spinner:          s,
		loading:          loading,
		chart:            components.NewLineChart(80, 20, chartStyle),
		legend:           components.NewLegend(),
		queryModalOpen:   showModal,
		metricVisibility: make(map[string]map[string]bool),
		legendVisible:    legendVisible,
	}
	if showModal {
		m.updateQueryModalFilter()
	}
	return m
}

func (m graphModel) Init() tea.Cmd {
	cmds := []tea.Cmd{
		m.spinner.Tick,
	}
	// Only fetch data if a metric or custom query is specified
	if m.metric != nil || m.customQuery != "" {
		cmds = append(cmds, m.fetchData())
	}
	if m.interval > 0 {
		cmds = append(cmds, graphTick(m.interval))
	}
	return tea.Batch(cmds...)
}

func graphTick(interval time.Duration) tea.Cmd {
	return tea.Tick(interval, func(t time.Time) tea.Msg {
		return graphTickMsg{}
	})
}

func (m graphModel) fetchData() tea.Cmd {
	return func() tea.Msg {
		// Calculate max data points based on chart width (2x width, minimum 10)
		maxDataPoints := max(m.width*2, 10)

		var series []api.TimeSeries
		var err error

		// Calculate the actual time range accounting for pan offset
		// from = how far back from "now" the window starts
		// until = how far back from "now" the window ends (0 = now)
		from := m.from + m.timeOffset
		until := m.timeOffset

		if m.customQuery != "" {
			// Custom queries are passed directly to query_range
			series, err = m.client.QueryRange(m.customQuery, from, until, maxDataPoints)
		} else if m.metric != nil {
			// Predefined metrics use QueryMetric for counter handling and label extraction
			series, err = m.client.QueryMetric(m.metric, from, until, maxDataPoints)
		} else {
			// No metric selected yet
			return graphDataMsg(nil)
		}

		if err != nil {
			return graphErrorMsg{err}
		}
		return graphDataMsg(series)
	}
}

func (m graphModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		return m.handleKeyMsg(msg)

	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.legend.SetWidth(m.width)
		m.updateChartSize()
		// Refresh data with new maxDataPoints based on width
		m.loading = true
		return m, m.fetchData()

	case graphDataMsg:
		m.series = m.filterSeries(msg)
		m.loading = false
		m.err = nil
		m.lastUpdated = time.Now()
		m.legend.UpdateSeries(m.series)
		if m.pendingRestore {
			m.restoreMetricVisibility()
			m.pendingRestore = false
		}
		m.updateChartSize()
		// Set the time range for the X-axis based on current from and timeOffset
		now := time.Now()
		endTime := now.Add(-m.timeOffset)
		startTime := now.Add(-m.timeOffset - m.from)
		m.chart.SetTimeRange(startTime, endTime)
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil

	case graphErrorMsg:
		m.err = msg.error
		m.loading = false
		return m, nil

	case graphTickMsg:
		// Skip refresh when panned to historical data (it won't change)
		if m.timeOffset > 0 {
			return m, graphTick(m.interval)
		}
		return m, tea.Batch(m.fetchData(), graphTick(m.interval))

	case spinner.TickMsg:
		var cmd tea.Cmd
		m.spinner, cmd = m.spinner.Update(msg)
		return m, cmd
	}

	return m, nil
}

func (m graphModel) handleKeyMsg(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	// Handle modal keys when modal is open
	if m.filterModalOpen {
		return m.handleFilterModalKey(msg)
	}
	if m.helpModalOpen {
		return m.handleHelpModalKey(msg)
	}
	if m.queryModalOpen {
		return m.handleQueryModalKey(msg)
	}

	switch msg.String() {
	case "ctrl+c", "q", "Q", "esc":
		m.quitting = true
		return m, tea.Quit

	case "?":
		m.helpModalOpen = true
		return m, nil

	case "c", "C":
		// Open query change modal (only for non-custom queries)
		if m.customQuery == "" {
			m.queryModalOpen = true
			m.queryModalSearch = ""
			m.updateQueryModalFilter()
			// Set index to current metric and adjust scroll to keep it visible
			if m.metric != nil {
				for i, metricIdx := range m.queryModalFiltered {
					if api.GetAllMetrics()[metricIdx].Key == m.metric.Key {
						m.queryModalIndex = i
						// Center the selection in the visible area
						maxVisible := m.queryModalMaxVisible()
						m.queryModalScroll = max(i-maxVisible/2, 0)
						maxScroll := max(len(m.queryModalFiltered)-maxVisible, 0)
						if m.queryModalScroll > maxScroll {
							m.queryModalScroll = maxScroll
						}
						break
					}
				}
			}
		}
		return m, nil

	case "r", "R":
		m.loading = true
		return m, m.fetchData()

	case "+", "=":
		// Zoom out (double time range)
		m.from = min(m.from*2,
			// Cap at 1 week
			168*time.Hour)
		m.loading = true
		return m, m.fetchData()

	case "-", "_":
		// Zoom in (halve time range)
		m.from = max(m.from/2, time.Minute)
		m.loading = true
		return m, m.fetchData()

	case "left":
		// Pan back in time (shift window by 1/4 of window size)
		panAmount := max(m.from/4, time.Minute)
		m.timeOffset = m.timeOffset + panAmount
		m.loading = true
		return m, m.fetchData()

	case "right":
		// Pan forward in time (shift window by 1/4 of window size)
		panAmount := max(m.from/4, time.Minute)
		m.timeOffset = max(m.timeOffset-panAmount, 0)
		m.loading = true
		return m, m.fetchData()

	case "x", "X":
		// Return to now (reset pan offset)
		if m.timeOffset > 0 {
			m.timeOffset = 0
			m.loading = true
			return m, m.fetchData()
		}
		return m, nil

	case "a", "A":
		m.legend.ShowAll()
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil

	case "n", "N":
		m.legend.HideAll()
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil

	case "up":
		// Cycle to previous metric
		if m.customQuery == "" {
			return m.cycleMetric(-1)
		}
		return m, nil

	case "down":
		// Cycle to next metric
		if m.customQuery == "" {
			return m.cycleMetric(1)
		}
		return m, nil

	case "l", "L":
		// Toggle legend visibility
		m.legendVisible = !m.legendVisible
		m.updateChartSize()
		return m, nil

	case "f", "F":
		// Open filter modal
		if len(m.series) > 0 {
			m.filterModalOpen = true
			m.filterModalIndex = 0
		}
		return m, nil

	case "1", "2", "3", "4", "5", "6", "7", "8", "9", "0":
		// Toggle series visibility
		index := int(msg.String()[0] - '1')
		if msg.String() == "0" {
			index = 9 // 0 maps to series 10 (index 9)
		}
		m.legend.ToggleSeries(index)
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil
	}

	return m, nil
}

func (m graphModel) handleFilterModalKey(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	series := m.legend.GetSeries()

	switch msg.String() {
	case "esc", "f", "F", "q", "Q":
		// Close modal
		m.filterModalOpen = false
		return m, nil

	case "up", "k":
		// Move cursor up
		if m.filterModalIndex > 0 {
			m.filterModalIndex--
		}
		return m, nil

	case "down", "j":
		// Move cursor down
		if m.filterModalIndex < len(series)-1 {
			m.filterModalIndex++
		}
		return m, nil

	case "enter", " ":
		// Toggle selected series
		m.legend.ToggleSeries(m.filterModalIndex)
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil

	case "a", "A":
		// Show all
		m.legend.ShowAll()
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil

	case "n", "N":
		// Hide all
		m.legend.HideAll()
		m.chart.SetSeries(m.legend.GetSeries())
		return m, nil
	}

	return m, nil
}

func (m graphModel) handleHelpModalKey(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	switch msg.String() {
	case "esc", "?", "q", "enter", " ":
		m.helpModalOpen = false
		return m, nil
	}
	return m, nil
}

func (m graphModel) handleQueryModalKey(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	maxVisible := m.queryModalMaxVisible()

	switch msg.String() {
	case "ctrl+c":
		m.quitting = true
		return m, tea.Quit

	case "esc":
		// Only allow closing without selection if a metric is already selected
		if m.metric != nil {
			m.queryModalOpen = false
			m.queryModalSearch = ""
		}
		return m, nil

	case "up":
		if len(m.queryModalFiltered) > 0 && m.queryModalIndex > 0 {
			m.queryModalIndex--
			if m.queryModalIndex < m.queryModalScroll {
				m.queryModalScroll = m.queryModalIndex
			}
		}
		return m, nil

	case "down":
		if len(m.queryModalFiltered) > 0 && m.queryModalIndex < len(m.queryModalFiltered)-1 {
			m.queryModalIndex++
			if m.queryModalIndex >= m.queryModalScroll+maxVisible {
				m.queryModalScroll = m.queryModalIndex - maxVisible + 1
			}
		}
		return m, nil

	case "enter":
		// Select metric from filtered list
		if len(m.queryModalFiltered) > 0 {
			metrics := api.GetAllMetrics()
			m.saveMetricVisibility()
			metricIdx := m.queryModalFiltered[m.queryModalIndex]
			metric := metrics[metricIdx]
			m.metric = &metric
			m.queryModalOpen = false
			m.queryModalSearch = ""
			m.loading = true
			m.pendingRestore = true
			m.series = nil
			m.legend.ClearVisibility()
			m.legend.UpdateSeries(nil)
			return m, m.fetchData()
		}
		return m, nil

	case "backspace", "ctrl+h":
		if len(m.queryModalSearch) > 0 {
			runes := []rune(m.queryModalSearch)
			m.queryModalSearch = string(runes[:len(runes)-1])
			m.updateQueryModalFilter()
		}
		return m, nil

	case "ctrl+u":
		if m.queryModalSearch != "" {
			m.queryModalSearch = ""
			m.updateQueryModalFilter()
		}
		return m, nil

	default:
		// Append printable characters to search
		if msg.Type == tea.KeyRunes {
			m.queryModalSearch += string(msg.Runes)
			m.updateQueryModalFilter()
		}
		return m, nil
	}
}

// saveMetricVisibility captures the legend's persistent visibility state for the active metric
func (m *graphModel) saveMetricVisibility() {
	if m.metric == nil {
		return
	}
	state := m.legend.GetVisibility()
	if len(state) > 0 {
		m.metricVisibility[m.metric.Key] = state
	}
}

// restoreMetricVisibility applies saved visibility state to the legend for the active metric
func (m *graphModel) restoreMetricVisibility() {
	if m.metric == nil {
		return
	}
	state, exists := m.metricVisibility[m.metric.Key]
	if !exists {
		return
	}
	m.legend.SetVisibility(state)
}

// fuzzyMatch returns true if all characters in pattern appear in text in order (case-insensitive)
func fuzzyMatch(text, pattern string) bool {
	text = strings.ToLower(text)
	pattern = strings.ToLower(pattern)
	pi := 0
	for i := 0; i < len(text) && pi < len(pattern); i++ {
		if text[i] == pattern[pi] {
			pi++
		}
	}
	return pi == len(pattern)
}

// updateQueryModalFilter recomputes the filtered metrics list based on the search text
func (m *graphModel) updateQueryModalFilter() {
	metrics := api.GetAllMetrics()
	m.queryModalFiltered = nil
	if m.queryModalSearch == "" {
		m.queryModalFiltered = make([]int, len(metrics))
		for i := range metrics {
			m.queryModalFiltered[i] = i
		}
	} else {
		for i, metric := range metrics {
			if fuzzyMatch(metric.Title, m.queryModalSearch) || fuzzyMatch(metric.Key, m.queryModalSearch) {
				m.queryModalFiltered = append(m.queryModalFiltered, i)
			}
		}
	}
	m.queryModalIndex = 0
	m.queryModalScroll = 0
}

// cycleMetric moves to the next or previous metric in the registry, wrapping at boundaries
func (m graphModel) cycleMetric(direction int) (tea.Model, tea.Cmd) {
	metrics := api.GetAllMetrics()
	if len(metrics) == 0 {
		return m, nil
	}

	currentIndex := 0
	if m.metric != nil {
		for i, metric := range metrics {
			if metric.Key == m.metric.Key {
				currentIndex = i
				break
			}
		}
	}

	m.saveMetricVisibility()
	newIndex := (currentIndex + direction + len(metrics)) % len(metrics)
	metric := metrics[newIndex]
	m.metric = &metric
	m.loading = true
	m.pendingRestore = true
	m.series = nil
	m.legend.ClearVisibility()
	m.legend.UpdateSeries(nil)
	return m, m.fetchData()
}

// queryModalMaxVisible returns the max number of metrics visible in the modal
func (m graphModel) queryModalMaxVisible() int {
	// Reserve space for modal structure:
	// - border (2 lines: top + bottom)
	// - padding (2 lines: top + bottom)
	// - title (1 line)
	// - blank line after title (1 line)
	// - search field (1 line)
	// - scroll indicators (2 lines: up + down, reserved even when not shown for consistency)
	// - blank line before hint (1 line)
	// - hint line (1 line)
	// Total: 11 lines
	available := min(max(m.height-11, 5), len(m.queryModalFiltered))
	return available
}

func (m graphModel) View() string {
	if m.quitting {
		return ""
	}

	var s string

	// Header
	titleStyle := styles.HeaderStyle.Width(m.width).Align(lipgloss.Center)
	var header string
	if m.metric == nil && m.customQuery == "" {
		header = "Select a Metric"
	} else {
		title := "Custom Query"
		if m.metric != nil {
			title = m.metric.Title
		}
		timeRangeStr := formatDuration(m.from)
		if m.timeOffset > 0 {
			// Show that we're viewing historical data
			offsetStr := formatDuration(m.timeOffset)
			header = fmt.Sprintf("%s (%s ending %s ago)", title, timeRangeStr, offsetStr)
		} else {
			header = fmt.Sprintf("%s (last %s)", title, timeRangeStr)
		}
	}
	s += titleStyle.Render(header) + "\n"

	// Loading indicator or last updated
	if m.loading {
		s += lipgloss.NewStyle().Width(m.width).Align(lipgloss.Center).Render(m.spinner.View()+" Loading...") + "\n"
	} else {
		s += "\n"
	}

	// Error or chart
	if m.err != nil {
		errorStyle := styles.ErrorStyle.Width(m.width).Align(lipgloss.Center).Padding(2)
		s += errorStyle.Render(fmt.Sprintf("Error: %v", m.err)) + "\n"
	} else if m.metric == nil && m.customQuery == "" {
		// No metric selected - show prompt
		noDataStyle := lipgloss.NewStyle().Width(m.width).Align(lipgloss.Center).Foreground(styles.Subtext0)
		s += noDataStyle.Render("Press 'c' to select a metric") + "\n"
	} else if len(m.series) == 0 && !m.loading {
		noDataStyle := lipgloss.NewStyle().Width(m.width).Align(lipgloss.Center).Foreground(styles.Subtext0)
		s += noDataStyle.Render("No data available for the selected time range") + "\n"
	} else {
		s += m.chart.View() + "\n"
	}

	// Legend
	if len(m.series) > 0 && m.legendVisible {
		legendStyle := lipgloss.NewStyle().Width(m.width).Align(lipgloss.Center)
		s += legendStyle.Render(m.legend.View()) + "\n"
	}

	// Help bar with last updated timestamp
	helpText := "? help • q quit"
	var lastUpdatedText string
	if !m.lastUpdated.IsZero() {
		lastUpdatedText = m.lastUpdated.Format("15:04:05")
	}
	// Create a two-column layout: help on left, timestamp on right
	leftStyle := lipgloss.NewStyle().Foreground(styles.Surface2)
	rightStyle := lipgloss.NewStyle().Foreground(styles.Surface2)
	left := leftStyle.Render(helpText)
	right := rightStyle.Render(lastUpdatedText)
	gap := max(m.width-lipgloss.Width(left)-lipgloss.Width(right), 0)
	s += left + strings.Repeat(" ", gap) + right

	// Overlay modals if open
	if m.filterModalOpen {
		s = m.overlayFilterModal(s)
	}
	if m.helpModalOpen {
		s = m.overlayHelpModal(s)
	}
	if m.queryModalOpen {
		s = m.overlayQueryModal(s)
	}

	return s
}

// renderModal renders lines as a centered modal dialog
func (m graphModel) renderModal(lines []string) string {
	modalStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(styles.Blue).
		Padding(1, 2).
		Background(styles.Base)

	modal := modalStyle.Render(strings.Join(lines, "\n"))
	return lipgloss.Place(m.width, m.height, lipgloss.Center, lipgloss.Center, modal)
}

// modalTitle renders a modal title line
func modalTitle(title string) string {
	return lipgloss.NewStyle().Bold(true).Foreground(styles.Blue).Render(title)
}

// modalHint renders a modal hint/instruction line
func modalHint(hint string) string {
	return lipgloss.NewStyle().Foreground(styles.Subtext0).Render(hint)
}

func (m graphModel) overlayFilterModal(_ string) string {
	series := m.legend.GetSeries()

	lines := []string{modalTitle("Filter Series"), ""}

	for i, ser := range series {
		checkbox := "[ ]"
		if ser.Visible {
			checkbox = "[✓]"
		}

		color := components.SeriesColors[i%len(components.SeriesColors)]
		marker := lipgloss.NewStyle().Foreground(color).Render("●")
		line := fmt.Sprintf("%s %s %s", checkbox, marker, ser.Name)

		if i == m.filterModalIndex {
			line = lipgloss.NewStyle().Background(styles.Surface0).Foreground(styles.Text).Render(line)
		} else if !ser.Visible {
			line = lipgloss.NewStyle().Foreground(styles.Surface2).Render(line)
		}

		lines = append(lines, line)
	}

	lines = append(lines, "", modalHint("↑/↓ navigate • enter toggle • a/n all/none • esc close"))
	return m.renderModal(lines)
}

func (m graphModel) overlayHelpModal(_ string) string {
	keyStyle := lipgloss.NewStyle().Foreground(styles.Mauve).Bold(true)
	descStyle := lipgloss.NewStyle().Background(styles.Base).Foreground(styles.Text)

	lines := []string{
		modalTitle("Keyboard Shortcuts"),
		"",
		keyStyle.Render(" + -   ") + descStyle.Render("Zoom time range (double/halve)"),
		keyStyle.Render(" ← →   ") + descStyle.Render("Pan time window (back/forward)"),
		keyStyle.Render("  x    ") + descStyle.Render("Return to now (reset pan)"),
		keyStyle.Render("  r    ") + descStyle.Render("Refresh data"),
		keyStyle.Render(" ↑ ↓   ") + descStyle.Render("Cycle through metrics"),
		keyStyle.Render("  c    ") + descStyle.Render("Change metric query"),
		"",
		keyStyle.Render(" 1-0   ") + descStyle.Render("Toggle series 1-10 visibility"),
		keyStyle.Render("  f    ") + descStyle.Render("Open filter modal (all series)"),
		keyStyle.Render("  l    ") + descStyle.Render("Toggle legend"),
		keyStyle.Render("  a    ") + descStyle.Render("Show all series"),
		keyStyle.Render("  n    ") + descStyle.Render("Hide all series"),
		"",
		keyStyle.Render("  ?    ") + descStyle.Render("Show this help"),
		keyStyle.Render("  q    ") + descStyle.Render("Quit"),
		"",
		modalHint("Press any key to close"),
	}

	return m.renderModal(lines)
}

func (m graphModel) overlayQueryModal(_ string) string {
	metrics := api.GetAllMetrics()
	maxVisible := m.queryModalMaxVisible()
	totalItems := len(m.queryModalFiltered)

	lines := []string{modalTitle("Change Metric"), ""}

	// Search field
	searchStyle := lipgloss.NewStyle().Foreground(styles.Subtext0)
	cursorStyle := lipgloss.NewStyle().Foreground(styles.Blue)
	lines = append(lines, searchStyle.Render("Search: ")+m.queryModalSearch+cursorStyle.Render("▏"))

	if totalItems == 0 {
		noMatch := lipgloss.NewStyle().Foreground(styles.Surface2).Render("  No matching metrics")
		lines = append(lines, "", noMatch, "")
	} else {
		// Scroll up indicator
		if m.queryModalScroll > 0 {
			lines = append(lines, lipgloss.NewStyle().Foreground(styles.Blue).Render("  ↑ more above"))
		} else {
			lines = append(lines, "")
		}

		// Render only visible items from filtered list
		endIndex := min(m.queryModalScroll+maxVisible, totalItems)

		for i := m.queryModalScroll; i < endIndex; i++ {
			metricIdx := m.queryModalFiltered[i]
			metric := metrics[metricIdx]

			prefix := "  "
			if i == m.queryModalIndex {
				prefix = "▸ "
			} else if m.metric != nil && metric.Key == m.metric.Key {
				prefix = "● "
			}

			line := prefix + metric.Title

			if i == m.queryModalIndex {
				line = lipgloss.NewStyle().Background(styles.Surface0).Foreground(styles.Text).Render(line)
			} else if m.metric != nil && metric.Key == m.metric.Key {
				line = lipgloss.NewStyle().Foreground(styles.Blue).Render(line)
			} else {
				line = lipgloss.NewStyle().Foreground(styles.Subtext1).Render(line)
			}

			lines = append(lines, line)
		}

		// Scroll down indicator
		if endIndex < totalItems {
			lines = append(lines, lipgloss.NewStyle().Foreground(styles.Blue).Render("  ↓ more below"))
		} else {
			lines = append(lines, "")
		}
	}

	lines = append(lines, "", modalHint("type to filter • ↑/↓ navigate • enter select • esc close"))
	return m.renderModal(lines)
}

// updateChartSize recalculates chart dimensions based on window and legend size
func (m *graphModel) updateChartSize() {
	// Reserve space for: header (1 line), loading/blank (1 line), legend, help (1 line)
	legendHeight := 0
	if m.legendVisible {
		legendHeight = m.legend.Height()
		if legendHeight == 0 {
			legendHeight = 1 // Reserve minimum space even with no data
		}
	}
	chartHeight := max(m.height-3-legendHeight, 5)
	m.chart.SetSize(m.width-2, chartHeight)
}

// formatDuration formats a duration into a human-readable string
func formatDuration(d time.Duration) string {
	if d >= 24*time.Hour {
		days := d / (24 * time.Hour)
		return fmt.Sprintf("%dd", days)
	}
	if d >= time.Hour {
		hours := d / time.Hour
		return fmt.Sprintf("%dh", hours)
	}
	if d >= time.Minute {
		mins := d / time.Minute
		return fmt.Sprintf("%dm", mins)
	}
	return fmt.Sprintf("%ds", d/time.Second)
}

// filterSeries applies the data source filter to the series list
func (m graphModel) filterSeries(series []api.TimeSeries) []api.TimeSeries {
	// No filter - return all series
	if len(m.filter.include) == 0 && len(m.filter.exclude) == 0 {
		return series
	}

	result := make([]api.TimeSeries, 0, len(series))
	for _, s := range series {
		if m.filter.matchesSeries(s.Name) {
			result = append(result, s)
		}
	}
	return result
}
