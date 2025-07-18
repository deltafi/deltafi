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
	"sort"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/list"
	"github.com/charmbracelet/bubbles/textinput"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/ui/styles"
)

// Property represents a single property with all its metadata
type Property struct {
	Key          string
	Value        string
	DefaultValue string
	Description  string
	Source       graphql.PropertySource
	DataType     graphql.DataType
	SetName      string
}

// PropertyItem implements list.Item interface
type PropertyItem struct {
	Property Property
}

func (i PropertyItem) Title() string {
	key := i.Property.Key
	if i.Property.Source == graphql.PropertySourceCustom {
		key = key + styles.WarningStyle.Render(" ★ ")
	}
	return key
}

func (i PropertyItem) Description() string {
	value := i.Property.Value
	if value == "" {
		value = i.Property.DefaultValue
	}

	// Truncate long values
	if len(value) > 50 {
		value = value[:47] + "..."
	}

	// Format based on data type
	var formattedValue string
	switch i.Property.DataType {
	case graphql.DataTypeBoolean:
		if value == "true" {
			formattedValue = styles.SuccessStyle.Render("✓ ") + "true"
		} else if value == "false" {
			formattedValue = styles.ErrorStyle.Render("✗ ") + "false"
		} else {
			formattedValue = value
		}
	case graphql.DataTypeNumber:
		formattedValue = styles.ItalicStyle.Render(value)
	default:
		formattedValue = value
	}

	return formattedValue
}

func (i PropertyItem) FilterValue() string {
	return i.Property.Key + " " + i.Property.Description
}

// PropertiesViewer is the main TUI model for the properties viewer
type PropertiesViewer struct {
	BaseCommand
	list           list.Model
	viewport       viewport.Model
	textInput      textinput.Model
	help           help.Model
	keys           propertiesKeyMap
	properties     []Property
	selectedProp   *Property
	saving         bool
	lastSavedValue string // Store the last saved value for verification
	width          int
	height         int
	ready          bool
	err            error
	listWidth      int // Fixed width for the property list
}

type propertiesKeyMap struct {
	Up     key.Binding
	Down   key.Binding
	Select key.Binding
	Edit   key.Binding
	Cancel key.Binding
	Search key.Binding
	Help   key.Binding
	Quit   key.Binding
	Toggle key.Binding
}

func (k propertiesKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{k.Help, k.Quit}
}

func (k propertiesKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{k.Up, k.Down, k.Edit},
		{k.Toggle, k.Cancel, k.Search},
		{k.Help, k.Quit},
	}
}

func NewPropertiesViewer() *PropertiesViewer {
	// Initialize key bindings
	keys := propertiesKeyMap{
		Up: key.NewBinding(
			key.WithKeys("up", "k"),
			key.WithHelp("↑/k", "up"),
		),
		Down: key.NewBinding(
			key.WithKeys("down", "j"),
			key.WithHelp("↓/j", "down"),
		),

		Edit: key.NewBinding(
			key.WithKeys("enter"),
			key.WithHelp("enter", "edit"),
		),
		Cancel: key.NewBinding(
			key.WithKeys("esc"),
			key.WithHelp("esc", "cancel"),
		),
		Search: key.NewBinding(
			key.WithKeys("/", "ctrl+f"),
			key.WithHelp("/|ctrl+f", "search"),
		),
		Help: key.NewBinding(
			key.WithKeys("?"),
			key.WithHelp("?", "help"),
		),
		Quit: key.NewBinding(
			key.WithKeys("q", "ctrl+c"),
			key.WithHelp("q", "quit"),
		),
		Toggle: key.NewBinding(
			key.WithKeys("ctrl+t"),
			key.WithHelp("ctrl+t", "toggle bool"),
		),
	}

	// Initialize list with custom delegate for blue selection
	items := []list.Item{}
	delegate := list.NewDefaultDelegate()
	delegate.Styles.SelectedTitle = delegate.Styles.SelectedTitle.Foreground(styles.Blue)
	delegate.Styles.FilterMatch = delegate.Styles.FilterMatch.Foreground(styles.Yellow)
	l := list.New(items, delegate, 0, 0)
	l.SetShowHelp(false)
	l.SetShowTitle(false)
	l.SetShowStatusBar(false)
	l.SetFilteringEnabled(true)
	l.SetShowFilter(false)

	// Note: The list will be constrained to a fixed width when SetSize is called

	// Initialize viewport
	vp := viewport.New(0, 0)
	vp.Style = lipgloss.NewStyle().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(styles.Surface2).
		Padding(1).
		Width(0) // Allow viewport to expand to fill available space

	// Initialize text input
	ti := textinput.New()
	ti.Placeholder = "Enter new value..."
	ti.CharLimit = 1000
	ti.Width = 50
	ti.Prompt = "Value: "

	// Initialize help
	h := help.New()

	return &PropertiesViewer{
		BaseCommand: NewBaseCommand(),
		list:        l,
		viewport:    vp,
		textInput:   ti,
		help:        h,
		keys:        keys,
		saving:      false,
	}
}

// Cleanup ensures the terminal is restored to its original state
func (pv *PropertiesViewer) Cleanup() {
	// No cleanup needed - no ongoing operations
}

func (pv *PropertiesViewer) Init() tea.Cmd {
	return tea.Batch(
		tea.EnterAltScreen,
		pv.fetchProperties(),
	)
}

func (pv *PropertiesViewer) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmds []tea.Cmd

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		pv.width = msg.Width
		pv.height = msg.Height
		pv.ready = true

		// Update list dimensions - use calculated width based on longest property name
		listHeight := pv.height - 4 // Reserve space for header, footer, and help

		// Use the calculated list width (with fallback to 60 if not calculated yet)
		listWidth := pv.listWidth
		if listWidth == 0 {
			listWidth = 60 // Fallback width
		}

		pv.list.SetSize(listWidth, listHeight)

		// Update viewport dimensions - fill remaining space
		vpHeight := listHeight
		// Calculate viewport width: total width - list width - 4 (spacer + padding)
		vpWidth := pv.width - listWidth - 4
		if vpWidth < 20 {
			vpWidth = 20 // Ensure minimum viewport width
		}
		pv.viewport.Width = vpWidth
		pv.viewport.Height = vpHeight

		// Update text input width
		pv.textInput.Width = vpWidth - 4

	case tea.KeyMsg:
		if pv.textInput.Focused() {
			switch msg.String() {
			case "enter":
				return pv, pv.saveProperty()
			case "esc":
				pv.err = nil // Clear error on cancel
				pv.textInput.Blur()
				pv.textInput.SetValue("")
				return pv, nil
			}

			// Update text input
			var cmd tea.Cmd
			pv.textInput, cmd = pv.textInput.Update(msg)
			return pv, cmd
		}

		switch msg.String() {
		case "q", "ctrl+c":
			return pv, tea.Batch(
				tea.ExitAltScreen,
				tea.Quit,
			)
		case "?":
			pv.help.ShowAll = !pv.help.ShowAll
		case "/", "ctrl+f", "C-f", "\x06":
			pv.list.SetShowFilter(!pv.list.ShowFilter())
			var cmd tea.Cmd
			if pv.list.ShowFilter() && msg.String() != "/" {
				// Simulate a '/' key event to focus the filter input
				fakeMsg := tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{'/'}, Alt: false}
				pv.list, cmd = pv.list.Update(fakeMsg)
			} else {
				pv.list, cmd = pv.list.Update(msg)
			}
			return pv, cmd
		case "esc":
			if pv.list.ShowFilter() {
				// Always pass esc to list to update its state (this will clear the filter input if needed)
				var cmd tea.Cmd
				pv.list, cmd = pv.list.Update(msg)
				// After updating, if the filter value is now empty, hide the filter field
				if pv.list.FilterValue() == "" {
					pv.list.SetShowFilter(false)
				}
				return pv, cmd
			}
		case "enter":
			if pv.selectedProp != nil {
				pv.textInput.SetValue(pv.selectedProp.Value)
				pv.textInput.Focus()
				return pv, textinput.Blink
			}
		case "ctrl+t":
			if pv.selectedProp != nil && pv.selectedProp.DataType == graphql.DataTypeBoolean {
				newValue := "false"
				if pv.selectedProp.Value != "true" {
					newValue = "true"
				}
				pv.textInput.SetValue(newValue)
				return pv, pv.saveProperty()
			}
		}

	case propertiesLoadedMsg:
		pv.properties = msg.properties
		pv.updateList()
		pv.loading = false

		// Calculate list width based on the longest property name (only once)
		if pv.listWidth == 0 {
			maxKeyLength := 0
			for _, prop := range pv.properties {
				if len(prop.Key) > maxKeyLength {
					maxKeyLength = len(prop.Key)
				}
			}
			// Add some padding for indicators and borders
			pv.listWidth = maxKeyLength + 10
			// Ensure minimum and maximum reasonable widths
			if pv.listWidth < 40 {
				pv.listWidth = 40
			}
			if pv.listWidth > 80 {
				pv.listWidth = 80
			}

			// Update the list size with the calculated width if we have dimensions
			if pv.ready && pv.height > 0 {
				listHeight := pv.height - 4
				pv.list.SetSize(pv.listWidth, listHeight)

				// Also update viewport width with the correct list width
				vpWidth := pv.width - pv.listWidth - 4
				if vpWidth < 20 {
					vpWidth = 20
				}
				pv.viewport.Width = vpWidth
				pv.viewport.Height = listHeight
			}
		}

		// Update selected property reference if it exists
		if pv.selectedProp != nil {
			for _, prop := range pv.properties {
				if prop.Key == pv.selectedProp.Key {
					pv.selectedProp = &prop
					break
				}
			}
		}

		// Force a viewport update to refresh the display
		if pv.selectedProp != nil {
			pv.updateViewport()
		}

		// Force the list to refresh its display
		pv.updateList()

	case propertySavedMsg:
		// Always reset saving state so user can retry
		pv.saving = false
		if msg.err != nil {
			pv.err = msg.err
			// Stay in edit mode: ensure text input is focused and value is preserved
			if pv.selectedProp == nil || pv.selectedProp.Key != msg.key {
				// Find and set the selected property by key
				for _, prop := range pv.properties {
					if prop.Key == msg.key {
						pv.selectedProp = &prop
						break
					}
				}
			}
			pv.textInput.SetValue(pv.lastSavedValue)
			pv.textInput.Focus()
			return pv, nil
		} else {
			pv.err = nil // Clear error on successful save
			// On success, blur and clear the text input
			pv.textInput.Blur()
			pv.textInput.SetValue("")
			// Start retry mechanism to fetch properties until we see the updated value
			return pv, tea.Tick(500*time.Millisecond, func(t time.Time) tea.Msg {
				return refreshPropertiesMsg{
					attempts:      0,
					expectedKey:   msg.key,
					expectedValue: pv.lastSavedValue, // The value we just saved
				}
			})
		}

	case refreshPropertiesMsg:
		// Refresh properties and check if the expected value is present
		return pv, tea.Batch(
			pv.fetchProperties(),
			tea.Tick(100*time.Millisecond, func(t time.Time) tea.Msg {
				return checkUpdateMsg{
					attempts:      msg.attempts,
					expectedKey:   msg.expectedKey,
					expectedValue: msg.expectedValue,
				}
			}),
		)

	case checkUpdateMsg:
		// Check if the expected value is now present in the properties
		found := false
		for _, prop := range pv.properties {
			if prop.Key == msg.expectedKey && prop.Value == msg.expectedValue {
				found = true
				break
			}
		}

		if found {
			// Value found, update the list and we're done
			pv.updateList()
			return pv, nil
		} else if msg.attempts < 5 {
			// Value not found yet, retry after another delay
			return pv, tea.Tick(500*time.Millisecond, func(t time.Time) tea.Msg {
				return refreshPropertiesMsg{
					attempts:      msg.attempts + 1,
					expectedKey:   msg.expectedKey,
					expectedValue: msg.expectedValue,
				}
			})
		} else {
			// Max attempts reached, just update the list with what we have
			pv.updateList()
			return pv, nil
		}

	case error:
		pv.err = msg
		pv.loading = false
	}

	// Only update other components if text input is not focused
	if !pv.textInput.Focused() {
		// Update list
		var cmd tea.Cmd
		pv.list, cmd = pv.list.Update(msg)
		// Ensure filter field is always visible if filter string is non-empty
		if pv.list.FilterValue() != "" {
			pv.list.SetShowFilter(true)
		}
		cmds = append(cmds, cmd)

		// Check if list selection changed
		if pv.list.SelectedItem() != nil {
			pv.updateSelection()
		}

		// Update viewport
		pv.viewport, cmd = pv.viewport.Update(msg)
		cmds = append(cmds, cmd)
	} else {
		// When editing, still update the list to ensure it refreshes
		var cmd tea.Cmd
		pv.list, cmd = pv.list.Update(msg)
		cmds = append(cmds, cmd)
	}

	// Always update help
	var cmd tea.Cmd
	pv.help, cmd = pv.help.Update(msg)
	cmds = append(cmds, cmd)

	return pv, tea.Batch(cmds...)
}

func (pv *PropertiesViewer) View() string {
	if !pv.ready {
		return "Loading..."
	}

	if pv.loading {
		return pv.spinner.View() + " Loading properties..."
	}

	// Create the layout
	doc := strings.Builder{}

	// Header
	header := styles.HeaderStyle.Render("DeltaFi Properties Viewer")
	if len(pv.properties) > 0 {
		header += styles.CommentStyle.Render(fmt.Sprintf(" (%d properties)", len(pv.properties)))
	}
	doc.WriteString(header)
	doc.WriteString("\n\n")

	if pv.textInput.Focused() {
		// Editing mode - show text input prominently
		doc.WriteString("Editing property: " + styles.HeaderStyle.Render(pv.selectedProp.Key))
		doc.WriteString("\n\n")
		doc.WriteString("Current value: " + pv.selectedProp.Value)
		doc.WriteString("\n\n")
		doc.WriteString("New value:\n")
		doc.WriteString(pv.textInput.View())
		doc.WriteString("\n\n")
		// Show error inline if present
		if pv.err != nil {
			doc.WriteString(styles.ErrorStyle.Render("Error: " + pv.err.Error()))
			doc.WriteString("\n\n")
		}
		doc.WriteString("Press Enter to save, Esc to cancel")
	} else {
		// Normal mode - show list and viewport
		// Use calculated list width based on longest property name
		listView := pv.list.View()
		viewportView := pv.viewport.View()

		// Use the calculated list width (with fallback to 60 if not calculated yet)
		listWidth := pv.listWidth
		if listWidth == 0 {
			listWidth = 60 // Fallback width
		}

		// Force the list to be exactly the calculated width
		listView = lipgloss.NewStyle().Width(listWidth).Render(listView)

		// Calculate remaining space for viewport
		remainingWidth := pv.width - listWidth - 4 // list width + 2 for spacer + 2 for padding
		if remainingWidth < 20 {
			remainingWidth = 20 // Minimum viewport width
		}

		// Use a layout that right-justifies the viewport
		content := lipgloss.JoinHorizontal(
			lipgloss.Top,
			listView,
			lipgloss.NewStyle().Width(2).Render(""), // Spacer
			lipgloss.NewStyle().Width(remainingWidth).Render(viewportView),
		)

		doc.WriteString(content)
		doc.WriteString("\n")
		// Show error as full-screen only if not editing and error exists
		if pv.err != nil {
			doc.WriteString(styles.ErrorStyle.Render("Error: " + pv.err.Error()))
			doc.WriteString("\n")
		}
	}

	// Help
	helpView := pv.help.View(pv.keys)
	doc.WriteString(helpView)

	return doc.String()
}

// Message types
type propertiesLoadedMsg struct {
	properties []Property
}

type propertySavedMsg struct {
	key string
	err error
}

type refreshPropertiesMsg struct {
	attempts      int
	expectedKey   string
	expectedValue string
}

type checkUpdateMsg struct {
	attempts      int
	expectedKey   string
	expectedValue string
}

func (pv *PropertiesViewer) fetchProperties() tea.Cmd {
	return func() tea.Msg {
		resp, err := graphql.GetPropertySets()
		if err != nil {
			return errMsg{err}
		}

		var properties []Property
		for _, set := range resp.GetPropertySets {
			for _, prop := range set.Properties {
				value := ""
				if prop.Value != nil {
					value = *prop.Value
				}
				defaultValue := ""
				if prop.DefaultValue != nil {
					defaultValue = *prop.DefaultValue
				}

				properties = append(properties, Property{
					Key:          prop.Key,
					Value:        value,
					DefaultValue: defaultValue,
					Description:  prop.Description,
					Source:       prop.PropertySource,
					DataType:     prop.DataType,
					SetName:      set.DisplayName,
				})
			}
		}

		// Sort properties by key
		sort.Slice(properties, func(i, j int) bool {
			return properties[i].Key < properties[j].Key
		})

		return propertiesLoadedMsg{properties: properties}
	}
}

func (pv *PropertiesViewer) updateList() {
	var items []list.Item
	for _, prop := range pv.properties {
		items = append(items, PropertyItem{Property: prop})
	}

	// Remember current selection index
	currentIndex := pv.list.Index()

	// Clear and re-set items to force refresh
	pv.list.SetItems([]list.Item{})
	pv.list.SetItems(items)

	// Restore selection if it was valid
	if currentIndex >= 0 && currentIndex < len(items) {
		pv.list.Select(currentIndex)
	}

	// Ensure the list maintains its calculated width after updating items
	if pv.ready && pv.height > 0 {
		listHeight := pv.height - 4
		listWidth := pv.listWidth
		if listWidth == 0 {
			listWidth = 60 // Fallback width
		}
		pv.list.SetSize(listWidth, listHeight)
	}
}

func (pv *PropertiesViewer) saveProperty() tea.Cmd {
	if pv.selectedProp == nil {
		return nil
	}

	newValue := pv.textInput.Value()
	// If the value hasn't changed, treat as no-op
	if newValue == pv.selectedProp.Value {
		pv.err = nil
		pv.textInput.Blur()
		pv.textInput.SetValue("")
		return nil
	}

	pv.saving = true
	pv.lastSavedValue = newValue // Store the value we're about to save
	pv.textInput.Blur()
	pv.textInput.SetValue("")

	return func() tea.Msg {
		updates := []graphql.KeyValueInput{{
			Key:   pv.selectedProp.Key,
			Value: &newValue,
		}}

		resp, err := graphql.UpdateProperties(updates)
		if err != nil {
			return propertySavedMsg{key: pv.selectedProp.Key, err: err}
		}

		if !resp.UpdateProperties {
			return propertySavedMsg{key: pv.selectedProp.Key, err: fmt.Errorf("failed to update property")}
		}

		return propertySavedMsg{key: pv.selectedProp.Key, err: nil}
	}
}

// Update list selection and viewport content
func (pv *PropertiesViewer) updateSelection() {
	if len(pv.list.Items()) == 0 {
		return
	}

	selected := pv.list.SelectedItem()
	if item, ok := selected.(PropertyItem); ok {
		pv.selectedProp = &item.Property
		pv.updateViewport()
	}
}

func (pv *PropertiesViewer) updateViewport() {
	if pv.selectedProp == nil {
		pv.viewport.SetContent("Select a property to view details")
		return
	}

	content := strings.Builder{}

	// Property header - ensure it spans the full width
	header := styles.HeaderStyle.Render(pv.selectedProp.Key)
	if pv.viewport.Width > 0 {
		// Pad the header to use full width
		header = lipgloss.NewStyle().Width(pv.viewport.Width - 2).Render(header)
	}
	content.WriteString(header)
	content.WriteString("\n\n")

	// Property details
	content.WriteString(styles.SubheaderStyle.Render("Details"))
	content.WriteString("\n")
	content.WriteString("Set: " + pv.selectedProp.SetName + "\n")
	content.WriteString("Type: " + string(pv.selectedProp.DataType) + "\n")
	content.WriteString("Source: " + string(pv.selectedProp.Source) + "\n\n")

	// Current value
	content.WriteString(styles.SubheaderStyle.Render("Current Value"))
	content.WriteString("\n")
	if pv.selectedProp.Value != "" {
		content.WriteString(pv.selectedProp.Value)
	} else {
		content.WriteString(styles.CommentStyle.Render("(using default)"))
	}
	content.WriteString("\n\n")

	// Default value
	content.WriteString(styles.SubheaderStyle.Render("Default Value"))
	content.WriteString("\n")
	if pv.selectedProp.DefaultValue != "" {
		content.WriteString(pv.selectedProp.DefaultValue)
	} else {
		content.WriteString(styles.CommentStyle.Render("(none)"))
	}
	content.WriteString("\n\n")

	// Description
	content.WriteString(styles.SubheaderStyle.Render("Description"))
	content.WriteString("\n")
	// Format description to wrap properly within the viewport width
	description := pv.selectedProp.Description
	if pv.viewport.Width > 0 {
		// Calculate available width for content (viewport width minus padding and borders)
		availableWidth := pv.viewport.Width - 6
		if availableWidth > 0 {
			// Simple word wrapping for the description
			words := strings.Fields(description)
			line := ""
			for _, word := range words {
				if len(line)+len(word)+1 <= availableWidth {
					if line != "" {
						line += " "
					}
					line += word
				} else {
					if line != "" {
						content.WriteString(line)
						content.WriteString("\n")
					}
					line = word
				}
			}
			if line != "" {
				content.WriteString(line)
			}
		} else {
			content.WriteString(description)
		}
	} else {
		content.WriteString(description)
	}

	pv.viewport.SetContent(content.String())
}
