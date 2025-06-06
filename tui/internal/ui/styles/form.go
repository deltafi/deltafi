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
package styles

import (
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

func HuhTheme() *huh.Theme {
	t := huh.ThemeBase()

	var (
		error      = Red
		foreground = Text
		comment    = Subtext0
	)

	t.Focused.Base = t.Focused.Base.BorderForeground(Yellow)
	t.Focused.Card = t.Focused.Base
	t.Focused.Title = t.Focused.Title.Foreground(Blue)
	t.Focused.NoteTitle = t.Focused.NoteTitle.Foreground(Lavender)
	t.Focused.Description = t.Focused.Description.Foreground(comment)
	t.Focused.ErrorIndicator = t.Focused.ErrorIndicator.Foreground(error)
	t.Focused.Directory = t.Focused.Directory.Foreground(Lavender)
	t.Focused.File = t.Focused.File.Foreground(foreground)
	t.Focused.ErrorMessage = t.Focused.ErrorMessage.Foreground(error)
	t.Focused.SelectSelector = t.Focused.SelectSelector.Foreground(Yellow)
	t.Focused.NextIndicator = t.Focused.NextIndicator.Foreground(Yellow)
	t.Focused.PrevIndicator = t.Focused.PrevIndicator.Foreground(Yellow)
	t.Focused.Option = t.Focused.Option.Foreground(foreground)
	t.Focused.MultiSelectSelector = t.Focused.MultiSelectSelector.Foreground(Yellow)
	t.Focused.SelectedOption = t.Focused.SelectedOption.Foreground(Green)
	t.Focused.SelectedPrefix = t.Focused.SelectedPrefix.Foreground(Green)
	t.Focused.UnselectedOption = t.Focused.UnselectedOption.Foreground(foreground)
	t.Focused.UnselectedPrefix = t.Focused.UnselectedPrefix.Foreground(comment)
	t.Focused.FocusedButton = t.Focused.FocusedButton.Foreground(Yellow).Background(Blue).Bold(true)
	t.Focused.BlurredButton = t.Focused.BlurredButton.Foreground(Gray).Background(DarkGray)

	t.Focused.TextInput.Cursor = t.Focused.TextInput.Cursor.Foreground(Yellow)
	t.Focused.TextInput.Placeholder = t.Focused.TextInput.Placeholder.Foreground(Surface2)
	t.Focused.TextInput.Prompt = t.Focused.TextInput.Prompt.Foreground(Yellow)

	t.Blurred = t.Focused
	t.Blurred.Base = t.Blurred.Base.BorderStyle(lipgloss.HiddenBorder())
	t.Blurred.Card = t.Blurred.Base
	t.Blurred.NextIndicator = lipgloss.NewStyle()
	t.Blurred.PrevIndicator = lipgloss.NewStyle()

	t.Group.Title = t.Focused.Title
	t.Group.Description = t.Focused.Description

	t.Help.FullDesc = t.Help.FullDesc.Foreground(Gray)
	t.Help.ShortDesc = t.Help.ShortDesc.Foreground(Gray)
	t.Help.ShortKey = t.Help.ShortKey.Foreground(Gray)
	return t
}
