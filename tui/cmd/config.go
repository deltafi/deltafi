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
/*
Copyright © 2024 DeltaFi Contributors <deltafi@deltafi.org>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/charmbracelet/bubbles/textarea"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"
	"gopkg.in/yaml.v3"
)

// configCmd represents the config command
var configCmd = &cobra.Command{
	Use:   "config",
	Short: "DeltaFi configuration editor",
	Long:  `DeltaFi configuration editor`,
	Run: func(cmd *cobra.Command, args []string) {
		runProgram(NewConfigCommand())
	},
}

func init() {
	rootCmd.AddCommand(configCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// configCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	// configCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")
}

type pane int

const (
	editorPane pane = iota
	previewPane
)

type ConfigCommand struct {
	BaseCommand
	editor         textarea.Model
	preview        viewport.Model
	config         *app.Config
	width          int
	height         int
	ready          bool
	activePane     pane
	configPath     string
	isModified     bool
	showSavePrompt bool
	editorScroll   int
}

func NewConfigCommand() *ConfigCommand {
	editor := textarea.New()
	editor.Placeholder = "# Edit your configuration here..."
	editor.ShowLineNumbers = true
	editor.Focus()
	editor.CharLimit = 99999

	preview := viewport.New(0, 0)
	preview.SetContent("")

	configPath := filepath.Join(app.TuiPath(), "deltafi.yaml")

	return &ConfigCommand{
		BaseCommand:    NewBaseCommand(),
		editor:         editor,
		preview:        preview,
		config:         app.GetInstance().GetConfig(),
		activePane:     editorPane,
		configPath:     configPath,
		isModified:     false,
		showSavePrompt: false,
		editorScroll:   0,
	}
}

func (c *ConfigCommand) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		tea.EnableMouseCellMotion,
		c.loadConfig,
	)
}

func (c *ConfigCommand) loadConfig() tea.Msg {
	content, err := os.ReadFile(c.configPath)
	if err != nil {
		if os.IsNotExist(err) {
			defaultConfig := app.DefaultConfig()
			yamlContent, err := yaml.Marshal(defaultConfig)
			if err != nil {
				return fmt.Errorf("failed to create default config: %w", err)
			}
			return string(yamlContent)
		}
		return err
	}
	return string(content)
}

func (c *ConfigCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmds []tea.Cmd

	if c.showSavePrompt {
		switch msg := msg.(type) {
		case tea.KeyMsg:
			switch msg.String() {
			case "y", "Y":
				if err := c.saveConfig(); err != nil {
					c.err = err
				}
				return c, tea.Quit
			case "n", "N":
				return c, tea.Quit
			case "esc":
				c.showSavePrompt = false
				return c, nil
			}
		}
		return c, nil
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height

		if !c.ready {
			c.editor.SetWidth(c.width/2 - 4)
			c.editor.SetHeight(c.height - 6)
			c.preview.Width = c.width/2 - 4
			c.preview.Height = c.height - 6
			c.refreshPreview()
			c.ready = true
		}

	case tea.MouseMsg:
		if msg.Type == tea.MouseLeft {
			if msg.X < c.width/2 {
				c.activePane = editorPane
				c.editor.Focus()

				lines := strings.Split(c.editor.Value(), "\n")
				maxVisibleLines := c.height - 6 // Adjust for borders and margins

				// Calculate the clicked line number relative to visible area
				clickY := msg.Y - 3 // Adjust for border and header
				if clickY >= 0 && clickY < maxVisibleLines {
					// Calculate actual line in file considering scroll position
					targetLine := clickY + c.editorScroll
					if targetLine < len(lines) {
						clickX := msg.X - 6 // Adjust for line numbers and border

						// Calculate absolute cursor position
						pos := 0
						for i := 0; i < targetLine; i++ {
							pos += len(lines[i]) + 1 // +1 for newline
						}

						// Add column position
						if clickX < 0 {
							clickX = 0
						}
						if clickX > len(lines[targetLine]) {
							pos += len(lines[targetLine])
						} else {
							pos += clickX
						}

						c.editor.SetCursor(pos)
					}
				}
			} else {
				c.activePane = previewPane
				c.editor.Blur()
			}
		} else if msg.Type == tea.MouseWheelUp {
			if c.activePane == editorPane {
				c.editor.CursorUp()
				if c.editorScroll > 0 {
					c.editorScroll--
				}
			} else {
				c.preview.LineUp(1)
			}
		} else if msg.Type == tea.MouseWheelDown {
			if c.activePane == editorPane {
				c.editor.CursorDown()
				c.editorScroll++
			} else {
				c.preview.LineDown(1)
			}
		}

	case tea.KeyMsg:
		switch msg.String() {
		case "tab":
			if c.activePane == editorPane {
				c.activePane = previewPane
				c.editor.Blur()
			} else {
				c.activePane = editorPane
				c.editor.Focus()
			}
			return c, nil
		case "ctrl+s":
			if err := c.saveConfig(); err != nil {
				c.err = err
				return c, nil
			}
			c.isModified = false
			return c, nil
		case "ctrl+c", "q":
			if c.isModified {
				c.showSavePrompt = true
				return c, nil
			}
			return c, tea.Quit
		case "up":
			if c.activePane == previewPane {
				c.preview.LineUp(1)
			}
		case "down":
			if c.activePane == previewPane {
				c.preview.LineDown(1)
			}
		case "pageup":
			if c.activePane == previewPane {
				c.preview.HalfViewUp()
			}
		case "pagedown":
			if c.activePane == previewPane {
				c.preview.HalfViewDown()
			}
		}

	case string:
		c.editor.SetValue(msg)
		c.refreshPreview()

	case error:
		c.err = msg
		return c, nil
	}

	var cmd tea.Cmd
	prevContent := c.editor.Value()
	c.editor, cmd = c.editor.Update(msg)
	cmds = append(cmds, cmd)

	// Check if content actually changed
	if prevContent != c.editor.Value() {
		c.isModified = true
		c.refreshPreview()
	}

	c.preview, cmd = c.preview.Update(msg)
	cmds = append(cmds, cmd)

	return c, tea.Batch(cmds...)
}

func (c *ConfigCommand) saveConfig() error {
	return os.WriteFile(c.configPath, []byte(c.editor.Value()), 0644)
}

func (c *ConfigCommand) refreshPreview() {
	var config app.Config
	content := c.editor.Value()

	if err := yaml.Unmarshal([]byte(content), &config); err != nil {
		c.preview.SetContent(fmt.Sprintf("Invalid YAML:\n%v", err))
		return
	}

	prettyConfig, err := yaml.Marshal(&config)
	if err != nil {
		c.preview.SetContent(fmt.Sprintf("Error formatting config:\n%v", err))
		return
	}

	c.preview.SetContent(string(prettyConfig))
}

func (c *ConfigCommand) View() string {
	if !c.ready {
		return "Initializing..."
	}

	if c.err != nil {
		return c.RenderError()
	}

	if c.showSavePrompt {
		prompt := lipgloss.NewStyle().
			Border(lipgloss.RoundedBorder()).
			BorderForeground(lipgloss.Color("205")).
			Padding(1, 2).
			Render("Save changes? (y/n/esc)")

		return lipgloss.Place(
			c.width,
			c.height,
			lipgloss.Center,
			lipgloss.Center,
			prompt,
		)
	}

	headerStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#7e22ce")).
		Bold(true).
		PaddingBottom(1)

	helpStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("#626262")).
		MarginLeft(2)

	activeStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("#7e22ce")).
		Margin(0).
		Padding(0).
		Width(c.width/2 - 1)

	inactiveStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("62")).
		Margin(0).
		Padding(0).
		Width(c.width/2 - 1)

	editorStyle := inactiveStyle
	previewStyle := inactiveStyle

	if c.activePane == editorPane {
		editorStyle = activeStyle
	} else {
		previewStyle = activeStyle
	}

	modifiedIndicator := ""
	if c.isModified {
		modifiedIndicator = " [modified]"
	}

	header := headerStyle.Render("Configuration: " + c.configPath + modifiedIndicator)

	editorView := editorStyle.Render(c.editor.View())
	previewView := previewStyle.Render(c.preview.View())

	mainView := lipgloss.JoinHorizontal(lipgloss.Top, editorView, previewView)
	help := helpStyle.Render("tab: switch pane • ctrl+s: save • q: quit")

	return lipgloss.JoinVertical(lipgloss.Left, header, mainView, help)
}
