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
	"strings"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/types"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(configCmd)
}

var configCmd = &cobra.Command{
	Use:     "config",
	Short:   "Configure DeltaFi system mode",
	Long:    `Interactive wizard to initialize DeltaFi system`,
	GroupID: "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {
		return ExecuteConfigWizard()
	},
}

func ExecuteConfigWizard() error {
	wizard := NewConfigWizard()
	success := runProgram(wizard)

	if !success {
		return wizard.err
	}

	return app.GetOrchestrator().Up([]string{})
}

type configWizardStep int

const (
	welcomeStep configWizardStep = iota
	deploymentStep
	orchestrationStep
	coreRepoStep
	cloneConfirmStep
	cloningStep
	confirmationStep
	completeStep
)

type ConfigWizard struct {
	BaseCommand
	initialStep        configWizardStep
	step               configWizardStep
	config             app.Config
	width              int
	height             int
	ready              bool
	help               help.Model
	keys               configWizardKeyMap
	selectedOption     int
	deploymentModes    []string
	orchestrationModes []string
	repoInput          textinput.Model
	cloneError         error
	err                error
}

type configWizardKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Select key.Binding
	Back   key.Binding
	Help   key.Binding
	Quit   key.Binding
}

func (k configWizardKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Up, k.Down, k.Select, k.Back}
}

func (k configWizardKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Select},
		{k.Back, k.Help, k.Quit},
	}
}

var configWizardKeys = configWizardKeyMap{
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
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
	Quit: key.NewBinding(
		key.WithKeys("ctrl+c"),
		key.WithHelp("^c", "quit"),
	),
}

func NewConfigWizard() *ConfigWizard {
	help := help.New()
	help.ShowAll = false

	ti := textinput.New()
	ti.Placeholder = "Git repository URL"
	ti.Width = 50

	config := app.LoadConfigOrDefault()
	initialStep := welcomeStep
	if app.ConfigExists() {
		initialStep = orchestrationStep
	}

	return &ConfigWizard{
		BaseCommand:    NewBaseCommand(),
		initialStep:    initialStep,
		step:           initialStep,
		config:         config,
		help:           help,
		keys:           configWizardKeys,
		selectedOption: 0,
		deploymentModes: []string{
			"Deployment",
			"PluginDevelopment",
			"CoreDevelopment",
		},
		orchestrationModes: []string{
			"Compose",
			"Kubernetes",
			"KinD",
		},
		repoInput: ti,
	}
}

func (c *ConfigWizard) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		c.spinner.Tick,
		textinput.Blink,
	)
}

type cloneFinishedMsg struct{ err error }

func (c *ConfigWizard) cloneRepo() tea.Cmd {
	return func() tea.Msg {
		coreRepo := filepath.Join(c.config.Development.RepoPath, "deltafi")
		cmd := exec.Command("git", "clone", c.config.Development.CoreRepo, coreRepo)
		err := cmd.Run()
		return cloneFinishedMsg{err}
	}
}

func (c *ConfigWizard) needsCoreSetup() bool {
	if c.config.DeploymentMode != types.CoreDevelopment {
		return false
	}

	coreRepo := filepath.Join(c.config.Development.RepoPath, "deltafi")
	if _, err := os.Stat(coreRepo); err == nil {
		return false
	}

	return true
}

func (c *ConfigWizard) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height
		c.help.Width = msg.Width
		c.ready = true

	case spinner.TickMsg:
		var cmd tea.Cmd
		c.spinner, cmd = c.spinner.Update(msg)
		return c, cmd

	case cloneFinishedMsg:
		if msg.err != nil {
			c.cloneError = msg.err
		}
		c.step = confirmationStep

	case tea.KeyMsg:
		if key.Matches(msg, c.keys.Quit) {
			c.err = fmt.Errorf("initialization cancelled")
			return c, tea.Quit
		}

		switch c.step {
		case welcomeStep:
			if key.Matches(msg, c.keys.Select) {
				c.selectedOption = int(c.config.OrchestrationMode)
				c.step = orchestrationStep
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
				c.selectedOption = int(c.config.DeploymentMode)
				c.step = deploymentStep
				if c.config.OrchestrationMode == orchestration.Kubernetes {
					// Kubernetes skips the deployment step
					c.config.DeploymentMode = types.Deployment
					c.step = confirmationStep
				}
			case key.Matches(msg, c.keys.Back):
				if c.initialStep != orchestrationStep {
					c.selectedOption = 0
					c.step = welcomeStep
				}
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
				c.config.DeploymentMode = types.DeploymentMode(c.selectedOption)
				c.selectedOption = 0
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
				c.selectedOption = int(c.config.OrchestrationMode)
				c.step = orchestrationStep
			}

		case coreRepoStep:
			switch {
			case key.Matches(msg, c.keys.Back):
				c.selectedOption = int(c.config.DeploymentMode)
				c.step = deploymentStep
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

				app.ReloadInstance() // Rebuild app config based on config changes

				// Configure shell if possible
				shell := app.DetectShell()
				if app.IsKnownShell(shell) {
					if err := app.ConfigureShell(shell, app.TuiPath()); err != nil {
						c.err = err
						return c, nil
					}
				}

				c.step = completeStep
			case key.Matches(msg, c.keys.Back):
				if c.needsCoreSetup() {
					c.step = cloneConfirmStep
				} else {
					if c.config.OrchestrationMode == orchestration.Kubernetes {
						c.selectedOption = int(c.config.OrchestrationMode)
						c.step = orchestrationStep
					} else {
						c.selectedOption = int(c.config.DeploymentMode)
						c.step = deploymentStep
					}
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

func (c *ConfigWizard) View() string {
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

func (c *ConfigWizard) renderWelcome() string {
	welcomeStyle := lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1)

	text := "Welcome to DeltaFi!\n\n" +
		"This wizard will help you configure your DeltaFi installation.\n\n" +
		"Press ENTER to continue..."

	return welcomeStyle.Render(text)
}

func (c *ConfigWizard) renderConfirmation() string {
	content := "Configuration Summary:\n\n" +
		"Deployment Mode:     " + c.config.DeploymentMode.String() + "\n" +
		"Orchestration Mode:  " + c.config.OrchestrationMode.String() + "\n" +
		"Shell hooks:         " + string(app.DetectShell())

	if c.config.DeploymentMode == types.CoreDevelopment {
		content += "\nCore Repository: " + c.config.Development.CoreRepo
		if c.cloneError != nil {
			content += "\n\n" + styles.ErrorStyle.Render("Failed to clone repository: "+c.cloneError.Error())
		}
	}

	content += "\n\nPress ENTER to save configuration..."

	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render(content)
}

func (c *ConfigWizard) renderTwoPane(totalWidth, leftWidth int, left, right string) string {
	rightWidth := totalWidth - leftWidth
	horizontal := true
	if rightWidth < 30 {
		horizontal = false
		rightWidth = totalWidth
	}
	if rightWidth > 80 {
		rightWidth = 80
	}
	leftStyle := lipgloss.NewStyle().Width(leftWidth).Margin(0).PaddingRight(1)
	rightStyle := lipgloss.NewStyle().Width(rightWidth).
		Border(lipgloss.RoundedBorder()).
		BorderForeground(styles.Surface2).
		Margin(0).
		Padding(0, 1)

	if horizontal {
		return lipgloss.JoinHorizontal(lipgloss.Top, leftStyle.Render(left), rightStyle.MarginTop(1).Render(right))
	} else {
		return lipgloss.JoinVertical(lipgloss.Left, rightStyle.Render(right), "", leftStyle.Render(left))
	}
}

func (c *ConfigWizard) renderMenu(items []string, header string) string {
	var menu []string
	menu = append(menu, styles.SubheaderStyle.PaddingBottom(1).Render(header))
	for i, item := range items {
		if i == c.selectedOption {
			item = styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render(item)
		} else {
			item = styles.MenuItemStyle.PaddingLeft(3).Render(item)
		}
		menu = append(menu, item)
	}

	retval := strings.Join(menu, "\n")
	retval = lipgloss.NewStyle().MarginLeft(1).Render(retval)

	return retval
}

// Update the deployment mode selection to use the new layout
func (c *ConfigWizard) renderDeploymentMode() string {
	menu := c.renderMenu(c.deploymentModes, "Select Operational Mode")
	description := types.DeploymentMode(c.selectedOption).Description()
	return c.renderTwoPane(c.width, 40, menu, description)
}

// Update the core repo step to use the new layout
func (c *ConfigWizard) renderCoreRepo() string {
	inputStyle := lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1)

	return inputStyle.Render(
		"Configure Core Repository URL:\n\n" +
			c.repoInput.View() + "\n\n" +
			"Press ENTER to continue",
	)
}

// Update the cloning step to use the new layout
func (c *ConfigWizard) renderCloning() string {
	return lipgloss.NewStyle().
		PaddingLeft(1).
		Render(c.spinner.View() + " Cloning repository...")
}

// Update the complete step to use the new layout
func (c *ConfigWizard) renderComplete() string {
	return lipgloss.NewStyle().
		PaddingLeft(1).
		PaddingRight(1).
		Render("Configuration saved successfully!\n\n" +
			"Press ENTER to exit...")
}

func (c *ConfigWizard) renderOrchestrationMode() string {
	menu := c.renderMenu(c.orchestrationModes, "Select Orchestration Mode")
	description := "Orchestration mode controls how containers for DeltaFi services and plugins are managed.\n\n" + orchestration.OrchestrationMode(c.selectedOption).Description()
	return c.renderTwoPane(c.width-8, 40, menu, description)

}

func (c *ConfigWizard) renderCloneConfirm() string {
	return c.renderMenu([]string{"Yes", "No"}, "Would you like to clone the core repository?")
}

func (c *ConfigWizard) GetError() error {
	return c.err
}
