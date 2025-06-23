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
	"regexp"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/table"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/cmd/util"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/dustin/go-humanize"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
	"golang.org/x/term"

	"github.com/charmbracelet/bubbles/viewport"
)

type searchModel struct {
	table      table.Model
	height     int
	width      int
	deltaFiles []graphql.DeltaFilesDeltaFilesDeltaFilesDeltaFile
	selected   int
	modalData  *graphql.DeltaFilesDeltaFilesDeltaFilesDeltaFile
	client     *api.Client
	offset     int
	goingUp    bool
	filter     searchFilter
	showHelp   bool
	viewport   viewport.Model
}
type searchFilter struct {
	startTimeStr       *string
	startTime          *time.Time
	endTimeStr         *string
	endTime            *time.Time
	egressed           *bool
	filtered           *bool
	dataSources        *[]string
	pinned             *bool
	testMode           *bool
	replayable         *bool
	replayed           *bool
	errorAcknowledged  *bool
	terminalStage      *bool
	pendingAnnotations *bool
	paused             *bool
	contentDeleted     *bool
	transforms         *[]string
	dataSinks          *[]string
	annotations        *[]graphql.KeyValueInput
	stage              *graphql.DeltaFileStage
	topics             *[]string
	name               *string
	requeueCountMin    *int
	ingressBytesMin    *int64
	ingressBytesMax    *int64
	referencedBytesMin *int64
	referencedBytesMax *int64
	totalBytesMin      *int64
	totalBytesMax      *int64
	filteredCause      *string
	errorCause         *string
	dids               *[]uuid.UUID
	order              *graphql.DeltaFileOrder
}

func NewSearchFilter(start string, end string) searchFilter {
	retval := searchFilter{
		startTimeStr: &start,
		endTimeStr:   &end,
	}
	if sortBy == "" {
		sortBy = "modified"
	}
	retval.order = &graphql.DeltaFileOrder{
		Direction: graphql.DeltaFileDirectionDesc,
		Field:     getSortField(sortBy),
	}
	retval.refresh()
	return retval
}

func (f *searchFilter) setOrderAscending() {
	if f.order == nil {
		f.order = &graphql.DeltaFileOrder{
			Direction: graphql.DeltaFileDirectionAsc,
			Field:     getSortField(sortBy),
		}
	}
	f.order.Direction = graphql.DeltaFileDirectionAsc
	f.order.Field = getSortField(sortBy)
}

func (f *searchFilter) setOrderDescending() {
	if f.order == nil {
		f.order = &graphql.DeltaFileOrder{
			Direction: graphql.DeltaFileDirectionDesc,
			Field:     getSortField(sortBy),
		}
	}
	f.order.Direction = graphql.DeltaFileDirectionDesc
	f.order.Field = getSortField(sortBy)
}

func (f *searchFilter) toGraphQLFilter() *graphql.DeltaFilesFilter {
	retval := &graphql.DeltaFilesFilter{}
	if f.name != nil && *f.name != "" {
		falseVal := false
		retval.NameFilter = &graphql.NameFilter{
			Name:          *f.name,
			CaseSensitive: &falseVal,
		}
	}
	if f.stage != nil {
		retval.Stage = f.stage
	}
	if f.startTime != nil {
		if useCreationTime {
			retval.CreatedAfter = f.startTime
		} else {
			retval.ModifiedAfter = f.startTime
		}
	}
	if f.endTime != nil {
		if useCreationTime {
			retval.CreatedBefore = f.endTime
		} else {
			retval.ModifiedBefore = f.endTime
		}
	}
	if f.dataSources != nil {
		retval.DataSources = *f.dataSources
	}
	if f.transforms != nil {
		retval.Transforms = *f.transforms
	}
	if f.dataSinks != nil {
		retval.DataSinks = *f.dataSinks
	}
	if f.topics != nil {
		retval.Topics = *f.topics
	}
	if f.annotations != nil {
		retval.Annotations = *f.annotations
	}
	if f.dids != nil {
		retval.Dids = *f.dids
	}
	if f.requeueCountMin != nil && *f.requeueCountMin > 0 {
		retval.RequeueCountMin = f.requeueCountMin
	}
	if f.ingressBytesMin != nil && *f.ingressBytesMin > 0 {
		retval.IngressBytesMin = f.ingressBytesMin
	}
	if f.ingressBytesMax != nil && *f.ingressBytesMax > 0 {
		retval.IngressBytesMax = f.ingressBytesMax
	}
	if f.referencedBytesMin != nil && *f.referencedBytesMin > 0 {
		retval.ReferencedBytesMin = f.referencedBytesMin
	}
	if f.referencedBytesMax != nil && *f.referencedBytesMax > 0 {
		retval.ReferencedBytesMax = f.referencedBytesMax
	}
	if f.totalBytesMin != nil && *f.totalBytesMin > 0 {
		retval.TotalBytesMin = f.totalBytesMin
	}
	if f.totalBytesMax != nil && *f.totalBytesMax > 0 {
		retval.TotalBytesMax = f.totalBytesMax
	}
	if f.filteredCause != nil && *f.filteredCause != "" {
		retval.FilteredCause = f.filteredCause
	}
	if f.errorCause != nil && *f.errorCause != "" {
		retval.ErrorCause = f.errorCause
	}
	if f.egressed != nil {
		retval.Egressed = f.egressed
	}
	if f.filtered != nil {
		retval.Filtered = f.filtered
	}
	if f.pinned != nil {
		retval.Pinned = f.pinned
	}
	if f.testMode != nil {
		retval.TestMode = f.testMode
	}
	if f.replayable != nil {
		retval.Replayable = f.replayable
	}
	if f.replayed != nil {
		retval.Replayed = f.replayed
	}
	if f.errorAcknowledged != nil {
		retval.ErrorAcknowledged = f.errorAcknowledged
	}
	if f.terminalStage != nil {
		retval.TerminalStage = f.terminalStage
	}
	if f.pendingAnnotations != nil {
		retval.PendingAnnotations = f.pendingAnnotations
	}
	if f.paused != nil {
		retval.Paused = f.paused
	}
	if f.contentDeleted != nil {
		retval.ContentDeleted = f.contentDeleted
	}
	return retval
}

func (f *searchFilter) refresh() error {

	now := time.Now()

	if f.startTimeStr != nil {
		startTime, err := util.ParseHumanizedTime(*f.startTimeStr)
		if err != nil {
			return err
		}
		f.startTime = &startTime
	} else {
		startOfDay := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
		f.startTime = &startOfDay
	}

	if f.endTimeStr != nil {
		endTime, err := util.ParseHumanizedTime(*f.endTimeStr)
		if err != nil {
			return err
		}
		f.endTime = &endTime
	} else {
		f.endTime = &now
	}

	return nil
}

func (f *searchFilter) getTableColumns(width int) []table.Column {

	sizeWidth := 8
	modifiedWidth := 20
	stageWidth := 10
	columnPad := 6
	borderPad := 4

	if width < 100 {
		sizeWidth = 0
		columnPad = 4
	}
	if width < 90 {
		modifiedWidth = 0
		columnPad = 2
	}
	if width < 80 {
		stageWidth = 0
		columnPad = 0
	}

	dataSourceWidth := (width - sizeWidth - modifiedWidth - stageWidth - columnPad - borderPad) / 2
	filenameWidth := width - sizeWidth - modifiedWidth - stageWidth - dataSourceWidth - columnPad - borderPad

	return []table.Column{
		{Title: getColumnTitle("filename", f.order), Width: filenameWidth},
		{Title: getColumnTitle("data-source", f.order), Width: dataSourceWidth},
		{Title: getColumnTitle("stage", f.order), Width: stageWidth},
		{Title: getColumnTitle("modified", f.order), Width: modifiedWidth},
		{Title: getColumnTitle("size", f.order), Width: sizeWidth},
	}
}

func (m *searchModel) updateTableColumns() {

	columns := m.filter.getTableColumns(m.width)
	m.table.SetColumns(columns)
}

func (m *searchModel) updateTableRows() {
	rows := make([]table.Row, len(m.deltaFiles))
	for i, df := range m.deltaFiles {
		rows[i] = table.Row{
			getStringValue(df.Name),
			df.DataSource,
			string(df.Stage),
			formatTime(df.Modified),
			formatSearchBytes(df.TotalBytes),
		}
	}
	m.table.SetRows(rows)
	m.table.SetCursor(m.selected)
	m.table.SetHeight(m.height - 2) // Leave room for title and help text
}

type searchDeltaFilesMsg struct {
	deltaFiles []graphql.DeltaFilesDeltaFilesDeltaFilesDeltaFile
	totalCount int
}

type searchErrMsg struct {
	error error
}

type navigateToDeltaFileMsg struct {
	did uuid.UUID
}

func initialSearchModel(filter searchFilter) searchModel {
	width, _, _ := term.GetSize(int(os.Stdout.Fd()))
	columns := filter.getTableColumns(width)

	t := table.New(
		table.WithColumns(columns),
		table.WithFocused(true),
		table.WithHeight(0), // Will be set on window size
	)

	s := table.DefaultStyles()
	s.Header = s.Header.
		Foreground(styles.Blue).
		Bold(true).
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(styles.Surface2).
		BorderBottom(true)
	s.Selected = s.Selected.
		Foreground(styles.Text).
		Background(styles.Blue).
		Bold(false)
	t.SetStyles(s)

	vp := viewport.New(0, 0)
	vp.Style = lipgloss.NewStyle().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(styles.Blue).
		Padding(1)
	vp.MouseWheelEnabled = true

	return searchModel{
		table:    t,
		selected: 0,
		client:   app.GetInstance().GetAPIClient(),
		offset:   0,
		goingUp:  false,
		filter:   filter,
		showHelp: false,
		viewport: vp,
	}
}

func getColumnTitle(column string, order *graphql.DeltaFileOrder) string {
	columnTitle := column

	switch column {
	case "modified":
		columnTitle = "Modified"
	case "filename":
		columnTitle = "Filename"
	case "data-source":
		columnTitle = "Data Source"
	case "stage":
		columnTitle = "Stage"
	case "size":
		columnTitle = "Size"
	default:
		columnTitle = column
	}

	if order != nil && getSortField(sortBy) == getSortField(column) {
		arrow := "↓"
		if order.Direction == graphql.DeltaFileDirectionAsc {
			arrow = "↑"
		}
		columnTitle += " " + arrow
	}

	return columnTitle
}

func getSortField(column string) string {
	switch column {
	case "filename":
		return "name"
	case "data-source":
		return "dataSource"
	case "stage":
		return "stage"
	case "size":
		return "totalBytes"
	default:
		return "modified"
	}
}

var sortColumns = []string{
	"modified",
	"size",
	"filename",
	"data-source",
	"stage",
}

func getNextSortColumn(current string) string {
	for i, col := range sortColumns {
		if col == current {
			nextIndex := (i + 1) % len(sortColumns)
			return sortColumns[nextIndex]
		}
	}
	return "modified" // Default if current not found
}

func getPreviousSortColumn(current string) string {
	for i, col := range sortColumns {
		if col == current {
			prevIndex := (i - 1 + len(sortColumns)) % len(sortColumns)
			return sortColumns[prevIndex]
		}
	}
	return "modified" // Default if current not found
}

func (m searchModel) Init() tea.Cmd {
	width, height, err := term.GetSize(int(os.Stdout.Fd()))
	if err != nil || width < 40 {
		width = 80 // Default width if detection fails
	}
	if err != nil {
		height = 40
	}

	m.width = width
	m.height = height
	return m.fetchDeltaFiles
}

func (m searchModel) fetchDeltaFiles() tea.Msg {

	limit := m.height - 5
	filter := m.filter.toGraphQLFilter()
	orderBy := m.filter.order

	response, err := graphql.DeltaFiles(&m.offset, &limit, filter, orderBy)
	if err != nil {
		return searchErrMsg{error: err}
	}

	return searchDeltaFilesMsg{
		deltaFiles: response.DeltaFiles.DeltaFiles,
		totalCount: *response.DeltaFiles.TotalCount,
	}
}

func (m searchModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var cmd tea.Cmd

	switch msg := msg.(type) {
	case navigateToDeltaFileMsg:
		if df, err := RenderDeltaFile(msg.did); err == nil {
			m.viewport.SetContent(df)
			m.viewport.GotoTop()
		} else {
			m.viewport.SetContent(styles.WarningStyle.Render("Deltafile not found: " + msg.did.String()))
		}
		return m, nil
	case tea.KeyMsg:
		if msg.String() == "ctrl+c" || msg.String() == "q" {
			return m, tea.Quit
		}
		if m.modalData != nil || m.showHelp {
			switch msg.String() {
			case "esc":
				m.modalData = nil
				m.showHelp = false
				return m, nil
			}
			// Let the viewport handle all other key events when modal is shown
			if m.modalData != nil {
				m.viewport, cmd = m.viewport.Update(msg)
				return m, cmd
			}
		} else {
			switch msg.String() {
			case "esc", "q":
				return m, tea.Quit
			case "h":
				m.showHelp = true
				return m, nil
			case "enter":
				if len(m.deltaFiles) > 0 {
					m.selected = m.table.Cursor()
					m.modalData = &m.deltaFiles[m.selected]
					// Set viewport dimensions when entering modal
					m.viewport.Width = m.width
					m.viewport.Height = m.height - 2 // Account for help text
					// Reset viewport position when entering modal
					m.viewport.GotoTop()
					// Initialize viewport content
					if df, err := RenderDeltaFile(m.modalData.Did); err == nil {
						m.viewport.SetContent(df)
					}
				}
				return m, nil
			case "tab":
				// Cycle to next sort column
				sortBy = getNextSortColumn(sortBy)
				// Create new order with current direction
				direction := graphql.DeltaFileDirectionDesc
				if m.filter.order != nil && m.filter.order.Direction == graphql.DeltaFileDirectionAsc {
					direction = graphql.DeltaFileDirectionAsc
				}
				m.filter.order = &graphql.DeltaFileOrder{
					Direction: direction,
					Field:     getSortField(sortBy),
				}
				m.updateTableColumns()
				return m, m.fetchDeltaFiles
			case "shift+tab":
				// Cycle to previous sort column
				sortBy = getPreviousSortColumn(sortBy)
				// Create new order with current direction
				direction := graphql.DeltaFileDirectionDesc
				if m.filter.order != nil && m.filter.order.Direction == graphql.DeltaFileDirectionAsc {
					direction = graphql.DeltaFileDirectionAsc
				}
				m.filter.order = &graphql.DeltaFileOrder{
					Direction: direction,
					Field:     getSortField(sortBy),
				}
				m.updateTableColumns()
				return m, m.fetchDeltaFiles
			case "o":
				// Toggle sort order
				if m.filter.order != nil && m.filter.order.Direction == graphql.DeltaFileDirectionAsc {
					m.filter.setOrderDescending()
				} else {
					m.filter.setOrderAscending()
				}
				// Update all column titles with the new sort direction
				columns := m.table.Columns()
				columns[0].Title = getColumnTitle("filename", m.filter.order)
				columns[1].Title = getColumnTitle("data-source", m.filter.order)
				columns[2].Title = getColumnTitle("stage", m.filter.order)
				columns[3].Title = getColumnTitle("modified", m.filter.order)
				columns[4].Title = getColumnTitle("size", m.filter.order)
				m.table.SetColumns(columns)
				m.offset = 0
				m.selected = 0
				return m, m.fetchDeltaFiles
			case "up", "k":
				if m.selected > 0 {
					m.selected--
					m.table.SetCursor(m.selected)
				} else if m.offset > 0 {
					m.offset = max(0, m.offset-(m.height-5))
					m.goingUp = true
					return m, m.fetchDeltaFiles
				}
				return m, nil
			case "down", "j":
				if m.selected < len(m.deltaFiles)-1 {
					m.selected++
					m.table.SetCursor(m.selected)
				} else if len(m.deltaFiles) > 0 {
					m.offset += m.height - 5
					m.selected = 0
					m.goingUp = false
					return m, m.fetchDeltaFiles
				}
				return m, nil
			case "pgup":
				if m.offset > 0 {
					m.offset = max(0, m.offset-(m.height-5))
					m.selected = 0
					return m, m.fetchDeltaFiles
				}
				return m, nil
			case "pgdown":
				if len(m.deltaFiles) > 0 {
					m.offset += m.height - 5
					m.selected = 0
					return m, m.fetchDeltaFiles
				}
				return m, nil
			case "r":
				m.offset = 0
				m.filter.refresh()
				m.goingUp = false
				return m, m.fetchDeltaFiles
			case "g":
				m.offset = 0
				m.selected = 0
				m.goingUp = false
				return m, m.fetchDeltaFiles
			}
		}
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.table.SetHeight(m.height - 2) // Leave room for title and help text
		m.updateTableColumns()
		if m.modalData != nil {
			m.viewport.Width = m.width
			m.viewport.Height = m.height - 2 // Account for help text
			// Reinitialize viewport content on window resize
			if df, err := RenderDeltaFile(m.modalData.Did); err == nil {
				m.viewport.SetContent(df)
			}
		}
		return m, m.fetchDeltaFiles
	case tea.MouseMsg:
		if m.modalData != nil {
			// Handle viewport mouse events
			if msg.Action == tea.MouseActionPress && msg.Button == tea.MouseButtonLeft {
				// Get the clicked line from the viewport
				content := m.viewport.View()
				lines := strings.Split(content, "\n")
				adjustedY := msg.Y
				if adjustedY >= 0 && adjustedY < len(lines) {
					line := lines[adjustedY]
					// Try to find a UUID in the clicked line
					if uuidStr := findUUIDInString(line); uuidStr != "" {
						if did, err := uuid.Parse(uuidStr); err == nil {
							return m, func() tea.Msg {
								return navigateToDeltaFileMsg{did: did}
							}
						}
					}
				}
			}
			m.viewport, cmd = m.viewport.Update(msg)
			return m, cmd
		} else if msg.Action == tea.MouseActionPress && msg.Button == tea.MouseButtonWheelUp {
			if m.selected > 0 {
				m.selected--
				m.table.SetCursor(m.selected)
			} else if m.offset > 0 {
				m.offset = max(0, m.offset-(m.height-5))
				m.goingUp = true
				return m, m.fetchDeltaFiles
			}
			return m, nil
		} else if msg.Action == tea.MouseActionPress && msg.Button == tea.MouseButtonWheelDown {
			if m.selected < len(m.deltaFiles)-1 {
				m.selected++
				m.table.SetCursor(m.selected)
			} else if len(m.deltaFiles) > 0 {
				m.offset += m.height - 5
				m.selected = 0
				m.goingUp = false
				return m, m.fetchDeltaFiles
			}
			return m, nil
		}
	case searchDeltaFilesMsg:
		m.deltaFiles = msg.deltaFiles
		if m.goingUp && len(m.deltaFiles) > 0 {
			m.selected = len(m.deltaFiles) - 1
		} else {
			m.selected = 0
		}
		m.goingUp = false

		m.updateTableRows()
		return m, nil
	case searchErrMsg:
		fmt.Printf("Error: %v\n", msg.error)
		return m, nil
	}

	// Handle viewport updates when modal is shown
	if m.modalData != nil {
		m.viewport, cmd = m.viewport.Update(msg)
		return m, cmd
	}

	// Handle table updates when modal is not shown
	if m.modalData == nil {
		m.table, cmd = m.table.Update(msg)
	}

	return m, cmd
}

func (m searchModel) View() string {
	if m.modalData != nil {
		help := styles.BaseStyle.Foreground(styles.Surface2).Render("↑/↓: Scroll • Esc: Back • Ctrl+C: Quit")
		return lipgloss.JoinVertical(
			lipgloss.Left,
			m.viewport.View(),
			help,
		)
	}

	if m.showHelp {
		helpContent := lipgloss.JoinVertical(
			lipgloss.Left,
			styles.BaseStyle.Foreground(styles.Blue).Bold(true).Render("Key Bindings"),
			"",
			"Navigation:",
			"  ↑/k        Move selection up",
			"  ↓/j        Move selection down",
			"  pgup       Previous page",
			"  pgdown     Next page",
			"  g          Go to start",
			"",
			"Sorting:",
			"  tab        Cycle to next sort column",
			"  shift+tab  Cycle to previous sort column",
			"  o          Toggle sort direction",
			"",
			"Actions:",
			"  enter      View DeltaFile details",
			"  r          Refresh results",
			"  h          Show this help",
			"",
			"Other:",
			"  esc/q      Quit",
			"  ctrl+c     Quit",
			"",
			"Press esc to close this help",
		)

		// Center the help content both horizontally and vertically
		helpText := styles.BaseStyle.Render(
			lipgloss.Place(
				m.width,
				m.height,
				lipgloss.Center,
				lipgloss.Center,
				helpContent,
			),
		)
		return helpText
	}

	// Calculate row range
	startRow := m.offset + 1
	endRow := m.offset + len(m.deltaFiles)
	rowRange := fmt.Sprintf(" %d-%d", startRow, endRow)

	// Create the help text with row range
	help := lipgloss.JoinHorizontal(
		lipgloss.Left,
		styles.AccentStyle.Foreground(styles.Blue).Width(24).Render(rowRange),
		styles.AccentStyle.Render(" ↑/↓: Nav • Enter: View • h: Help • Ctrl+C: Quit"),
	)

	m.updateTableRows()

	return lipgloss.JoinVertical(
		lipgloss.Left,
		m.table.View(),
		help,
	)
}

var searchCmd = &cobra.Command{
	Use:   "search",
	Short: "Search and filter DeltaFiles with advanced criteria",
	Long: `Search and filter DeltaFiles using comprehensive criteria and filters.

Provides an interactive interface to search through all DeltaFiles
in the system with powerful filtering options including:
- Time-based filtering (creation, modification)
- Status filtering (egressed, filtered, paused, etc.)
- Content filtering (size, annotations, metadata)
- Flow-based filtering (data sources, transforms, sinks)
- Error and cause filtering

Supports human-readable time expressions like "today", "yesterday",
"last week", "in 2 hours", etc.

Examples:
  deltafi search --from "yesterday" --to "now"          # Time range
  deltafi search --datasources "file-watcher"           # By data source
  deltafi search --stage "ERROR" --error-cause "timeout" # Error filtering
  deltafi search --annotations "priority=high"          # Annotation filtering`,
	GroupID: "deltafile",
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		// Validate start time
		if _, err := util.ParseHumanizedTime(startTime); err != nil {
			return fmt.Errorf("invalid start time '%s': %w", startTime, err)
		}
		// Validate end time
		if _, err := util.ParseHumanizedTime(endTime); err != nil {
			return fmt.Errorf("invalid end time '%s': %w", endTime, err)
		}
		return nil
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		if len(args) > 0 {
			return fmt.Errorf("search command does not accept positional arguments: %v", args)
		}

		filter := NewSearchFilter(startTime, endTime)
		if len(dataSources) > 0 {
			filter.dataSources = &dataSources
		}
		if len(transforms) > 0 {
			filter.transforms = &transforms
		}
		if len(dataSinks) > 0 {
			filter.dataSinks = &dataSinks
		}
		if len(annotations) > 0 {
			annotationFilters := make([]graphql.KeyValueInput, 0, len(annotations))
			for _, annotation := range annotations {
				key, value, err := parseAnnotation(annotation)
				if err != nil {
					return err
				}
				annotationFilters = append(annotationFilters, graphql.KeyValueInput{
					Key:   key,
					Value: value,
				})
			}
			filter.annotations = &annotationFilters
		}
		if len(dids) > 0 {
			uuidDids := make([]uuid.UUID, 0, len(dids))
			for _, did := range dids {
				uuidDid, err := uuid.Parse(did)
				if err != nil {
					return fmt.Errorf("invalid DID format: %s", did)
				}
				uuidDids = append(uuidDids, uuidDid)
			}
			filter.dids = &uuidDids
		}
		if stage != "" {
			stageValue := graphql.DeltaFileStage(stage)
			filter.stage = &stageValue
		}
		if len(topics) > 0 {
			filter.topics = &topics
		}
		if name != "" {
			filter.name = &name
		}
		if requeueCountMin > 0 {
			filter.requeueCountMin = &requeueCountMin
		}
		if ingressBytesMin > 0 {
			filter.ingressBytesMin = &ingressBytesMin
		}
		if ingressBytesMax > 0 {
			filter.ingressBytesMax = &ingressBytesMax
		}
		if referencedBytesMin > 0 {
			filter.referencedBytesMin = &referencedBytesMin
		}
		if referencedBytesMax > 0 {
			filter.referencedBytesMax = &referencedBytesMax
		}
		if totalBytesMin > 0 {
			filter.totalBytesMin = &totalBytesMin
		}
		if totalBytesMax > 0 {
			filter.totalBytesMax = &totalBytesMax
		}
		if filteredCause != "" {
			filter.filteredCause = &filteredCause
		}
		if errorCause != "" {
			filter.errorCause = &errorCause
		}
		if egressed.IsSet() {
			val := egressed.IsTrue()
			filter.egressed = &val
		}
		if filtered.IsSet() {
			val := filtered.IsTrue()
			filter.filtered = &val
		}
		if pinned.IsSet() {
			val := pinned.IsTrue()
			filter.pinned = &val
		}
		if testMode.IsSet() {
			val := testMode.IsTrue()
			filter.testMode = &val
		}
		if replayable.IsSet() {
			val := replayable.IsTrue()
			filter.replayable = &val
		}
		if replayed.IsSet() {
			val := replayed.IsTrue()
			filter.replayed = &val
		}
		if errorAcknowledged.IsSet() {
			val := errorAcknowledged.IsTrue()
			filter.errorAcknowledged = &val
		}
		if terminalStage.IsSet() {
			val := terminalStage.IsTrue()
			filter.terminalStage = &val
		}
		if pendingAnnotations.IsSet() {
			val := pendingAnnotations.IsTrue()
			filter.pendingAnnotations = &val
		}
		if paused.IsSet() {
			val := paused.IsTrue()
			filter.paused = &val
		}
		if contentDeleted.IsSet() {
			val := contentDeleted.IsTrue()
			filter.contentDeleted = &val
		}

		p := tea.NewProgram(initialSearchModel(filter), tea.WithAltScreen(), tea.WithMouseCellMotion(), tea.WithMouseAllMotion())
		if _, err := p.Run(); err != nil {
			return fmt.Errorf("failed to run search: %v", err)
		}
		return nil
	},
}

var (
	startTime          string
	endTime            string
	useCreationTime    bool
	useLocal           bool
	useZulu            bool
	dataSources        []string
	transforms         []string
	dataSinks          []string
	annotations        []string
	stage              string
	topics             []string
	name               string
	requeueCountMin    int
	ingressBytesMin    int64
	ingressBytesMax    int64
	referencedBytesMin int64
	referencedBytesMax int64
	totalBytesMin      int64
	totalBytesMax      int64
	filteredCause      string
	errorCause         string
	egressed           util.TristateFlag
	filtered           util.TristateFlag
	pinned             util.TristateFlag
	testMode           util.TristateFlag
	replayable         util.TristateFlag
	replayed           util.TristateFlag
	errorAcknowledged  util.TristateFlag
	terminalStage      util.TristateFlag
	pendingAnnotations util.TristateFlag
	paused             util.TristateFlag
	contentDeleted     util.TristateFlag
	useHumanize        bool
	dids               []string
	ascending          bool
	descending         bool
	sortBy             string
)

func init() {
	rootCmd.AddCommand(searchCmd)
	searchCmd.Flags().StringVar(&startTime, "from", "today", "Display DeltaFiles modified after this time (default: today)")
	searchCmd.Flags().StringVar(&endTime, "to", "now", "Display DeltaFiles modified before this time (default: now)")
	searchCmd.Flags().StringVar(&endTime, "until", "now", "Alias for --to")
	searchCmd.Flags().BoolVarP(&useCreationTime, "creation-time", "C", false, "Filter by creation time instead of modification time")
	searchCmd.Flags().BoolVar(&useLocal, "local", false, "Display times in local timezone")
	searchCmd.Flags().BoolVar(&useZulu, "zulu", false, "Display times in UTC/Zulu timezone")
	searchCmd.Flags().BoolVar(&ascending, "ascending", false, "Sort results in ascending order")
	searchCmd.Flags().BoolVar(&descending, "descending", false, "Sort results in descending order")
	searchCmd.Flags().StringVar(&sortBy, "sort-by", "modified", "Column to sort by (modified, filename, data-source, stage, size)")

	searchCmd.Flags().BoolVar(&useHumanize, "humanize", false, "Display timestamps in human-readable format")

	searchCmd.Flags().StringSliceVarP(&dataSources, "data-source", "d", []string{}, "Filter by data source name (can be specified multiple times)")
	searchCmd.Flags().StringSliceVarP(&transforms, "transform", "t", []string{}, "Filter by transform name (can be specified multiple times)")
	searchCmd.Flags().StringSliceVarP(&dataSinks, "data-sink", "x", []string{}, "Filter by data sink name (can be specified multiple times)")
	searchCmd.Flags().StringSliceVar(&annotations, "annotation", []string{}, "Filter by annotation (format: key=value, can be specified multiple times)")
	searchCmd.Flags().StringVarP(&stage, "stage", "s", "", "Filter by stage (IN_FLIGHT, COMPLETE, ERROR, CANCELLED)")
	searchCmd.Flags().StringSliceVar(&topics, "topics", []string{}, "Filter by topic name (can be specified multiple times)")
	searchCmd.Flags().StringVarP(&name, "name", "n", "", "Filter by DeltaFile name")
	searchCmd.Flags().StringVar(&filteredCause, "filtered-cause", "", "Filter by filtered cause")
	searchCmd.Flags().StringVar(&errorCause, "error-cause", "", "Filter by error cause")
	searchCmd.Flags().StringSliceVar(&dids, "did", []string{}, "Filter by DeltaFile ID (can be specified multiple times)")

	searchCmd.Flags().IntVar(&requeueCountMin, "requeue-count-min", 0, "Minimum requeue count")

	searchCmd.Flags().Int64Var(&ingressBytesMin, "ingress-bytes-min", 0, "Minimum ingress bytes")
	searchCmd.Flags().Int64Var(&ingressBytesMax, "ingress-bytes-max", 0, "Maximum ingress bytes")
	searchCmd.Flags().Int64Var(&referencedBytesMin, "referenced-bytes-min", 0, "Minimum referenced bytes")
	searchCmd.Flags().Int64Var(&referencedBytesMax, "referenced-bytes-max", 0, "Maximum referenced bytes")
	searchCmd.Flags().Int64Var(&totalBytesMin, "total-bytes-min", 0, "Minimum total bytes")
	searchCmd.Flags().Int64Var(&totalBytesMax, "total-bytes-max", 0, "Maximum total bytes")

	contentDeleted.RegisterFlag(searchCmd, "content-deleted", "Filter by content deleted status", "yes", "no")
	egressed.RegisterFlag(searchCmd, "egressed", "Filter by egressed status", "yes", "no")
	errorAcknowledged.RegisterFlag(searchCmd, "error-acknowledged", "Filter by error acknowledged status", "yes", "no")
	filtered.RegisterFlag(searchCmd, "filtered", "Filter by filtered status", "yes", "no")
	paused.RegisterFlag(searchCmd, "paused", "Filter by paused status", "yes", "no")
	pendingAnnotations.RegisterFlag(searchCmd, "pending-annotations", "Filter by pending annotations status", "yes", "no")
	pinned.RegisterFlag(searchCmd, "pinned", "Filter by pinned status", "yes", "no")
	replayable.RegisterFlag(searchCmd, "replayable", "Filter by replayable status", "yes", "no")
	replayed.RegisterFlag(searchCmd, "replayed", "Filter by replayed status", "yes", "no")
	terminalStage.RegisterFlag(searchCmd, "terminal-stage", "Filter by terminal stage status", "yes", "no")
	testMode.RegisterFlag(searchCmd, "test-mode", "Filter by test mode status", "yes", "no")

	searchCmd.RegisterFlagCompletionFunc("data-source", getDataSourceNames)
	searchCmd.RegisterFlagCompletionFunc("transform", getTransformNames)
	searchCmd.RegisterFlagCompletionFunc("data-sink", getDataSinkNames)
	searchCmd.RegisterFlagCompletionFunc("stage", getStageValues)
	searchCmd.RegisterFlagCompletionFunc("topics", getTopicNames)
	searchCmd.RegisterFlagCompletionFunc("annotation", getAnnotationKeys)

	// Register completion for all flags to prevent filesystem completion
	searchCmd.RegisterFlagCompletionFunc("from", cobra.FixedCompletions([]cobra.Completion{
		"today",
		"yesterday",
		"last week",
		"last month",
		"now",
		"beginning",
		"everbefore",
	}, cobra.ShellCompDirectiveNoFileComp))
	searchCmd.RegisterFlagCompletionFunc("to", cobra.FixedCompletions([]cobra.Completion{
		"now",
		"today",
		"yesterday",
		"last week",
		"last month",
		"tomorrow",
		"end",
		"forever",
	}, cobra.ShellCompDirectiveNoFileComp))
	searchCmd.RegisterFlagCompletionFunc("until", cobra.FixedCompletions([]cobra.Completion{
		"now",
		"today",
		"yesterday",
		"last week",
		"last month",
		"tomorrow",
		"end",
		"forever",
	}, cobra.ShellCompDirectiveNoFileComp))
	searchCmd.RegisterFlagCompletionFunc("did", cobra.NoFileCompletions)
	searchCmd.RegisterFlagCompletionFunc("name", cobra.NoFileCompletions)
	searchCmd.RegisterFlagCompletionFunc("filtered-cause", cobra.NoFileCompletions)
	searchCmd.RegisterFlagCompletionFunc("error-cause", cobra.NoFileCompletions)

	searchCmd.MarkFlagsMutuallyExclusive("local", "zulu")
	searchCmd.MarkFlagsMutuallyExclusive("to", "until")
}

// Helper function to safely get string value from pointer
func getStringValue(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// Helper function to format bytes for search
func formatSearchBytes(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(bytes)/float64(div), "KMGTPE"[exp])
}

// Helper function to format time based on timezone preference
func formatTime(t time.Time) string {
	if useHumanize {
		return humanize.Time(t)
	}
	if useZulu {
		return t.UTC().Format(time.RFC3339)
	}
	if useLocal {
		return t.Local().Format("2006-01-02T15:04:05")
	}
	return t.Format(time.RFC3339)
}

// Helper function to parse annotation key-value pairs
func parseAnnotation(annotation string) (string, *string, error) {
	parts := strings.SplitN(annotation, "=", 2)
	if len(parts) == 0 {
		return "", nil, fmt.Errorf("invalid annotation format: %s (expected key or key=value)", annotation)
	}
	key := parts[0]
	if key == "" {
		return "", nil, fmt.Errorf("invalid annotation format: empty key")
	}
	if len(parts) == 1 {
		return key, nil, nil
	}
	value := parts[1]
	return key, &value, nil
}

// Helper function to get stage values for tab completion
func getStageValues(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	return []string{
		"IN_FLIGHT",
		"COMPLETE",
		"ERROR",
		"CANCELLED",
	}, cobra.ShellCompDirectiveNoFileComp
}

// Helper function to get topic names for tab completion
func getTopicNames(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	response, err := graphql.GetAllTopics()
	if err != nil {
		return nil, cobra.ShellCompDirectiveError
	}

	names := make([]string, len(response.GetAllTopics))
	for i, topic := range response.GetAllTopics {
		names[i] = topic.Name
	}
	return names, cobra.ShellCompDirectiveNoFileComp
}

// Helper function to get annotation keys for tab completion
func getAnnotationKeys(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	response, err := graphql.GetAnnotationKeys()
	if err != nil {
		return nil, cobra.ShellCompDirectiveError
	}

	// Append equals sign to each key for tab completion
	keys := make([]string, len(response.AnnotationKeys))
	for i, key := range response.AnnotationKeys {
		keys[i] = key + "="
	}
	return keys, cobra.ShellCompDirectiveNoSpace | cobra.ShellCompDirectiveNoFileComp
}

// Add this helper function at the end of the file, before the last closing brace
func findUUIDInString(s string) string {
	// UUID regex pattern
	uuidPattern := `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}`
	re := regexp.MustCompile(uuidPattern)
	matches := re.FindString(s)
	return matches
}
