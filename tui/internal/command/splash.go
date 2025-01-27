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
package command

import (
	"strconv"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/internal/ui/art"
)

// SplashCommand displays the DeltaFi logo
type SplashCommand struct {
	BaseCommand
	duration time.Duration
	width    int
	height   int
	ready    bool
}

// timeoutMsg is sent when the splash screen should close
type timeoutMsg struct{}

func NewSplashCommand(args []string) *SplashCommand {
	duration := 3 * time.Second // default duration
	if len(args) > 0 {
		if seconds, err := strconv.Atoi(args[0]); err == nil {
			duration = time.Duration(seconds) * time.Second
		}
	}

	return &SplashCommand{
		BaseCommand: NewBaseCommand(),
		duration:    duration,
	}
}

func (c *SplashCommand) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		c.timeout,
	)
}

func (c *SplashCommand) timeout() tea.Msg {
	time.Sleep(c.duration)
	return timeoutMsg{}
}

func (c *SplashCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		c.width = msg.Width
		c.height = msg.Height
		c.ready = true

	case timeoutMsg:
		return c, tea.Quit

	case tea.KeyMsg:
		// Allow quitting before timeout
		return c, tea.Quit
	}

	return c, nil
}

func (c *SplashCommand) View() string {
	if !c.ready {
		return ""
	}

	// Style for the logo and box
	boxStyle := lipgloss.NewStyle().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("#005fff")).
		Padding(1)

	// Center the content in the terminal
	return lipgloss.Place(
		c.width,
		c.height,
		lipgloss.Center,
		lipgloss.Center,
		boxStyle.Render(art.Logo),
	)
}
