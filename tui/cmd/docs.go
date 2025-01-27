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

	"github.com/deltafi/tui/internal/app"
	"github.com/spf13/cobra"

	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/glamour"
	"github.com/charmbracelet/lipgloss"
)

// docsCmd represents the docs command
var docsCmd = &cobra.Command{
	Use:   "docs",
	Short: "DeltaFi documentation browser",
	Long:  `DeltaFi documentation browser`,
	Run: func(cmd *cobra.Command, args []string) {
		runProgram(NewDocsCommand())
	},
}

func init() {
	rootCmd.AddCommand(docsCmd)

	// Here you will define your flags and configuration settings.

	// Cobra supports Persistent Flags which will work for this command
	// and all subcommands, e.g.:
	// docsCmd.PersistentFlags().String("foo", "", "A help for foo")

	// Cobra supports local flags which will only run when this command
	// is called directly, e.g.:
	// docsCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")
}

// Document represents a markdown document
type Document struct {
	Path     string
	Name     string
	Content  string
	IsDir    bool
	Parent   string
	Children []Document
}

// Implement list.Item interface
func (d Document) Title() string       { return d.Name }
func (d Document) Description() string { return "" }
func (d Document) FilterValue() string { return d.Name }

// DocsCommand handles the documentation browser
type DocsCommand struct {
	BaseCommand
	list            list.Model
	viewport        viewport.Model
	docs            []Document
	currentPath     string
	stack           [][]Document // Navigation stack
	currentDoc      *Document
	width           int
	height          int
	ready           bool
	showingDoc      bool
	renderer        *glamour.TermRenderer
	docStyle        lipgloss.Style
	titleStyle      lipgloss.Style
	breadcrumbStyle lipgloss.Style
	err             error
}

type keyMap struct {
	Up    key.Binding
	Down  key.Binding
	Enter key.Binding
	Back  key.Binding
	Quit  key.Binding
	Help  key.Binding
}

var keys = keyMap{
	Up: key.NewBinding(
		key.WithKeys("up", "k"),
		key.WithHelp("↑/k", "up"),
	),
	Down: key.NewBinding(
		key.WithKeys("down", "j"),
		key.WithHelp("↓/j", "down"),
	),
	Enter: key.NewBinding(
		key.WithKeys("enter"),
		key.WithHelp("enter", "select/view"),
	),
	Back: key.NewBinding(
		key.WithKeys("esc", "backspace"),
		key.WithHelp("esc/backspace", "back"),
	),
	Quit: key.NewBinding(
		key.WithKeys("q", "ctrl+c"),
		key.WithHelp("q", "quit"),
	),
	Help: key.NewBinding(
		key.WithKeys("?"),
		key.WithHelp("?", "toggle help"),
	),
}

// Update the NewDocsCommand function:
func NewDocsCommand() *DocsCommand {
	renderer, _ := glamour.NewTermRenderer(
		glamour.WithAutoStyle(),
		glamour.WithWordWrap(0),
	)

	// Create default list with empty items
	delegate := list.NewDefaultDelegate()
	initialList := list.New([]list.Item{}, delegate, 0, 0)
	initialList.SetShowHelp(true)
	initialList.SetFilteringEnabled(false)
	initialList.SetShowStatusBar(false)
	initialList.SetShowPagination(true)
	initialList.DisableQuitKeybindings()
	initialList.Title = "Documentation"
	initialList.AdditionalFullHelpKeys = func() []key.Binding {
		return []key.Binding{
			keys.Back,
			keys.Quit,
		}
	}

	return &DocsCommand{
		BaseCommand: NewBaseCommand(),
		renderer:    renderer,
		list:        initialList,
		docStyle: lipgloss.NewStyle().
			Margin(1, 2),
		titleStyle: lipgloss.NewStyle().
			Foreground(lipgloss.Color("205")).
			Bold(true).
			MarginLeft(2),
		breadcrumbStyle: lipgloss.NewStyle().
			Foreground(lipgloss.Color("241")),
	}
}

// Update the initializeComponents function:
func (c *DocsCommand) initializeComponents(msg tea.WindowSizeMsg) {
	c.width = msg.Width
	c.height = msg.Height

	// Update list dimensions
	c.list.SetWidth(msg.Width)
	c.list.SetHeight(msg.Height - 4)

	// Initialize viewport
	c.viewport = viewport.New(msg.Width, msg.Height-4)
	c.viewport.Style = c.docStyle

	c.ready = true
}

// Update the Update function:
func (c *DocsCommand) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd
	var cmds []tea.Cmd

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		if !c.ready {
			c.initializeComponents(msg)
		}
		c.width = msg.Width
		c.height = msg.Height

		if c.showingDoc {
			c.viewport.Width = msg.Width
			c.viewport.Height = msg.Height - 4
		} else {
			c.list.SetWidth(msg.Width)
			c.list.SetHeight(msg.Height - 4)
		}

	case error:
		c.err = msg
		return c, tea.Quit

	case []Document:
		if !c.ready {
			return c, nil
		}
		items := make([]list.Item, len(msg))
		for i, doc := range msg {
			items[i] = doc
		}
		c.stack = append(c.stack, msg)
		c.list.SetItems(items)

	case tea.KeyMsg:
		if c.err != nil {
			return c, tea.Quit
		}

		// Global quit handling
		if key.Matches(msg, keys.Quit) {
			return c, tea.Quit
		}

		if c.showingDoc {
			switch {
			case key.Matches(msg, keys.Back):
				c.showingDoc = false
				c.currentDoc = nil
				return c, nil
			default:
				c.viewport, cmd = c.viewport.Update(msg)
				return c, cmd
			}
		}

		switch {
		case key.Matches(msg, keys.Enter):
			if c.list.SelectedItem() == nil {
				return c, nil
			}
			i, ok := c.list.SelectedItem().(Document)
			if ok {
				if i.IsDir {
					return c, func() tea.Msg { return i.Children }
				} else {
					c.showingDoc = true
					c.currentDoc = &i
					rendered, _ := c.renderer.Render(i.Content)
					c.viewport.SetContent(rendered)
					return c, nil
				}
			}

		case key.Matches(msg, keys.Back):
			if len(c.stack) > 1 {
				c.stack = c.stack[:len(c.stack)-1]
				docs := c.stack[len(c.stack)-1]
				items := make([]list.Item, len(docs))
				for i, doc := range docs {
					items[i] = doc
				}
				c.list.SetItems(items)
			}
		}

		var listCmd tea.Cmd
		c.list, listCmd = c.list.Update(msg)
		cmds = append(cmds, listCmd)
	}

	if c.showingDoc {
		c.viewport, cmd = c.viewport.Update(msg)
		cmds = append(cmds, cmd)
	}

	return c, tea.Batch(cmds...)
}

func (c *DocsCommand) Init() tea.Cmd {
	fmt.Printf("Initializing...\r\n")
	basePath := filepath.Join(app.GetDistroPath(), "docs")
	fmt.Printf("Working directory: %s\r\n", basePath)
	return tea.Sequence(
		tea.EnterAltScreen,
		c.initDocs,
	)
}

func (c *DocsCommand) initDocs() tea.Msg {
	docs := []Document{}
	basePath := filepath.Join(app.GetDistroPath(), "docs")

	// Print the working directory
	fmt.Printf("Working directory: %s\r\n", basePath)

	// Check if docs directory exists
	if _, err := os.Stat(basePath); os.IsNotExist(err) {
		return fmt.Errorf("documentation directory not found: %s", basePath)
	}

	// Walk the docs directory
	err := filepath.WalkDir(basePath, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		// Skip the root directory itself
		if path == basePath {
			return nil
		}

		relPath, _ := filepath.Rel(basePath, path)
		parent := filepath.Dir(relPath)
		if parent == "." {
			parent = ""
		}

		// Create document
		doc := Document{
			Path:   path,
			Name:   d.Name(),
			IsDir:  d.IsDir(),
			Parent: parent,
		}

		// If it's a markdown file, load its content
		if !d.IsDir() && strings.HasSuffix(strings.ToLower(d.Name()), ".md") {
			content, err := os.ReadFile(path)
			if err == nil {
				doc.Content = string(content)
				doc.Name = strings.TrimSuffix(d.Name(), filepath.Ext(d.Name()))
			}
		}
		// Print doc
		// fmt.Printf("Found doc: %s\n", path)
		// fmt.Printf("%s", doc.Content)

		docs = append(docs, doc)
		return nil
	})

	if err != nil {
		return err
	}

	if len(docs) == 0 {
		return fmt.Errorf("no documentation files found in %s", basePath)
	}

	// Build the document tree
	rootDocs := []Document{}
	for _, doc := range docs {
		if doc.Parent == "" {
			if doc.IsDir {
				doc.Children = getChildren(docs, doc.Name)
			}
			rootDocs = append(rootDocs, doc)
		}
	}

	// Sort documents (directories first, then alphabetically)
	sort.Slice(rootDocs, func(i, j int) bool {
		if rootDocs[i].IsDir != rootDocs[j].IsDir {
			return rootDocs[i].IsDir
		}
		return rootDocs[i].Name < rootDocs[j].Name
	})

	return rootDocs
}

func getChildren(docs []Document, parent string) []Document {
	children := []Document{}
	for _, doc := range docs {
		if filepath.Dir(doc.Path) == filepath.Join("docs", parent) {
			if doc.IsDir {
				doc.Children = getChildren(docs, doc.Name)
			}
			children = append(children, doc)
		}
	}
	sort.Slice(children, func(i, j int) bool {
		if children[i].IsDir != children[j].IsDir {
			return children[i].IsDir
		}
		return children[i].Name < children[j].Name
	})
	return children
}

func (c *DocsCommand) View() string {
	if c.err != nil {
		return c.RenderError()
	}

	if !c.ready {
		return "Initializing..."
	}

	if c.showingDoc && c.currentDoc != nil {
		breadcrumb := c.breadcrumbStyle.Render(c.currentDoc.Path)
		title := c.titleStyle.Render(c.currentDoc.Name)
		return fmt.Sprintf("%s\n%s\n%s", breadcrumb, title, c.viewport.View())
	}

	path := "docs"
	if len(c.stack) > 1 {
		parts := make([]string, len(c.stack))
		for i, docs := range c.stack[:len(c.stack)-1] {
			if len(docs) > 0 {
				parts[i] = docs[0].Parent
			}
		}
		path = filepath.Join(parts...)
	}
	breadcrumb := c.breadcrumbStyle.Render(path)
	return fmt.Sprintf("%s\n%s", breadcrumb, c.list.View())
}

func (c *DocsCommand) RenderError() string {
	return fmt.Sprintf("\n  Error: %v\n\n  Press any key to exit...", c.err)
}
