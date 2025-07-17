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
package components

import (
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/styles"

	"github.com/charmbracelet/lipgloss"
	"github.com/charmbracelet/lipgloss/table"
)

type SimpleTable struct {
	Table       *api.Table
	BaseStyle   lipgloss.Style
	HeaderStyle lipgloss.Style
	OddRowStyle lipgloss.Style
	Border      lipgloss.Border
	BorderStyle lipgloss.Style
	width       int
	StyleFunc   func(row, col int) lipgloss.Style
	verbose     bool
}

func NewSimpleTable(t *api.Table) *SimpleTable {
	baseStyle := lipgloss.NewStyle().Padding(0, 1)
	oddRowStyle := baseStyle

	return &SimpleTable{
		Table:       t,
		BaseStyle:   baseStyle,
		HeaderStyle: baseStyle.Foreground(styles.Blue).Bold(true),
		OddRowStyle: oddRowStyle,
		Border:      lipgloss.RoundedBorder(),
		BorderStyle: lipgloss.NewStyle().Foreground(styles.Surface1),
		verbose:     false,
	}
}

func (t *SimpleTable) Width(width int) *SimpleTable {
	t.width = width
	return t
}

func (t *SimpleTable) Verbose(verbose bool) *SimpleTable {
	t.verbose = verbose
	return t
}

func (t *SimpleTable) calculateKeyColumnWidth() int {
	maxWidth := 0
	// Check header width
	if len(t.Table.Columns) > 0 {
		maxWidth = len(t.Table.Columns[0])
	}
	// Check all key values in rows
	for _, row := range t.Table.Rows {
		if len(row) > 0 {
			keyWidth := len(row[0])
			if keyWidth > maxWidth {
				maxWidth = keyWidth
			}
		}
	}
	// Add padding
	return maxWidth + 2
}

func (t *SimpleTable) getRowsWithSpacing() [][]string {
	if !t.verbose {
		return t.Table.Rows
	}

	var rows [][]string
	for i, row := range t.Table.Rows {
		rows = append(rows, row)
		if i < len(t.Table.Rows)-1 {
			// Add empty row after each data row except the last one
			emptyRow := make([]string, len(row))
			rows = append(rows, emptyRow)
		}
	}
	return rows
}

func (t *SimpleTable) Render() string {
	keyColumnWidth := t.calculateKeyColumnWidth()
	rows := t.getRowsWithSpacing()

	tab := table.New().
		Headers(t.Table.Columns...).
		Rows(rows...).
		Border(t.Border).
		BorderStyle(t.BorderStyle).
		Width(t.width).
		Wrap(true).
		StyleFunc(func(row, col int) lipgloss.Style {
			if row == table.HeaderRow {
				return t.HeaderStyle
			}

			// Skip styling for empty rows
			if t.verbose && row%2 == 1 {
				return lipgloss.NewStyle()
			}

			// Calculate the actual data row index for alternating colors
			dataRowIndex := row
			if t.verbose {
				dataRowIndex = row / 2
			}

			if dataRowIndex%2 == 1 {
				return t.OddRowStyle
			}
			return t.BaseStyle
		})

	// Set the width of the first column by padding the content
	if len(t.Table.Columns) > 0 {
		// Create a new style with the calculated width
		keyStyle := t.BaseStyle.Width(keyColumnWidth)
		// Apply the style to the first column
		tab = tab.StyleFunc(func(row, col int) lipgloss.Style {
			dataRowIndex := row
			if t.verbose {
				dataRowIndex = row / 2
			}
			if col == 0 {
				if dataRowIndex%2 == 1 {
					return t.OddRowStyle.Width(keyColumnWidth)
				}
				return keyStyle
			}
			if row == table.HeaderRow {
				return t.HeaderStyle
			}

			// Skip styling for empty rows
			if t.verbose && row%2 == 1 {
				return lipgloss.NewStyle()
			}

			if dataRowIndex%2 == 1 {
				return t.OddRowStyle
			}
			return t.BaseStyle
		})
	}

	return tab.Render()
}

func (t *SimpleTable) RenderPlain() string {
	keyColumnWidth := t.calculateKeyColumnWidth()
	rows := t.getRowsWithSpacing()

	tab := table.New().
		Headers(t.Table.Columns...).
		Rows(rows...).
		Border(lipgloss.MarkdownBorder()).
		BorderBottom(false).
		BorderTop(false).
		BorderHeader(false).
		BorderLeft(false).
		BorderRight(false).
		BorderStyle(t.BorderStyle).
		Wrap(false).
		StyleFunc(func(row, col int) lipgloss.Style {
			if col == 0 {
				return t.BaseStyle.Width(keyColumnWidth)
			}

			// Skip styling for empty rows
			if t.verbose && row%2 == 1 {
				return lipgloss.NewStyle()
			}

			// Calculate the actual data row index for alternating colors
			dataRowIndex := row
			if t.verbose {
				dataRowIndex = row / 2
			}

			if dataRowIndex%2 == 1 {
				return t.OddRowStyle
			}
			return t.BaseStyle
		})

	return tab.Render()
}
