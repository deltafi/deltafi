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

var (
	composeFlag           bool
	kindFlag              bool
	kubernetesFlag        bool
	noWizard              bool
	deploymentFlag        bool
	pluginDevelopmentFlag bool
	coreDevelopmentFlag   bool
	forceFlag             bool
	jsonFlag              bool
	noStartFlag           bool
)

func init() {
	rootCmd.AddCommand(configCmd)

	// Add orchestration mode flags to config command only
	configCmd.Flags().BoolVar(&composeFlag, "compose", false, "Set orchestration mode to Compose")
	configCmd.Flags().BoolVar(&kindFlag, "kind", false, "Set orchestration mode to KinD")
	configCmd.Flags().BoolVar(&kubernetesFlag, "kubernetes", false, "Set orchestration mode to Kubernetes")

	// Add deployment mode flags to config command only
	configCmd.Flags().BoolVar(&deploymentFlag, "deployment", false, "Set deployment mode to Deployment")
	configCmd.Flags().BoolVar(&pluginDevelopmentFlag, "plugin-development", false, "Set deployment mode to PluginDevelopment")
	configCmd.Flags().BoolVar(&coreDevelopmentFlag, "core-development", false, "Set deployment mode to CoreDevelopment")
	configCmd.Flags().BoolVar(&forceFlag, "force", false, "Force configuration changes without confirmation")

	configCmd.Flags().BoolVar(&jsonFlag, "json", false, "Output configuration in JSON format and exit")
	configCmd.Flags().BoolVar(&noStartFlag, "no-start", false, "Do not automatically start DeltaFi if it is not running")

	noWizard = false
}

var configCmd = &cobra.Command{
	Use:     "config",
	Short:   "Configure DeltaFi system mode",
	Long:    `Interactive wizard to initialize DeltaFi system`,
	GroupID: "orchestration",
	RunE: func(cmd *cobra.Command, args []string) error {

		if jsonFlag {
			config := app.LoadConfigOrDefault()
			json, err := json.MarshalIndent(config, "", "  ")
			if err != nil {
				return fmt.Errorf("Error rendering configuration: %w", err)
			}
			fmt.Println(styles.ColorizeJSON(string(json)))
			return nil
		}

		configExists := app.ConfigExists()
		config := app.LoadConfigOrDefault()
		currentOrchestrationMode := config.OrchestrationMode
		currentDeploymentMode := config.DeploymentMode

		// Handle orchestration and deployment mode flags
		if composeFlag || kindFlag || kubernetesFlag || deploymentFlag || pluginDevelopmentFlag || coreDevelopmentFlag {
			noWizard = true

			if composeFlag {
				config.OrchestrationMode = orchestration.Compose
			} else if kindFlag {
				config.OrchestrationMode = orchestration.Kind
			} else if kubernetesFlag {
				config.OrchestrationMode = orchestration.Kubernetes
			}
			if deploymentFlag {
				config.DeploymentMode = types.Deployment
			} else if pluginDevelopmentFlag {
				config.DeploymentMode = types.PluginDevelopment
			} else if coreDevelopmentFlag {
				config.DeploymentMode = types.CoreDevelopment
			}
		} else {
			var err error
			config, err = ExecuteConfigWizard()
			if err != nil {
				return err
			}
		}

		orchestrationModeChanged := currentOrchestrationMode != config.OrchestrationMode
		deploymentModeChanged := currentDeploymentMode != config.DeploymentMode
		configurationChanged := orchestrationModeChanged || deploymentModeChanged

		if orchestrationModeChanged && configExists {
			fmt.Printf("Orchestration mode will be set to %s\n", styles.WarningStyle.Render(config.OrchestrationMode.String()))
			goForIt := true
			if !forceFlag {
				fmt.Println()
				if app.IsRunning() {
					goForIt = ConfirmPrompt(styles.WarningStyle.Render("DeltaFi is running. Would you like to destroy your current cluster?"))
				} else {
					goForIt = ConfirmPrompt(styles.WarningStyle.Render("Would you like to clean up persistent storage?"))
				}
			}
			if goForIt {
				down(true)
			}
		}

		err := config.Save()
		if err != nil {
			return fmt.Errorf("Failed to save orchestration/deployment mode: %w", err)
		}

		// Configure shell if possible
		shell := app.DetectShell()
		if app.IsKnownShell(shell) {
			if err := app.ConfigureShell(shell, app.TuiPath()); err != nil {
				fmt.Printf(styles.WarningStyle.Render("Failed to configure shell: %s\n"), err)
			}
		}

		if configurationChanged {
			app.ReloadInstance()
		}

		fmt.Printf("\nOrchestration mode: %s\n", config.OrchestrationMode.String())
		fmt.Printf("Deployment mode:    %s\n", config.DeploymentMode.String())

		if configurationChanged || !configExists || !app.IsRunning() {
			goForIt := true

			if !app.IsRunning() && noStartFlag {
				goForIt = false
			}

			if !forceFlag && goForIt {
				fmt.Println()
				if app.IsRunning() {
					goForIt = ConfirmPrompt("DeltaFi configuration has changed. Do you want to update now?")
				} else if configExists {
					goForIt = ConfirmPrompt("DeltaFi configuration has changed. Would you like to start DeltaFi?")
				} else {
					goForIt = ConfirmPrompt(styles.SuccessStyle.Render("Would you like to start DeltaFi?"))
				}
			}
			if goForIt {
				return Up(true)
			}
		}

		return nil
	},
}

func ExecuteConfigWizard() (app.Config, error) {
	wizard := NewConfigWizard()
	success := runProgram(wizard)

	if !success {
		return app.Config{}, wizard.err
	}

	return wizard.config, nil
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
	disabledOptions    map[int]bool
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

	// Check availability of orchestration tools and disable options accordingly
	disabledOptions := make(map[int]bool)
	if !isDockerAvailable() {
		disabledOptions[0] = true // Disable Compose option (index 0)
	}
	if !isKubernetesClusterRunning() {
		disabledOptions[1] = true // Disable Kubernetes option (index 1)
	}
	if !isKindAvailable() {
		disabledOptions[2] = true // Disable Kind option (index 2)
	}

	return &ConfigWizard{
		BaseCommand:    NewBaseCommand(),
		initialStep:    initialStep,
		step:           initialStep,
		config:         config,
		help:           help,
		keys:           configWizardKeys,
		selectedOption: 0, // Will be set properly when entering orchestration step
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
		disabledOptions: disabledOptions,
		repoInput:       ti,
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

		// Initialize selectedOption if starting at orchestration step
		if c.step == orchestrationStep {
			c.selectedOption = int(c.config.OrchestrationMode)
			// If the selected option is disabled, find the first enabled option
			if c.disabledOptions[c.selectedOption] {
				for i := 0; i < len(c.orchestrationModes); i++ {
					if !c.disabledOptions[i] {
						c.selectedOption = i
						break
					}
				}
			}
		}

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
				// Find the first enabled orchestration mode
				validOption := 0
				for i := 0; i < len(c.orchestrationModes); i++ {
					if !c.disabledOptions[i] {
						validOption = i
						break
					}
				}
				c.selectedOption = validOption
				c.step = orchestrationStep
			}

		case orchestrationStep:
			switch {
			case key.Matches(msg, c.keys.Up):
				// Find the previous enabled option
				for i := c.selectedOption - 1; i >= 0; i-- {
					if !c.disabledOptions[i] {
						c.selectedOption = i
						break
					}
				}
			case key.Matches(msg, c.keys.Down):
				// Find the next enabled option
				for i := c.selectedOption + 1; i < len(c.orchestrationModes); i++ {
					if !c.disabledOptions[i] {
						c.selectedOption = i
						break
					}
				}
			case key.Matches(msg, c.keys.Select):
				// Only allow selection if the current option is not disabled
				if !c.disabledOptions[c.selectedOption] {
					c.config.OrchestrationMode = orchestration.OrchestrationMode(c.selectedOption)
					c.selectedOption = int(c.config.DeploymentMode)
					c.step = deploymentStep
					if c.config.OrchestrationMode == orchestration.Kubernetes {
						// Kubernetes skips the deployment step
						c.config.DeploymentMode = types.Deployment
						c.step = confirmationStep
					}
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
				// Find the first enabled orchestration mode when going back
				validOption := 0
				for i := 0; i < len(c.orchestrationModes); i++ {
					if !c.disabledOptions[i] {
						validOption = i
						break
					}
				}
				c.selectedOption = validOption
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
				return c, tea.Quit
			case key.Matches(msg, c.keys.Back):
				if c.needsCoreSetup() {
					c.step = cloneConfirmStep
				} else {
					if c.config.OrchestrationMode == orchestration.Kubernetes {
						// Find the first enabled orchestration mode when going back
						validOption := 0
						for i := 0; i < len(c.orchestrationModes); i++ {
							if !c.disabledOptions[i] {
								validOption = i
								break
							}
						}
						c.selectedOption = validOption
						c.step = orchestrationStep
					} else {
						c.selectedOption = int(c.config.DeploymentMode)
						c.step = deploymentStep
					}
				}
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
	rightStyle := lipgloss.NewStyle().Width(rightWidth).Border(lipgloss.RoundedBorder()).
		BorderForeground(styles.Surface2).
		Margin(0).
		Padding(0, 1)

	if horizontal {
		return lipgloss.JoinHorizontal(lipgloss.Top, leftStyle.Render(left), rightStyle.MarginTop(1).Width(rightWidth).Render(right))
	} else {
		return lipgloss.JoinVertical(lipgloss.Left, rightStyle.Render(right), "", leftStyle.Render(left))
	}
}

func (c *ConfigWizard) getDisabledReason(index int) string {
	switch index {
	case 0: // Compose
		return " (Docker not available)"
	case 1: // Kubernetes
		return " (No cluster)"
	case 2: // Kind
		return " (Kind not available)"
	default:
		return " (not available)"
	}
}

func (c *ConfigWizard) renderMenu(items []string, header string, disabledOptions map[int]bool) string {
	var menu []string
	menu = append(menu, styles.SubheaderStyle.PaddingBottom(1).Render(header))
	for i, item := range items {
		if i == c.selectedOption {
			if disabledOptions[i] {
				reason := c.getDisabledReason(i)
				item = styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.DisabledMenuItemStyle.Padding(0, 1).Render(item+reason)
			} else {
				item = styles.MenuMarkerStyle.PaddingRight(1).Render("▶") + styles.SelectedMenuItemStyle.Padding(0, 1).Render(item)
			}
		} else {
			if disabledOptions[i] {
				reason := c.getDisabledReason(i)
				item = styles.DisabledMenuItemStyle.PaddingLeft(3).Render(item + reason)
			} else {
				item = styles.MenuItemStyle.PaddingLeft(3).Render(item)
			}
		}
		menu = append(menu, item)
	}

	retval := strings.Join(menu, "\n")
	retval = lipgloss.NewStyle().MarginLeft(1).Render(retval)

	return retval
}

// Update the deployment mode selection to use the new layout
func (c *ConfigWizard) renderDeploymentMode() string {
	menu := c.renderMenu(c.deploymentModes, "Select Operational Mode", c.disabledOptions)
	description := types.DeploymentMode(c.selectedOption).Description()
	return c.renderTwoPane(c.width-8, 40, menu, description)
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

func (c *ConfigWizard) renderOrchestrationMode() string {
	menu := c.renderMenu(c.orchestrationModes, "Select Orchestration Mode", c.disabledOptions)
	description := "Orchestration mode controls how containers for DeltaFi services and plugins are managed.\n\n" + orchestration.OrchestrationMode(c.selectedOption).Description()
	return c.renderTwoPane(c.width-8, 40, menu, description)

}

func (c *ConfigWizard) renderCloneConfirm() string {
	return c.renderMenu([]string{"Yes", "No"}, "Would you like to clone the core repository?", c.disabledOptions)
}

func (c *ConfigWizard) GetError() error {
	return c.err
}
