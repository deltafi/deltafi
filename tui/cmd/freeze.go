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
	"fmt"
	"os"
	"path/filepath"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
	"gopkg.in/yaml.v3"
)

type freezeWizardStep int

const (
	freezeCheckStep freezeWizardStep = iota
	freezePluginsStep
	freezeExtraImagesStep
	freezeSnapshotStep
	freezeCompleteStep
)

type FreezeCommand struct {
	BaseCommand
	step           freezeWizardStep
	width          int
	height         int
	ready          bool
	help           help.Model
	keys           freezeKeyMap
	selectedOption int
	plugins        []string
	extraImages    []string
	textInput      textinput.Model
	pluginList     list.Model
	imageList      list.Model
	err            error
	hasSnapshot    bool
}

type freezeKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Select key.Binding
	Back   key.Binding
	Add    key.Binding
	Delete key.Binding
	Edit   key.Binding
	Help   key.Binding
	Quit   key.Binding
}

func (k freezeKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Select, k.Back, k.Add, k.Delete, k.Edit}
}

func (k freezeKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Select},
		{k.Add, k.Delete, k.Edit},
		{k.Back, k.Help, k.Quit},
	}
}

var freezeKeys = freezeKeyMap{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("↑/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("↓/j", "down"),
	),
	Select: key.NewBinding(
		key.WithKeys("enter", "space"),
		key.WithHelp("enter", "select"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc"),
		key.WithHelp("esc", "back"),
	),
	Add: key.NewBinding(
		key.WithKeys("a"),
		key.WithHelp("a", "add"),
	),
	Delete: key.NewBinding(
		key.WithKeys("d"),
		key.WithHelp("d", "delete"),
	),
	Edit: key.NewBinding(
		key.WithKeys("e"),
		key.WithHelp("e", "edit"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("^c", "quit"),
	),
}

var freezeCmd = &cobra.Command{
	Use:     "freeze",
	Short:   "Create preconfigured DeltaFisystem distro",
	Long:    `Interactive wizard to create a preconfigured DeltaFisystem distro`,
	GroupID: "deltafi",
	Run: func(cmd *cobra.Command, args []string) {
		command := NewFreezeCommand()
		runProgram(command)
	},
}

func init() {
	rootCmd.AddCommand(freezeCmd)
}

func NewFreezeCommand() *FreezeCommand {
	help := help.New()
	help.ShowAll = false

	ti := textinput.New()
	ti.Placeholder = "Docker image tag"
	ti.Width = 50

	cmd := &FreezeCommand{
		BaseCommand:    NewBaseCommand(),
		step:           freezeCheckStep,
		help:           help,
		keys:           freezeKeys,
		selectedOption: 0,
		plugins:        make([]string, 0),
		extraImages:    make([]string, 0),
		textInput:      ti,
	}

	// Load existing plugins and extra images if available
	if err := cmd.loadPlugins(); err != nil {
		cmd.err = err
		return cmd
	}

	if err := cmd.loadExtraImages(); err != nil {
		cmd.err = err
		return cmd
	}

	return cmd
}

func (c *FreezeCommand) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		c.spinner.Tick,
		textinput.Blink,
	)
}

func (c *FreezeCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height
		c.help.Width = msg.Width
		c.ready = true

	case tea.KeyMsg:
		if key.Matches(msg, c.keys.Quit) {
			return c, tea.Quit
		}

		switch c.step {
		case freezeCheckStep:
			// Check if we can proceed
			if app.GetOrchestrationMode() != orchestration.Compose {
				c.err = fmt.Errorf("freeze command is only available in Compose orchestration mode")
				return c, nil
			}
			if !app.IsRunning() {
				c.err = fmt.Errorf("DeltaFi must be running to use the freeze command")
				return c, nil
			}
			c.step = freezePluginsStep
			c.selectedOption = len(c.plugins) + 1 // Select [Save and Continue]

		case freezePluginsStep:
			if c.textInput.Focused() {
				if key.Matches(msg, c.keys.Select) {
					value := c.textInput.Value()
					if value != "" {
						if c.selectedOption < len(c.plugins) {
							// Edit mode
							c.plugins[c.selectedOption] = value
						} else {
							// Add mode
							c.plugins = append(c.plugins, value)
						}
						c.textInput.SetValue("")
						c.textInput.Blur()
					}
				} else if key.Matches(msg, c.keys.Back) {
					c.textInput.SetValue("")
					c.textInput.Blur()
				} else {
					var cmd tea.Cmd
					c.textInput, cmd = c.textInput.Update(msg)
					return c, cmd
				}
			} else {
				switch {
				case key.Matches(msg, c.keys.Add):
					c.textInput.Focus()
				case key.Matches(msg, c.keys.Delete):
					if len(c.plugins) > 0 && c.selectedOption < len(c.plugins) {
						c.plugins = append(c.plugins[:c.selectedOption], c.plugins[c.selectedOption+1:]...)
						if c.selectedOption >= len(c.plugins) {
							c.selectedOption = len(c.plugins) - 1
						}
					}
				case key.Matches(msg, c.keys.Edit):
					if len(c.plugins) > 0 && c.selectedOption < len(c.plugins) {
						c.textInput.SetValue(c.plugins[c.selectedOption])
						c.textInput.Focus()
					}
				case key.Matches(msg, c.keys.Select):
					if c.selectedOption == len(c.plugins) {
						// User selected [Add New Plugin]
						c.textInput.Focus()
					} else if c.selectedOption == len(c.plugins)+1 {
						// User selected [Save and Continue]
						if err := c.savePlugins(); err != nil {
							c.err = err
							return c, nil
						}
						c.step = freezeExtraImagesStep
						c.selectedOption = len(c.extraImages) + 1 // Select [Save and Continue]
					}
				case key.Matches(msg, c.keys.Up):
					if c.selectedOption > 0 {
						c.selectedOption--
					}
				case key.Matches(msg, c.keys.Down):
					if c.selectedOption < len(c.plugins)+1 {
						c.selectedOption++
					}
				}
			}

		case freezeExtraImagesStep:
			if c.textInput.Focused() {
				if key.Matches(msg, c.keys.Select) {
					value := c.textInput.Value()
					if value != "" {
						if c.selectedOption < len(c.extraImages) {
							// Edit mode
							c.extraImages[c.selectedOption] = value
						} else {
							// Add mode
							c.extraImages = append(c.extraImages, value)
						}
						c.textInput.SetValue("")
						c.textInput.Blur()
					}
				} else if key.Matches(msg, c.keys.Back) {
					c.textInput.SetValue("")
					c.textInput.Blur()
				} else {
					var cmd tea.Cmd
					c.textInput, cmd = c.textInput.Update(msg)
					return c, cmd
				}
			} else {
				switch {
				case key.Matches(msg, c.keys.Add):
					c.textInput.Focus()
				case key.Matches(msg, c.keys.Delete):
					if len(c.extraImages) > 0 && c.selectedOption < len(c.extraImages) {
						c.extraImages = append(c.extraImages[:c.selectedOption], c.extraImages[c.selectedOption+1:]...)
						if c.selectedOption >= len(c.extraImages) {
							c.selectedOption = len(c.extraImages) - 1
						}
					}
				case key.Matches(msg, c.keys.Edit):
					if len(c.extraImages) > 0 && c.selectedOption < len(c.extraImages) {
						c.textInput.SetValue(c.extraImages[c.selectedOption])
						c.textInput.Focus()
					}
				case key.Matches(msg, c.keys.Select):
					if c.selectedOption == len(c.extraImages) {
						// User selected [Add New Image]
						c.textInput.Focus()
					} else if c.selectedOption == len(c.extraImages)+1 {
						// User selected [Save and Continue]
						if err := c.saveExtraImages(); err != nil {
							c.err = err
							return c, nil
						}
						// Check for snapshot before moving to complete step
						c.hasSnapshot = c.checkForSnapshot()
						if c.hasSnapshot {
							c.step = freezeSnapshotStep
						} else {
							c.step = freezeCompleteStep
						}
						c.selectedOption = 0
						return c, cmd
					}
				case key.Matches(msg, c.keys.Up):
					if c.selectedOption > 0 {
						c.selectedOption--
					}
				case key.Matches(msg, c.keys.Down):
					if c.selectedOption < len(c.extraImages)+1 {
						c.selectedOption++
					}
				case key.Matches(msg, c.keys.Back):
					c.selectedOption = 0
					c.step = freezePluginsStep
					return c, cmd
				}
			}

		case freezeSnapshotStep:
			switch {
			case key.Matches(msg, c.keys.Up), key.Matches(msg, c.keys.Down):
				c.selectedOption = 1 - c.selectedOption // Toggle between 0 and 1
				return c, cmd
			case key.Matches(msg, c.keys.Select):
				if c.selectedOption == 0 { // Yes
					if err := c.restoreSnapshot(); err != nil {
						c.err = err
						return c, nil
					}
				}
				c.step = freezeCompleteStep
				c.selectedOption = 0
				return c, cmd
			case key.Matches(msg, c.keys.Back):
				c.step = freezeExtraImagesStep
				c.selectedOption = 0
				return c, cmd
			}

		case freezeCompleteStep:
			if key.Matches(msg, c.keys.Select) {
				return c, tea.Quit
			}
		}

		if key.Matches(msg, c.keys.Help) {
			c.help.ShowAll = !c.help.ShowAll
		}
	}

	return c, cmd
}

func (c *FreezeCommand) View() string {
	if !c.ready {
		return "Initializing..."
	}

	if c.err != nil {
		return c.RenderError()
	}

	mainStyle := lipgloss.NewStyle().
		Padding(1).
		Border(lipgloss.RoundedBorder()).
		BorderForeground(styles.Blue).
		Width(c.width - 2).
		Height(c.height - 2)

	contentStyle := lipgloss.NewStyle().
		Padding(0).
		MarginLeft(1).
		MarginRight(1)

	headerStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Bold(true).
		MarginLeft(2).
		MarginBottom(1)

	var content string
	switch c.step {
	case freezeCheckStep:
		content = c.renderCheck()
	case freezePluginsStep:
		content = c.renderPlugins()
	case freezeExtraImagesStep:
		content = c.renderExtraImages()
	case freezeSnapshotStep:
		content = c.renderSnapshot()
	case freezeCompleteStep:
		content = c.renderComplete()
	}

	header := headerStyle.Render("DeltaFi System Configuration Freeze")
	box := contentStyle.Render(content)

	help := lipgloss.NewStyle().
		MarginLeft(2).
		PaddingTop(1).
		Render(c.help.View(c.keys))

	fullView := lipgloss.JoinVertical(lipgloss.Left,
		header,
		box,
		help,
	)

	return mainStyle.Render(fullView)
}

func (c *FreezeCommand) renderCheck() string {
	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render("Checking system requirements...")
}

func (c *FreezeCommand) renderPlugins() string {
	var content string
	if c.textInput.Focused() {
		content = "Enter plugin docker tag:\n\n" + c.textInput.View()
	} else {
		content = "Configure Plugins\n\n"
		for i, plugin := range c.plugins {
			if i == c.selectedOption {
				content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render(plugin) + "\n"
			} else {
				content += styles.MenuItemStyle.PaddingLeft(3).Render(plugin) + "\n"
			}
		}
		if c.selectedOption == len(c.plugins) {
			content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render("[Add New Plugin]") + "\n"
		} else {
			content += styles.MenuItemStyle.PaddingLeft(3).Render("[Add New Plugin]") + "\n"
		}
		if c.selectedOption == len(c.plugins)+1 {
			content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render("[Save and Continue]") + "\n"
		} else {
			content += styles.MenuItemStyle.PaddingLeft(3).Render("[Save and Continue]") + "\n"
		}
	}

	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render(content)
}

func (c *FreezeCommand) renderExtraImages() string {
	var content string
	if c.textInput.Focused() {
		content = "Enter extra image docker tag:\n\n" + c.textInput.View()
	} else {
		content = "Configure Extra Images\n\n"
		for i, image := range c.extraImages {
			if i == c.selectedOption {
				content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render(image) + "\n"
			} else {
				content += styles.MenuItemStyle.PaddingLeft(3).Render(image) + "\n"
			}
		}
		if c.selectedOption == len(c.extraImages) {
			content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render("[Add New Image]") + "\n"
		} else {
			content += styles.MenuItemStyle.PaddingLeft(3).Render("[Add New Image]") + "\n"
		}
		if c.selectedOption == len(c.extraImages)+1 {
			content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render("[Save and Continue]") + "\n"
		} else {
			content += styles.MenuItemStyle.PaddingLeft(3).Render("[Save and Continue]") + "\n"
		}
	}

	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render(content)
}

func (c *FreezeCommand) renderSnapshot() string {
	content := "A site snapshot exists. Do you want to restore the site snapshot into DeltaFi?\n\n"

	options := []string{"Yes", "No"}
	for i, option := range options {
		if i == c.selectedOption {
			content += styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render(option) + "\n"
		} else {
			content += styles.MenuItemStyle.PaddingLeft(3).Render(option) + "\n"
		}
	}

	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render(content)
}

func (c *FreezeCommand) renderComplete() string {
	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render("Configuration saved successfully!\n\n" +
			"Press ENTER to exit...")
}

func (c *FreezeCommand) loadPlugins() error {
	sitePath := app.GetInstance().GetConfig().SiteDirectory
	pluginsFile := filepath.Join(sitePath, "plugins.yaml")

	// Check if file exists
	if _, err := os.Stat(pluginsFile); os.IsNotExist(err) {
		return nil // File doesn't exist, which is fine
	}

	// Read and parse the YAML file
	data, err := os.ReadFile(pluginsFile)
	if err != nil {
		return fmt.Errorf("failed to read plugins file: %w", err)
	}

	// Unmarshal directly into plugins slice
	if err := yaml.Unmarshal(data, &c.plugins); err != nil {
		return fmt.Errorf("failed to parse plugins file: %w", err)
	}

	return nil
}

func (c *FreezeCommand) savePlugins() error {
	sitePath := app.GetInstance().GetConfig().SiteDirectory
	pluginsFile := filepath.Join(sitePath, "plugins.yaml")

	// Create site directory if it doesn't exist
	if err := os.MkdirAll(sitePath, 0755); err != nil {
		return fmt.Errorf("failed to create site directory: %w", err)
	}

	// Marshal plugins directly to YAML as a list
	data, err := yaml.Marshal(c.plugins)
	if err != nil {
		return fmt.Errorf("failed to marshal plugins: %w", err)
	}

	// Write to file
	if err := os.WriteFile(pluginsFile, data, 0644); err != nil {
		return fmt.Errorf("failed to write plugins file: %w", err)
	}

	return nil
}

func (c *FreezeCommand) saveExtraImages() error {
	sitePath := app.GetInstance().GetConfig().SiteDirectory
	imagesFile := filepath.Join(sitePath, "extra_images.yaml")

	// Create site directory if it doesn't exist
	if err := os.MkdirAll(sitePath, 0755); err != nil {
		return fmt.Errorf("failed to create site directory: %w", err)
	}

	// Marshal extra images to YAML
	data, err := yaml.Marshal(c.extraImages)
	if err != nil {
		return fmt.Errorf("failed to marshal extra images: %w", err)
	}

	// Write to file
	if err := os.WriteFile(imagesFile, data, 0644); err != nil {
		return fmt.Errorf("failed to write extra images file: %w", err)
	}

	return nil
}

func (c *FreezeCommand) checkForSnapshot() bool {
	sitePath := app.GetInstance().GetConfig().SiteDirectory
	snapshotFile := filepath.Join(sitePath, "snapshot.json")
	_, err := os.Stat(snapshotFile)
	return err == nil
}

func (c *FreezeCommand) restoreSnapshot() error {
	sitePath := app.GetInstance().GetConfig().SiteDirectory
	snapshotFile := filepath.Join(sitePath, "snapshot.json")

	input, err := os.ReadFile(snapshotFile)
	if err != nil {
		return fmt.Errorf("error reading snapshot file: %v", err)
	}

	resp, err := ImportSnapshot("Freeze", input)
	if err != nil {
		return fmt.Errorf("error importing snapshot: %v", err)
	}

	snapshotId := resp.ImportSnapshot.GetId()

	restoreResp, err := RestoreSnapshot(snapshotId, true)
	if err != nil {
		return fmt.Errorf("error restoring snapshot: %v", err)
	}

	if !restoreResp.ResetFromSnapshotWithId.Success {
		if len(restoreResp.ResetFromSnapshotWithId.Errors) > 0 {
			return fmt.Errorf("failed to restore snapshot: %s", *restoreResp.ResetFromSnapshotWithId.Errors[0])
		}
		return fmt.Errorf("failed to restore snapshot")
	}

	return nil
}

func (c *FreezeCommand) loadExtraImages() error {
	sitePath := app.GetInstance().GetConfig().SiteDirectory
	imagesFile := filepath.Join(sitePath, "extra_images.yaml")

	// Check if file exists
	if _, err := os.Stat(imagesFile); os.IsNotExist(err) {
		return nil // File doesn't exist, which is fine
	}

	// Read and parse the YAML file
	data, err := os.ReadFile(imagesFile)
	if err != nil {
		return fmt.Errorf("failed to read extra images file: %w", err)
	}

	// Unmarshal directly into extraImages slice
	if err := yaml.Unmarshal(data, &c.extraImages); err != nil {
		return fmt.Errorf("failed to parse extra images file: %w", err)
	}

	return nil
}
