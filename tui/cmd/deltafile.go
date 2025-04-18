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
	"time"

	"github.com/charmbracelet/lipgloss"
	"github.com/charmbracelet/lipgloss/list"
	"github.com/charmbracelet/lipgloss/tree"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
)

var (
	deltafileCmd = &cobra.Command{
		Use:   "deltafile",
		Short: "View DeltaFile details",
		Long:  `View detailed information about a DeltaFile, including its flows, actions, and status.`,
		RunE: func(cmd *cobra.Command, args []string) error {
			if len(args) < 1 {
				return fmt.Errorf("deltafile ID is required")
			}
			did, err := uuid.Parse(args[0])
			if err != nil {
				return fmt.Errorf("invalid DeltaFile ID: %v", err)
			}
			return viewDeltaFile(did)
		},
	}
)

func init() {
	rootCmd.AddCommand(deltafileCmd)
}

func viewDeltaFile(did uuid.UUID) error {
	resp, err := graphql.DeltaFile(did)
	if err != nil {
		return fmt.Errorf("failed to fetch DeltaFile: %v", err)
	}

	if resp == nil || resp.DeltaFile == nil {
		return fmt.Errorf("no DeltaFile found with ID: %s", did)
	}

	return displayStructuredView(resp)
}

func formatTimestamp(t time.Time) string {
	return t.Format(time.RFC3339)
}

func formatState(state string) string {
	switch state {
	case "COMPLETE":
		return styles.SuccessStyle.Render(state)
	case "ERROR":
		return styles.ErrorStyle.Render(state)
	case "FILTERED":
		return styles.WarningStyle.Render(state)
	default:
		return state
	}
}

func displayFlowTree(flows []graphql.DeltaFileDeltaFileFlowsDeltaFileFlow) {

	flowDepthMap := make(map[int]*tree.Tree)

	enumeratorStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("63")).MarginRight(1).Bold(true)
	actionEnumeratorStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("64")).MarginRight(1).Bold(true)

	// itemStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("212"))

	t := tree.Root(styles.SubheaderStyle.Render("\nFlow Details")).
		Enumerator(tree.RoundedEnumerator).
		EnumeratorStyle(enumeratorStyle)
		// ItemStyle(itemStyle)

	flowDepthMap[0] = t

	// Process each flow
	for _, flow := range flows {
		// Calculate elapsed time
		elapsed := flow.Modified.Sub(flow.Created)
		var elapsedStr string
		if elapsed.Milliseconds() < 1000 {
			elapsedStr = fmt.Sprintf("%dms", elapsed.Milliseconds())
		} else if elapsed.Seconds() < 60 {
			elapsedStr = fmt.Sprintf("%.1fs", elapsed.Seconds())
		} else {
			elapsedStr = fmt.Sprintf("%.1fm", elapsed.Minutes())
		}

		// Flow header
		flowHeader := fmt.Sprintf("%s (Type: %s, State: %s, Elapsed: %s)",
			flow.Name,
			flow.Type,
			formatState(string(flow.State)),
			elapsedStr)

		flowTree := tree.Root(flowHeader)

		// Test Mode Info
		if flow.TestMode {
			testModeStr := "Test Mode"
			if flow.TestModeReason != nil {
				testModeStr = fmt.Sprintf("%s (%s)", testModeStr, *flow.TestModeReason)
			}
			flowTree.Child(testModeStr)
		}

		// Actions
		if len(flow.Actions) > 0 {
			actionTree := tree.Root(lipgloss.NewStyle().Foreground(lipgloss.Color("64")).Render("Actions")).EnumeratorStyle(actionEnumeratorStyle)
			for _, action := range flow.Actions {
				actionName := action.Name
				if action.Name == "NO_SUBSCRIBERS" {
					actionName = styles.ErrorStyle.Render(action.Name)
				}
				actionText := fmt.Sprintf("%s (Type: %s, State: %s)",
					actionName,
					action.Type,
					formatState(string(action.State)))
				actionTree.Child(actionText)
			}
			flowTree.Child(actionTree)
		}
		flowDepthMap[flow.Depth+1] = flowTree
		flowDepthMap[flow.Depth].Child(flowTree)
	}

	fmt.Print(t)
	fmt.Println()
}

func displayStructuredView(resp *graphql.DeltaFileResponse) error {
	df := resp.DeltaFile

	// Basic Information section
	basicInfoTable := &api.Table{
		// Columns: []string{"Deltafile Details", ""},
		Rows: [][]string{
			{"DID", df.Did.String()},
			{"Name", *df.Name},
			{"Data Source", df.DataSource},
			{"Stage", formatState(string(df.Stage))},
			{"Created", formatTimestamp(df.Created)},
			{"Modified", formatTimestamp(df.Modified)},
			{"Ingress Bytes", formatBytes(df.IngressBytes)},
			{"Referenced Bytes", formatBytes(df.ReferencedBytes)},
			{"Total Bytes", formatBytes(df.TotalBytes)},
		},
	}

	// terminalWidth, _, _ := term.GetSize(int(os.Stdout.Fd()))
	basicInfoTableComponent := components.NewSimpleTable(basicInfoTable) //.Width(terminalWidth)
	fmt.Println(basicInfoTableComponent.Render())

	// Status Flags section
	statusFlagsTable := &api.Table{
		// Columns: []string{"Flags", ""},
		Rows: [][]string{},
	}

	if df.ContentDeleted != nil {
		statusFlagsTable.Rows = append(statusFlagsTable.Rows, []string{"Content Deleted", formatTimestamp(*df.ContentDeleted)})
	}

	if df.Pinned != nil && *df.Pinned {
		statusFlagsTable.Rows = append(statusFlagsTable.Rows, []string{"Pinned"})
	}

	if df.Egressed != nil && *df.Egressed {
		statusFlagsTable.Rows = append(statusFlagsTable.Rows, []string{"Egressed"})
	}

	if df.Filtered != nil && *df.Filtered {
		statusFlagsTable.Rows = append(statusFlagsTable.Rows, []string{"Filtered"})
	}

	if df.Paused != nil && *df.Paused {
		statusFlagsTable.Rows = append(statusFlagsTable.Rows, []string{"Paused"})
	}

	statusFlagsTableComponent := components.NewSimpleTable(statusFlagsTable)
	fmt.Println(statusFlagsTableComponent.Render())

	displayAnnotations(*df)
	displayDeltaFileTable(df.ChildDids, "Child DeltaFiles")
	displayDeltaFileTable(df.ParentDids, "Parent DeltaFiles")
	displayFlowTree(df.Flows)

	contentTags := map[string]bool{}
	hasContentTags := false
	for _, flow := range df.Flows {
		for _, content := range flow.Input.Content {
			if len(content.Tags) > 0 {
				hasContentTags = true
				for _, tag := range content.Tags {
					contentTags[tag] = true
				}
			}
		}
	}

	if hasContentTags {
		fmt.Println(styles.SubheaderStyle.Render("\nContent Tags"))
		fmt.Println()
		tagList := list.New()
		for tag := range contentTags {
			tagList.Items(tag)
		}
		fmt.Println(tagList)
	}

	fmt.Println()
	return nil
}

func displayDeltaFileTable(dids []uuid.UUID, title string) {
	if len(dids) > 0 {
		fmt.Println()
		fmt.Println(styles.SubheaderStyle.PaddingLeft(2).Render(title))
		childTable := &api.Table{
			Columns: []string{"DID", "Filename", "Stage"},
			Rows:    make([][]string, 0),
		}
		for _, did := range dids {
			resp, err := graphql.DeltaFile(did)

			if err != nil || resp == nil || resp.DeltaFile == nil {
				childTable.Rows = append(childTable.Rows, []string{
					did.String(),
					"",
					"NOT FOUND",
				})
			} else {
				childTable.Rows = append(childTable.Rows, []string{
					did.String(),
					*resp.DeltaFile.Name,
					formatState(string(resp.DeltaFile.Stage)),
				})
			}
		}
		table := components.NewSimpleTable(childTable)
		fmt.Println(table.Render())
	}
}

func displayAnnotations(df graphql.DeltaFileDeltaFile) {
	if df.Annotations != nil && len(*df.Annotations) > 0 {

		table := &api.Table{
			Columns: []string{"Annotation Key", "Value"},
			Rows:    make([][]string, 0),
		}

		for k, v := range *df.Annotations {
			table.Rows = append(table.Rows, []string{k, fmt.Sprintf("%v", v)})
		}
		fmt.Println(components.NewSimpleTable(table).Render())
	}

}

func formatBytes(bytes int64) string {
	if bytes < 1024 {
		return fmt.Sprintf("%d B", bytes)
	} else if bytes < 1024*1024 {
		return fmt.Sprintf("%.1f KB", float64(bytes)/1024)
	} else if bytes < 1024*1024*1024 {
		return fmt.Sprintf("%.1f MB", float64(bytes)/(1024*1024))
	} else {
		return fmt.Sprintf("%.1f GB", float64(bytes)/(1024*1024*1024))
	}
}
