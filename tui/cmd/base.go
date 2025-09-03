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

	"github.com/Masterminds/semver/v3"
	"github.com/charmbracelet/bubbles/spinner"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/orchestration"
	"github.com/deltafi/tui/internal/ui/styles"
)

// BaseCommand provides common functionality for all commands
type BaseCommand struct {
	spinner      spinner.Model
	err          error
	loading      bool
	quitting     bool
	orchestrator orchestration.Orchestrator
}

func NewBaseCommand() BaseCommand {
	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = lipgloss.NewStyle().Foreground(lipgloss.Color("205"))

	return BaseCommand{
		spinner:      s,
		loading:      true,
		orchestrator: app.GetOrchestrator(),
	}
}

// Init implements tea.Model
func (b BaseCommand) Init() tea.Cmd {
	return b.spinner.Tick
}

// Update implements tea.Model
func (b BaseCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c", "esc":
			b.quitting = true
			return b, tea.Quit
		}
	case spinner.TickMsg:
		var cmd tea.Cmd
		b.spinner, cmd = b.spinner.Update(msg)
		return b, cmd
	}
	return b, nil
}

// View implements tea.Model
func (b BaseCommand) View() string {
	if b.quitting {
		return ""
	}

	// if b.loading {
	// 	return styles.DefaultStyles().Loading.Render(b.spinner.View() + " Loading...")
	// }

	// if b.err != nil {
	// 	return styles.DefaultStyles().Error.Render(b.err.Error())
	// }

	return ""
}

// Helper methods for embedding commands

func (b *BaseCommand) HandleKeyMsg(msg tea.KeyMsg) (bool, tea.Cmd) {
	switch msg.String() {
	case "q", "ctrl+c", "esc":
		b.quitting = true
		return true, tea.Quit
	}
	return false, nil
}

func (b *BaseCommand) UpdateSpinner(msg spinner.TickMsg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd
	b.spinner, cmd = b.spinner.Update(msg)
	return b, cmd
}

func (b *BaseCommand) RenderError() string {
	// if b.err != nil {
	// 	return styles.DefaultStyles().Error.Render(b.err.Error())
	// }
	return ""
}

func (b *BaseCommand) RenderLoading(message string) string {
	// if b.loading {
	// 	return styles.DefaultStyles().Loading.Render(b.spinner.View() + " " + message)
	// }
	return ""
}

// Getters and setters

func (b *BaseCommand) IsLoading() bool {
	return b.loading
}

func (b *BaseCommand) SetLoading(loading bool) {
	b.loading = loading
}

func (b *BaseCommand) IsQuitting() bool {
	return b.quitting
}

func (b *BaseCommand) SetQuitting(quitting bool) {
	b.quitting = quitting
}

func (b *BaseCommand) GetError() error {
	return b.err
}

func (b *BaseCommand) SetError(err error) {
	b.err = err
}

func (b *BaseCommand) GetSpinner() spinner.Model {
	return b.spinner
}

func RequireRunningDeltaFi() {
	if !app.IsRunning() {
		fmt.Println(newError("\nDeltaFi is not running",
			fmt.Sprintf(`
To configure a new DeltaFi instance, use the init wizard:  %s

To start a DeltaFi with your current configuration:        %s
		`, styles.AccentStyle.Bold(true).Render("deltafi config"), styles.AccentStyle.Bold(true).Render("deltafi up"))))
		os.Exit(1)
	}
}

func IsDeltafiRunning() bool {
	return app.IsRunning()
}

func WaitForDeltaFi() {
	app.WaitForRunning()
}

func GetRunningVersion() *semver.Version {
	version := &semver.Version{}

	liveVersion, err := graphql.Version()
	if err == nil {
		version, _ = semver.NewVersion(liveVersion.Version)
	} else {
		version = app.GetInstance().GetConfig().GetCoreVersion()
	}
	return version
}

func ShellExec(executable string, env []string, args []string) error {
	c := *exec.Command(executable, args...)
	c.Env = env
	c.Stdin = os.Stdin
	c.Stdout = os.Stdout
	c.Stderr = os.Stderr
	return c.Run()
}
