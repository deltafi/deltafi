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
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"

	"github.com/spf13/cobra"
	"sigs.k8s.io/yaml"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/glamour"
	gstyles "github.com/charmbracelet/glamour/styles"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
)

var (
	markdownHeaderColor  string = "39"
	markdownHeaderMargin uint   = 0
)

func renderAsSimpleTable(t api.Table, plain bool) {

	table := components.NewSimpleTable(&t)

	if plain {
		fmt.Println(table.RenderPlain())
	} else {
		fmt.Println(table.Render())
	}
}

func renderAsSimpleTableWithWidth(t api.Table, width int) {
	table := components.NewSimpleTable(&t).Width(width)
	fmt.Println(table.Render())
}

func runProgram(model tea.Model) bool {
	p := tea.NewProgram(model)
	finalModel, err := p.Run()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		return false
	}

	// Check if the model has an error
	if errModel, ok := finalModel.(interface{ GetError() error }); ok {
		if err := errModel.GetError(); err != nil {
			return false
		}
	}

	return true
}

func parseFile(cmd *cobra.Command, out interface{}) error {
	filename, _ := cmd.Flags().GetString("file")
	content, err := os.ReadFile(filename)
	if err != nil {
		return wrapInError("Could not read the file "+filename, err)
	}

	ext := strings.ToLower(filepath.Ext(filename))
	switch ext {
	case ".json":
		if err := json.Unmarshal(content, out); err != nil {
			return wrapInError("Error reading Json", err)
		}
	case ".yaml", ".yml":
		if err := yaml.Unmarshal(content, out); err != nil {
			return wrapInError("Error reading YAML", err)
		}
	default:
		return newError("Unsupported file extension: "+ext, "The extension must be json, yaml, or yml")
	}
	return nil
}

func prettyPrint(cmd *cobra.Command, data interface{}) error {
	format, _ := cmd.Flags().GetString("format")
	switch format {
	case "json":
		plain, _ := cmd.Flags().GetBool("plain")
		return printJSON(data, plain)
	case "yaml":
		return printYAML(data)
	default:
		plain, _ := cmd.Flags().GetBool("plain")
		return printJSON(data, plain)
	}
}

func printJSON(data interface{}, plain bool) error {
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return wrapInError("Error reading Json", err)
	}

	if plain {
		fmt.Println(string(jsonData))
	} else {
		fmt.Println(styles.ColorizeJSON(string(jsonData)))
	}
	return nil
}

func asJSON(data interface{}) (string, error) {
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return "", wrapInError("Error reading Json", err)
	}
	return string(jsonData), nil
}

func printYAML(data interface{}) error {
	yamlData, err := yaml.Marshal(data)
	if err != nil {
		return wrapInError("Error reading YAML", err)
	}

	fmt.Println(string(yamlData))
	return nil
}

func formatState(state string) string {
	switch state {
	case "COMPLETE":
		return styles.SuccessStyle.Render(state)
	case "ERROR":
		return styles.ErrorStyle.Render(state)
	case "FILTERED":
		return styles.WarningStyle.Render(state)
	case "JOINED":
		return styles.SuccessStyle.Render(state)
	case "INHERITED":
		return styles.InfoStyle.Render(state)
	default:
		return state
	}
}

func renderMarkdown(text string, width int) string {
	r, err := glamour.NewTermRenderer(
		GetMarkdownStyle(),
		glamour.WithWordWrap(width),
	)
	if err != nil {
		return text
	}

	return renderGlamourMarkdown(text, true, r)
}

func renderGlamourMarkdown(text string, interactive bool, renderer *glamour.TermRenderer) string {
	var r *glamour.TermRenderer
	var err error

	if renderer == nil {
		// For non-interactive mode
		r, err = glamour.NewTermRenderer(
			GetMarkdownStyle(),
			glamour.WithWordWrap(80),
		)
		if err != nil {
			return text
		}
	} else {
		r = renderer
	}

	rendered, err := r.Render(text)
	if err != nil {
		return text
	}

	// Clean up the rendered text
	rendered = strings.TrimSpace(rendered)
	rendered = strings.ReplaceAll(rendered, "\n\n", "\n")

	// If this is non-interactive mode, add padding for alignment
	if !interactive {
		lines := strings.Split(rendered, "\n")
		for i, line := range lines {
			if i > 0 { // Don't pad the first line since it comes after the icon
				lines[i] = "  " + line
			}
		}
		rendered = strings.Join(lines, "\n")
	}

	return rendered
}

func GetMarkdownStyle() glamour.TermRendererOption {

	gstyles.DarkStyleConfig.Heading.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.H1.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.H2.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.H3.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.H4.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.H5.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.H6.Color = &markdownHeaderColor
	gstyles.DarkStyleConfig.Heading.Margin = &markdownHeaderMargin
	gstyles.DarkStyleConfig.H1.Margin = &markdownHeaderMargin
	gstyles.DarkStyleConfig.H1.Prefix = ""
	gstyles.DarkStyleConfig.H2.Prefix = "▌ "
	gstyles.DarkStyleConfig.H3.Prefix = "┃ "
	gstyles.DarkStyleConfig.H4.Prefix = "│ "
	gstyles.DarkStyleConfig.H5.Prefix = "┆ "
	gstyles.DarkStyleConfig.H6.Prefix = "┊ "

	gstyles.DarkStyleConfig.Heading.BackgroundColor = nil
	gstyles.DarkStyleConfig.H1.BackgroundColor = nil

	return glamour.WithOptions(
		glamour.WithAutoStyle(),
	)
}

func escapedCompletions(values []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	var results []string

	// Get the current shell
	shell := os.Getenv("SHELL")
	isZsh := strings.Contains(shell, "zsh")

	for _, value := range values {
		if isZsh {
			// For zsh, we need simple escaping
			value = strings.ReplaceAll(value, " ", "\\ ")
		} else {
			// For bash, we need crazy triple backslash escaping
			value = strings.ReplaceAll(value, " ", "\\\\\\ ")
		}
		if strings.HasPrefix(value, toComplete) {
			results = append(results, value)
		}
	}
	sort.Strings(results)
	return results, cobra.ShellCompDirectiveNoFileComp | cobra.ShellCompDirectiveDefault
}

func copyFileWithPerms(src, dst string) error {
	sourceFile, err := os.Open(src)
	if err != nil {
		return err
	}
	defer sourceFile.Close()

	sourceInfo, err := sourceFile.Stat()
	if err != nil {
		return err
	}

	destFile, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer destFile.Close()

	_, err = io.Copy(destFile, sourceFile)
	if err != nil {
		return err
	}

	// Copy permissions
	err = os.Chmod(dst, sourceInfo.Mode())
	if err != nil {
		return err
	}

	return destFile.Sync()
}

func executeShellCommand(cmd exec.Cmd) error {
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}
