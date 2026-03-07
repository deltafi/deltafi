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
// ABOUTME: Terminal line chart component for time-series data visualization
// ABOUTME: Wraps ntcharts timeserieslinechart with DeltaFi styling
package components

import (
	"fmt"
	"time"

	"github.com/NimbleMarkets/ntcharts/linechart"
	"github.com/NimbleMarkets/ntcharts/linechart/timeserieslinechart"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/styles"
)

// hourMinuteLabelFormatter returns a formatter that displays time as hh:mm
func hourMinuteLabelFormatter() linechart.LabelFormatter {
	return func(i int, v float64) string {
		t := time.Unix(int64(v), 0).Local()
		return t.Format("15:04")
	}
}

// compactValueFormatter returns a formatter that displays values with SI suffixes
func compactValueFormatter() linechart.LabelFormatter {
	return func(i int, v float64) string {
		return formatCompactValue(v)
	}
}

// formatCompactValue formats a number with SI suffixes for compact display
func formatCompactValue(v float64) string {
	abs := v
	if abs < 0 {
		abs = -abs
	}

	switch {
	case abs >= 1_000_000_000_000:
		return fmt.Sprintf("%.1fT", v/1_000_000_000_000)
	case abs >= 1_000_000_000:
		return fmt.Sprintf("%.1fG", v/1_000_000_000)
	case abs >= 1_000_000:
		return fmt.Sprintf("%.1fM", v/1_000_000)
	case abs >= 1_000:
		return fmt.Sprintf("%.1fK", v/1_000)
	case abs == 0:
		return "0"
	case abs < 0.01:
		return fmt.Sprintf("%.4f", v)
	case abs < 1:
		return fmt.Sprintf("%.2f", v)
	default:
		return fmt.Sprintf("%.0f", v)
	}
}

// ChartStyle represents the rendering style for the chart
type ChartStyle string

const (
	ChartStyleBraille ChartStyle = "braille"
	ChartStyleLine    ChartStyle = "line"
)

// SeriesColors are the colors assigned to different data series
var SeriesColors = []lipgloss.AdaptiveColor{
	styles.Blue,
	styles.Green,
	styles.Mauve,
	styles.Peach,
	styles.Teal,
	styles.Pink,
	styles.Yellow,
	styles.Lavender,
	styles.Sapphire,
	styles.Sky,
}

// LineChart wraps ntcharts timeserieslinechart with multi-series support
type LineChart struct {
	chart     timeserieslinechart.Model
	series    []api.TimeSeries
	width     int
	height    int
	style     ChartStyle
	timeStart time.Time
	timeEnd   time.Time
}

// NewLineChart creates a new line chart with the given dimensions and style
func NewLineChart(width, height int, style ChartStyle) *LineChart {
	chart := timeserieslinechart.New(width, height,
		timeserieslinechart.WithAxesStyles(
			lipgloss.NewStyle().Foreground(styles.Subtext0),
			lipgloss.NewStyle().Foreground(styles.Subtext0),
		),
		timeserieslinechart.WithXLabelFormatter(hourMinuteLabelFormatter()),
		timeserieslinechart.WithYLabelFormatter(compactValueFormatter()),
	)
	return &LineChart{
		chart:  chart,
		width:  width,
		height: height,
		style:  style,
	}
}

// SetStyle sets the chart rendering style
func (lc *LineChart) SetStyle(style ChartStyle) {
	lc.style = style
}

// SetSize updates the chart dimensions
func (lc *LineChart) SetSize(width, height int) {
	lc.width = width
	lc.height = height
	lc.chart.Resize(width, height)
}

// SetTimeRange sets the X-axis view time range explicitly
func (lc *LineChart) SetTimeRange(start, end time.Time) {
	lc.timeStart = start
	lc.timeEnd = end
	lc.chart.SetViewTimeRange(start, end)
}

// SetSeries sets the data to display. Each TimeSeries becomes a named dataset.
func (lc *LineChart) SetSeries(series []api.TimeSeries) {
	lc.series = series

	// Clear existing data by recreating the chart
	lc.chart = timeserieslinechart.New(lc.width, lc.height,
		timeserieslinechart.WithAxesStyles(
			lipgloss.NewStyle().Foreground(styles.Subtext0),
			lipgloss.NewStyle().Foreground(styles.Subtext0),
		),
		timeserieslinechart.WithXLabelFormatter(hourMinuteLabelFormatter()),
		timeserieslinechart.WithYLabelFormatter(compactValueFormatter()),
	)

	// Calculate max data points based on width (2x width, minimum 10)
	maxDataPoints := max(lc.width*2, 10)

	// Add each series as a named dataset with a color
	for i, s := range series {
		if !s.Visible {
			continue
		}

		color := SeriesColors[i%len(SeriesColors)]
		style := lipgloss.NewStyle().Foreground(color)

		// Downsample data points if needed
		dataPoints := lc.downsampleDataPoints(s.DataPoints, maxDataPoints)

		for _, dp := range dataPoints {
			if dp.IsNull {
				continue
			}
			lc.chart.PushDataSet(s.Name, timeserieslinechart.TimePoint{
				Time:  dp.Timestamp,
				Value: dp.Value,
			})
		}

		// Set style for this dataset
		lc.chart.SetDataSetStyle(s.Name, style)
	}

	// Apply stored view time range after data is pushed
	if !lc.timeStart.IsZero() && !lc.timeEnd.IsZero() {
		lc.chart.SetViewTimeRange(lc.timeStart, lc.timeEnd)
	}
}

// downsampleDataPoints reduces the number of data points to maxPoints
func (lc *LineChart) downsampleDataPoints(points []api.DataPoint, maxPoints int) []api.DataPoint {
	if len(points) <= maxPoints {
		return points
	}

	result := make([]api.DataPoint, 0, maxPoints)
	step := float64(len(points)) / float64(maxPoints)

	for i := range maxPoints {
		index := int(float64(i) * step)
		if index >= len(points) {
			index = len(points) - 1
		}
		result = append(result, points[index])
	}

	return result
}

// GetVisibleSeriesNames returns the names of visible series in order
func (lc *LineChart) GetVisibleSeriesNames() []string {
	var names []string
	for _, s := range lc.series {
		if s.Visible {
			names = append(names, s.Name)
		}
	}
	return names
}

// View renders the chart and returns the string representation
func (lc *LineChart) View() string {
	visibleNames := lc.GetVisibleSeriesNames()
	if len(visibleNames) == 0 {
		return lipgloss.NewStyle().
			Width(lc.width).
			Height(lc.height).
			Align(lipgloss.Center, lipgloss.Center).
			Foreground(styles.Subtext0).
			Render("No data to display")
	}

	switch lc.style {
	case ChartStyleLine:
		lc.chart.DrawDataSets(visibleNames)
	default:
		lc.chart.DrawBrailleDataSets(visibleNames)
	}
	return lc.chart.View()
}
