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
	"os"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/styles"

	"github.com/charmbracelet/lipgloss"
	"github.com/charmbracelet/lipgloss/table"
)

type SimpleTable struct {
	Table       *api.Table
	re          *lipgloss.Renderer
	BaseStyle   lipgloss.Style
	HeaderStyle lipgloss.Style
	Border      lipgloss.Border
	BorderStyle lipgloss.Style
	StyleFunc   func(row, col int) lipgloss.Style
}

func NewSimpleTable(t *api.Table) *SimpleTable {

	re := lipgloss.NewRenderer(os.Stdout)
	baseStyle := re.NewStyle().Padding(0, 1)

	return &SimpleTable{
		Table:       t,
		re:          re,
		BaseStyle:   baseStyle,
		HeaderStyle: baseStyle.Foreground(styles.Blue).Bold(true),
		Border:      lipgloss.RoundedBorder(),
		BorderStyle: re.NewStyle().Foreground(styles.Surface1),
	}
}

func (t *SimpleTable) Render() string {

	tab := table.New().
		Headers(t.Table.Columns...).
		Rows(t.Table.Rows...).
		Border(t.Border).
		BorderStyle(t.BorderStyle).
		StyleFunc(func(row, col int) lipgloss.Style {
			if row == table.HeaderRow {
				return t.HeaderStyle
			}

			return t.BaseStyle
		})

	return tab.Render()
}

func (t *SimpleTable) RenderPlain() string {
	tab := table.New().
		Headers(t.Table.Columns...).
		Rows(t.Table.Rows...).
		Border(lipgloss.HiddenBorder()).
		BorderStyle(t.BorderStyle).
		StyleFunc(func(row, col int) lipgloss.Style {
			return t.BaseStyle
		})

	return tab.Render()
}
