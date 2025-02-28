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
	"os"
	"strings"

	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/ui/styles"
	"golang.org/x/term"
)

type Error struct {
	Message string
	Context string
}

func wrapInError(summary string, err error) *Error {
	return &Error{
		Message: summary,
		Context: err.Error(),
	}
}

func newError(summary string, context string) *Error {
	return &Error{
		Message: summary,
		Context: context,
	}
}

func (e *Error) Error() string {
	return renderError(e.Message, e.Context)
}

// renderError formats an error message with a summary and context.
func renderError(summary, context string) string {
	// Get terminal width
	width := getTerminalWidth()

	// Define styles
	summaryStyle := lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("1")). // Bright red
		Width(width).
		Align(lipgloss.Left)

	separatorStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("8")). // Gray color
		Width(width).
		Align(lipgloss.Left)

	contextStyle := styles.CommentStyle.Width(width).Align(lipgloss.Left)

	// Create separator line
	separator := separatorStyle.Render(strings.Repeat("─", width))

	// Render the error message
	return lipgloss.JoinVertical(lipgloss.Top,
		summaryStyle.Render(summary),
		separator,
		contextStyle.Render(context),
	)
}

func getTerminalWidth() int {
	width, _, err := term.GetSize(int(os.Stdout.Fd()))
	if err != nil || width < 40 {
		width = 80 // Default width if detection fails
	}
	return width - 4 // Add some padding
}
