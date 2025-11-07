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
package generator

import (
	"bytes"
	"fmt"
	"os"
	"strings"

	"github.com/charmbracelet/lipgloss"
	"golang.org/x/term"
)

// FileDiff represents the difference between two files
type FileDiff struct {
	Path        string
	OldContent  []byte
	NewContent  []byte
	IsNew       bool // File doesn't exist in old version
	IsDeleted   bool // File exists in old but not in new
	IsModified  bool // File exists in both but content differs
	IsIdentical bool // File exists in both and content is identical
}

// CompareFiles compares two files and returns a FileDiff
func CompareFiles(path string, oldContent, newContent []byte) *FileDiff {
	diff := &FileDiff{
		Path:       path,
		OldContent: oldContent,
		NewContent: newContent,
	}

	if oldContent == nil {
		diff.IsNew = true
		return diff
	}

	if newContent == nil {
		diff.IsDeleted = true
		return diff
	}

	if bytes.Equal(oldContent, newContent) {
		diff.IsIdentical = true
	} else {
		diff.IsModified = true
	}

	return diff
}

// diffLine represents a line in the diff
type diffLine struct {
	oldLine string
	newLine string
	status  string // "added", "removed", "modified", "unchanged"
	oldNum  int
	newNum  int
}

// GenerateUnifiedDiff generates a unified diff string for the file
func (d *FileDiff) GenerateUnifiedDiff() string {
	if d.IsIdentical {
		return ""
	}

	if d.IsNew {
		return fmt.Sprintf("--- /dev/null\n+++ %s\n@@ -0,0 +1,%d @@\n%s",
			d.Path, len(strings.Split(string(d.NewContent), "\n")), string(d.NewContent))
	}

	if d.IsDeleted {
		return fmt.Sprintf("--- %s\n+++ /dev/null\n@@ -1,%d +0,0 @@\n%s",
			d.Path, len(strings.Split(string(d.OldContent), "\n")), string(d.OldContent))
	}

	// For modified files, generate a simple diff
	oldLines := strings.Split(string(d.OldContent), "\n")
	newLines := strings.Split(string(d.NewContent), "\n")

	var diff strings.Builder
	diff.WriteString(fmt.Sprintf("--- %s\n", d.Path))
	diff.WriteString(fmt.Sprintf("+++ %s\n", d.Path))

	// Simple line-by-line comparison
	maxLen := len(oldLines)
	if len(newLines) > maxLen {
		maxLen = len(newLines)
	}

	for i := 0; i < maxLen; i++ {
		if i < len(oldLines) && i < len(newLines) {
			if oldLines[i] != newLines[i] {
				diff.WriteString(fmt.Sprintf("-%s\n", oldLines[i]))
				diff.WriteString(fmt.Sprintf("+%s\n", newLines[i]))
			} else {
				diff.WriteString(fmt.Sprintf(" %s\n", oldLines[i]))
			}
		} else if i < len(oldLines) {
			diff.WriteString(fmt.Sprintf("-%s\n", oldLines[i]))
		} else if i < len(newLines) {
			diff.WriteString(fmt.Sprintf("+%s\n", newLines[i]))
		}
	}

	return diff.String()
}

// GenerateSideBySideDiff generates a colorized side-by-side diff
// Uses terminal width to determine column sizes for proper alignment, similar to csdiff
func (d *FileDiff) GenerateSideBySideDiff() string {
	if d.IsIdentical {
		return ""
	}

	// Get terminal width
	width, _, err := term.GetSize(int(os.Stdout.Fd()))
	if err != nil || width < 80 {
		width = 80 // Default width if detection fails or too narrow
	}

	const (
		lineNumWidth = 6     // Width for line numbers (e.g., "  123 ")
		separator    = " │ " // Separator between columns
		separatorLen = 3     // Length of separator string
		padding      = 2     // Padding on each side
	)

	// Calculate column width based on terminal width
	// Total width = padding + lineNumWidth + columnWidth + separator + lineNumWidth + columnWidth + padding
	// So: columnWidth = (width - padding*2 - lineNumWidth*2 - separatorLen) / 2
	availableWidth := width - padding*2 - lineNumWidth*2 - separatorLen
	columnWidth := availableWidth / 2

	// Ensure minimum column width
	if columnWidth < 20 {
		columnWidth = 20
	}

	// Define styles for diff lines
	removedStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#f38ba8")). // Red
		Background(lipgloss.Color("#3a2d3d"))

	addedStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#a6e3a1")). // Green
		Background(lipgloss.Color("#2d3a2d"))

	unchangedStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#cdd6f4")) // Text color

	lineNumStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#6c7086")). // Muted
		Width(lineNumWidth).
		Align(lipgloss.Right)

	separatorStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#45475a")) // Surface1

	headerStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#89b4fa")). // Blue
		Bold(true)

	// Helper to format a line with line number and content
	formatLine := func(lineNum int, content string, style lipgloss.Style, isLeft bool) string {
		var numStr string
		if lineNum > 0 {
			numStr = fmt.Sprintf("%5d ", lineNum)
		} else {
			numStr = "      " // Empty line number
		}

		// Truncate or pad content to column width (before styling)
		// This ensures the actual character width is correct
		content = truncateOrPad(content, columnWidth)

		// Apply styling
		styledContent := style.Render(content)
		styledNum := lineNumStyle.Render(numStr)

		return styledNum + styledContent
	}

	if d.IsNew {
		newLines := strings.Split(string(d.NewContent), "\n")
		var lines []string

		// Header
		leftHeader := truncateOrPad("(new file)", lineNumWidth+columnWidth)
		rightHeader := truncateOrPad(d.Path, lineNumWidth+columnWidth)
		lines = append(lines, headerStyle.Render(leftHeader)+separatorStyle.Render(separator)+headerStyle.Render(rightHeader))
		lines = append(lines, strings.Repeat("─", lineNumWidth+columnWidth)+separatorStyle.Render(separator)+strings.Repeat("─", lineNumWidth+columnWidth))

		// Content
		for i, line := range newLines {
			leftLine := formatLine(0, "", unchangedStyle, true)
			rightLine := formatLine(i+1, line, addedStyle, false)
			lines = append(lines, leftLine+separatorStyle.Render(separator)+rightLine)
		}

		return strings.Join(lines, "\n")
	}

	if d.IsDeleted {
		oldLines := strings.Split(string(d.OldContent), "\n")
		var lines []string

		// Header
		leftHeader := truncateOrPad(d.Path, lineNumWidth+columnWidth)
		rightHeader := truncateOrPad("(deleted)", lineNumWidth+columnWidth)
		lines = append(lines, headerStyle.Render(leftHeader)+separatorStyle.Render(separator)+headerStyle.Render(rightHeader))
		lines = append(lines, strings.Repeat("─", lineNumWidth+columnWidth)+separatorStyle.Render(separator)+strings.Repeat("─", lineNumWidth+columnWidth))

		// Content
		for i, line := range oldLines {
			leftLine := formatLine(i+1, line, removedStyle, true)
			rightLine := formatLine(0, "", unchangedStyle, false)
			lines = append(lines, leftLine+separatorStyle.Render(separator)+rightLine)
		}

		return strings.Join(lines, "\n")
	}

	// For modified files, generate side-by-side diff
	oldLines := strings.Split(string(d.OldContent), "\n")
	newLines := strings.Split(string(d.NewContent), "\n")

	// Compute diff lines
	diffLines := computeDiffLines(oldLines, newLines)

	var lines []string

	// Header
	leftHeader := truncateOrPad(d.Path+" (old)", lineNumWidth+columnWidth)
	rightHeader := truncateOrPad(d.Path+" (new)", lineNumWidth+columnWidth)
	lines = append(lines, headerStyle.Render(leftHeader)+separatorStyle.Render(separator)+headerStyle.Render(rightHeader))
	lines = append(lines, strings.Repeat("─", lineNumWidth+columnWidth)+separatorStyle.Render(separator)+strings.Repeat("─", lineNumWidth+columnWidth))

	// Content
	for _, dl := range diffLines {
		var leftLine, rightLine string

		switch dl.status {
		case "removed":
			leftLine = formatLine(dl.oldNum, dl.oldLine, removedStyle, true)
			rightLine = formatLine(0, "", unchangedStyle, false)
		case "added":
			leftLine = formatLine(0, "", unchangedStyle, true)
			rightLine = formatLine(dl.newNum, dl.newLine, addedStyle, false)
		case "modified":
			leftLine = formatLine(dl.oldNum, dl.oldLine, removedStyle, true)
			rightLine = formatLine(dl.newNum, dl.newLine, addedStyle, false)
		case "unchanged":
			leftLine = formatLine(dl.oldNum, dl.oldLine, unchangedStyle, true)
			rightLine = formatLine(dl.newNum, dl.newLine, unchangedStyle, false)
		}

		lines = append(lines, leftLine+separatorStyle.Render(separator)+rightLine)
	}

	return strings.Join(lines, "\n")
}

// truncateOrPad truncates or pads a string to the specified width
func truncateOrPad(s string, width int) string {
	if len(s) > width {
		return s[:width-3] + "..."
	}
	return s + strings.Repeat(" ", width-len(s))
}

// computeDiffLines computes the diff between old and new lines
func computeDiffLines(oldLines, newLines []string) []diffLine {
	var result []diffLine

	// Simple line-by-line comparison
	maxLen := len(oldLines)
	if len(newLines) > maxLen {
		maxLen = len(newLines)
	}

	oldNum := 1
	newNum := 1

	for i := 0; i < maxLen; i++ {
		hasOld := i < len(oldLines)
		hasNew := i < len(newLines)

		if hasOld && hasNew {
			if oldLines[i] == newLines[i] {
				result = append(result, diffLine{
					oldLine: oldLines[i],
					newLine: newLines[i],
					status:  "unchanged",
					oldNum:  oldNum,
					newNum:  newNum,
				})
				oldNum++
				newNum++
			} else {
				// Modified line
				result = append(result, diffLine{
					oldLine: oldLines[i],
					newLine: newLines[i],
					status:  "modified",
					oldNum:  oldNum,
					newNum:  newNum,
				})
				oldNum++
				newNum++
			}
		} else if hasOld {
			// Removed line
			result = append(result, diffLine{
				oldLine: oldLines[i],
				newLine: "",
				status:  "removed",
				oldNum:  oldNum,
				newNum:  0,
			})
			oldNum++
		} else if hasNew {
			// Added line
			result = append(result, diffLine{
				oldLine: "",
				newLine: newLines[i],
				status:  "added",
				oldNum:  0,
				newNum:  newNum,
			})
			newNum++
		}
	}

	return result
}

// ReadFileIfExists reads a file if it exists, returns nil if it doesn't
func ReadFileIfExists(path string) ([]byte, error) {
	content, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return nil, nil
	}
	return content, err
}
