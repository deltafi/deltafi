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
	"os/exec"
	"path/filepath"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

type wizardStep int

const (
	welcomeStep wizardStep = iota
	deploymentStep
	orchestrationStep
	coreRepoStep
	cloneConfirmStep
	cloningStep
	confirmationStep
	completeStep
)

type InitCommand struct {
	BaseCommand
	step               wizardStep
	config             app.Config
	width              int
	height             int
	ready              bool
	help               help.Model
	keys               initKeyMap
	selectedOption     int
	deploymentModes    []string
	orchestrationModes []string
	repoInput          textinput.Model
	cloneError         error
}

type initKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Select key.Binding
	Back   key.Binding
	Help   key.Binding
	Quit   key.Binding
}

func (k initKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Select, k.Back}
}

func (k initKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Select},
		{k.Back, k.Help, k.Quit},
	}
}

var initKeys = initKeyMap{
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
		key.WithKeys("esc", "backspace"),
		key.WithHelp("esc", "back"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
	Quit: key.NewBinding(
		key.WithKeys("q", "ctrl+c"),
		key.WithHelp("q", "quit"),
	),
}

var initCmd = &cobra.Command{
	Use:   "init",
	Short: "Initialize DeltaFi system",
	Long:  `Interactive wizard to initialize DeltaFi system`,
	Run: func(cmd *cobra.Command, args []string) {
		runProgram(NewInitCommand())
	},
}

func init() {
	rootCmd.AddCommand(initCmd)
}

func NewInitCommand() *InitCommand {
	help := help.New()
	help.ShowAll = false

	ti := textinput.New()
	ti.Placeholder = "Git repository URL"
	ti.Width = 50

	return &InitCommand{
		BaseCommand:    NewBaseCommand(),
		step:           welcomeStep,
		config:         app.DefaultConfig(),
		help:           help,
		keys:           initKeys,
		selectedOption: 0,
		deploymentModes: []string{
			"Deployment",
			"CoreDevelopment",
			"PluginDevelopment",
		},
		orchestrationModes: []string{
			"Compose",
			"Kubernetes",
			"KinD",
		},
		repoInput: ti,
	}
}

func (c *InitCommand) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		c.spinner.Tick,
		textinput.Blink,
	)
}

type cloneFinishedMsg struct{ err error }

func (c *InitCommand) cloneRepo() tea.Cmd {
	return func() tea.Msg {
		repoPath := filepath.Join(app.TuiPath(), "repos", "deltafi")
		cmd := exec.Command("git", "clone", c.config.Development.CoreRepo, repoPath)
		err := cmd.Run()
		return cloneFinishedMsg{err}
	}
}

func (c *InitCommand) needsCoreSetup() bool {
	if c.config.DeploymentMode != app.CoreDevelopment {
		return false
	}

	repoPath := filepath.Join(app.TuiPath(), "repos", "deltafi")
	if _, err := os.Stat(repoPath); err == nil {
		return false
	}

	return true
}

func (c *InitCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height
		c.help.Width = msg.Width
		c.ready = true

	case cloneFinishedMsg:
		if msg.err != nil {
			c.cloneError = msg.err
		}
		c.step = confirmationStep

	case tea.KeyMsg:
		if key.Matches(msg, c.keys.Quit) {
			return c, tea.Quit
		}

		switch c.step {
		case welcomeStep:
			if key.Matches(msg, c.keys.Select) {
				c.step = deploymentStep
			}

		case deploymentStep:
			switch {
			case key.Matches(msg, c.keys.Up):
				if c.selectedOption > 0 {
					c.selectedOption--
				}
			case key.Matches(msg, c.keys.Down):
				if c.selectedOption < len(c.deploymentModes)-1 {
					c.selectedOption++
				}
			case key.Matches(msg, c.keys.Select):
				c.config.DeploymentMode = app.DeploymentMode(c.selectedOption)
				c.selectedOption = 0
				c.step = orchestrationStep
			case key.Matches(msg, c.keys.Back):
				c.step = welcomeStep
			}

		case orchestrationStep:
			switch {
			case key.Matches(msg, c.keys.Up):
				if c.selectedOption > 0 {
					c.selectedOption--
				}
			case key.Matches(msg, c.keys.Down):
				if c.selectedOption < len(c.orchestrationModes)-1 {
					c.selectedOption++
				}
			case key.Matches(msg, c.keys.Select):
				c.config.OrchestrationMode = orchestration.OrchestrationMode(c.selectedOption)
				if c.needsCoreSetup() {
					if c.config.Development.CoreRepo == "" {
						c.config.Development.CoreRepo = "git@gitlab.com:systolic/deltafi/deltafi.git"
					}
					c.repoInput.SetValue(c.config.Development.CoreRepo)
					c.repoInput.Focus()
					c.step = coreRepoStep
				} else {
					c.step = confirmationStep
				}
			case key.Matches(msg, c.keys.Back):
				c.selectedOption = 0
				c.step = deploymentStep
			}

		case coreRepoStep:
			switch {
			case key.Matches(msg, c.keys.Back):
				c.step = orchestrationStep
				c.repoInput.Blur()
			case key.Matches(msg, c.keys.Select):
				c.config.Development.CoreRepo = c.repoInput.Value()
				c.repoInput.Blur()
				c.step = cloneConfirmStep
			default:
				c.repoInput, cmd = c.repoInput.Update(msg)
				return c, cmd
			}

		case cloneConfirmStep:
			switch {
			case key.Matches(msg, c.keys.Up), key.Matches(msg, c.keys.Down):
				c.selectedOption = 1 - c.selectedOption // Toggle between 0 and 1
			case key.Matches(msg, c.keys.Select):
				if c.selectedOption == 0 { // Yes
					c.step = cloningStep
					return c, c.cloneRepo()
				} else {
					c.step = confirmationStep
				}
			case key.Matches(msg, c.keys.Back):
				c.step = coreRepoStep
				c.repoInput.Focus()
			}

		case confirmationStep:
			switch {
			case key.Matches(msg, c.keys.Select):
				if err := c.config.Save(); err != nil {
					c.err = err
					return c, nil
				}
				c.step = completeStep
			case key.Matches(msg, c.keys.Back):
				if c.needsCoreSetup() {
					c.step = cloneConfirmStep
				} else {
					c.step = orchestrationStep
				}
			}

		case completeStep:
			if key.Matches(msg, c.keys.Select) {
				return c, tea.Quit
			}
		}

		if key.Matches(msg, c.keys.Help) {
			c.help.ShowAll = !c.help.ShowAll
		}
	}

	return c, nil
}

func (c *InitCommand) View() string {
	if !c.ready {
		return "Initializing..."
	}

	if c.err != nil {
		return c.RenderError()
	}

	mainStyle := lipgloss.NewStyle().
		Width(c.width).
		Height(c.height).
		Align(lipgloss.Center)

	contentStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(styles.Crust).
		Padding(1).
		Width(c.width / 2)

	headerStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Bold(true).
		Padding(1)

	var content string
	switch c.step {
	case welcomeStep:
		content = c.renderWelcome()
	case deploymentStep:
		content = c.renderDeploymentMode()
	case orchestrationStep:
		content = c.renderOrchestrationMode()
	case coreRepoStep:
		content = c.renderCoreRepo()
	case cloneConfirmStep:
		content = c.renderCloneConfirm()
	case cloningStep:
		content = c.renderCloning()
	case confirmationStep:
		content = c.renderConfirmation()
	case completeStep:
		content = c.renderComplete()
	}

	header := headerStyle.Render("DeltaFi System Initialization")
	box := contentStyle.Render(content)

	help := c.help.View(c.keys)
	fullView := lipgloss.JoinVertical(lipgloss.Center,
		header,
		box,
		help,
	)

	return mainStyle.Render(fullView)
}

func (c *InitCommand) renderWelcome() string {
	welcomeStyle := lipgloss.NewStyle().
		Align(lipgloss.Center).
		Padding(1)

	text := "Welcome to DeltaFi!\n\n" +
		"This wizard will help you configure your DeltaFi installation.\n\n" +
		"Press ENTER to continue..."

	return welcomeStyle.Render(text)
}

func (c *InitCommand) renderDeploymentMode() string {
	content := "Select Deployment Mode:\n\n"

	for i, mode := range c.deploymentModes {
		item := "  " + mode
		if i == c.selectedOption {
			item = "> " + styles.SelectedMenuItemStyle.Render(mode)
		}
		content += item + "\n"
	}

	return content
}

func (c *InitCommand) renderOrchestrationMode() string {
	content := "Select Orchestration Mode:\n\n"

	for i, mode := range c.orchestrationModes {
		item := "  " + mode
		if i == c.selectedOption {
			item = "> " + styles.SelectedMenuItemStyle.Render(mode)
		}
		content += item + "\n"
	}

	return content
}

func (c *InitCommand) renderCoreRepo() string {
	return fmt.Sprintf(
		"Configure Core Repository URL:\n\n%s\n\nPress ENTER to continue...",
		c.repoInput.View(),
	)
}

func (c *InitCommand) renderCloneConfirm() string {
	content := "Would you like to clone the core repository?\n\n"

	yes := "  Yes"
	no := "  No"

	if c.selectedOption == 0 {
		yes = "> " + styles.SelectedMenuItemStyle.Render("Yes")
	} else {
		no = "> " + styles.SelectedMenuItemStyle.Render("No")
	}

	return content + yes + "\n" + no
}

func (c *InitCommand) renderCloning() string {
	return c.spinner.View() + " Cloning repository..."
}

func (c *InitCommand) renderConfirmation() string {
	content := "Configuration Summary:\n\n" +
		"Deployment Mode: " + c.config.DeploymentMode.String() + "\n" +
		"Orchestration Mode: " + c.config.OrchestrationMode.String()

	if c.config.DeploymentMode == app.CoreDevelopment {
		content += "\nCore Repository: " + c.config.Development.CoreRepo
		if c.cloneError != nil {
			content += "\n\n" + styles.ErrorStyle.Render("Failed to clone repository: "+c.cloneError.Error())
		}
	}

	content += "\n\nPress ENTER to save configuration..."

	return content
}

func (c *InitCommand) renderComplete() string {
	content := "Configuration saved successfully!\n\n" +
		"Press ENTER to exit..."

	return content
}
