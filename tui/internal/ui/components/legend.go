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
// ABOUTME: Legend component for displaying series names with colors
// ABOUTME: Shows visibility state and allows toggling series on/off
package components

import (
	"fmt"
	"maps"
	"strings"

	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/styles"
)

// Legend displays series names with their assigned colors
type Legend struct {
	series     []api.TimeSeries
	visibility map[string]bool // persistent visibility state across data refreshes
	width      int
}

// NewLegend creates a new legend component
func NewLegend() *Legend {
	return &Legend{
		visibility: make(map[string]bool),
	}
}

// SetSeries sets the series to display in the legend, updating persistent visibility
func (l *Legend) SetSeries(series []api.TimeSeries) {
	for _, s := range series {
		l.visibility[s.Name] = s.Visible
	}
	l.series = series
}

// UpdateSeries updates the series data while preserving visibility state.
// Visibility is tracked persistently so series that temporarily disappear
// from the data retain their visibility setting when they return.
func (l *Legend) UpdateSeries(series []api.TimeSeries) {
	for i := range series {
		if visible, exists := l.visibility[series[i].Name]; exists {
			series[i].Visible = visible
		} else {
			l.visibility[series[i].Name] = series[i].Visible
		}
	}

	l.series = series
}

// GetVisibility returns a copy of the persistent visibility state
func (l *Legend) GetVisibility() map[string]bool {
	result := make(map[string]bool, len(l.visibility))
	maps.Copy(result, l.visibility)
	return result
}

// SetVisibility replaces the persistent visibility state and applies it to current series
func (l *Legend) SetVisibility(state map[string]bool) {
	l.visibility = make(map[string]bool, len(state))
	maps.Copy(l.visibility, state)
	for i := range l.series {
		if visible, ok := l.visibility[l.series[i].Name]; ok {
			l.series[i].Visible = visible
		}
	}
}

// ClearVisibility resets the persistent visibility state
func (l *Legend) ClearVisibility() {
	l.visibility = make(map[string]bool)
}

// SetWidth sets the available width for the legend
func (l *Legend) SetWidth(width int) {
	l.width = width
}

// View renders the legend as a horizontal list of colored labels
func (l *Legend) View() string {
	if len(l.series) == 0 {
		return ""
	}

	var items []string
	for i, s := range l.series {
		color := SeriesColors[i%len(SeriesColors)]

		// Only show key hint for first 10 series (keys 1-9, 0)
		var keyHint string
		if i < 10 {
			keyNum := (i + 1) % 10 // 1-9, then 0
			keyHint = fmt.Sprintf(" [%d]", keyNum)
		}

		var item string
		if s.Visible {
			// Visible: colored marker with name
			marker := lipgloss.NewStyle().Foreground(color).Render("●")
			name := lipgloss.NewStyle().Foreground(styles.Text).Render(s.Name)
			key := lipgloss.NewStyle().Foreground(styles.Subtext0).Render(keyHint)
			item = fmt.Sprintf("%s %s%s", marker, name, key)
		} else {
			// Hidden: dimmed
			marker := lipgloss.NewStyle().Foreground(styles.Surface2).Render("○")
			name := lipgloss.NewStyle().Foreground(styles.Surface2).Render(s.Name)
			key := lipgloss.NewStyle().Foreground(styles.Surface2).Render(keyHint)
			item = fmt.Sprintf("%s %s%s", marker, name, key)
		}
		items = append(items, item)
	}

	// Join with separators and wrap if needed
	separator := lipgloss.NewStyle().Foreground(styles.Surface1).Render("  │  ")
	return strings.Join(items, separator)
}

// ToggleSeries toggles visibility of the series at the given index (0-based)
func (l *Legend) ToggleSeries(index int) {
	if index >= 0 && index < len(l.series) {
		l.series[index].Visible = !l.series[index].Visible
		l.visibility[l.series[index].Name] = l.series[index].Visible
	}
}

// ShowAll makes all series visible
func (l *Legend) ShowAll() {
	for i := range l.series {
		l.series[i].Visible = true
		l.visibility[l.series[i].Name] = true
	}
}

// HideAll makes all series hidden
func (l *Legend) HideAll() {
	for i := range l.series {
		l.series[i].Visible = false
		l.visibility[l.series[i].Name] = false
	}
}

// GetSeries returns the current series with their visibility state
func (l *Legend) GetSeries() []api.TimeSeries {
	return l.series
}

// Height returns the rendered height of the legend in lines
func (l *Legend) Height() int {
	if len(l.series) == 0 {
		return 0
	}
	// Apply same width styling as used when rendering
	styled := lipgloss.NewStyle().Width(l.width).Render(l.View())
	return lipgloss.Height(styled)
}
