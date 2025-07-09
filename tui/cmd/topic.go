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
	"bytes"
	"fmt"
	"io"
	"os"
	"sort"
	"strings"

	"github.com/spf13/cobra"

	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/styles"
)

var topicCmd = &cobra.Command{
	Use:   "topic",
	Short: "Manage publish/subscribe topics",
	Long: `Manage and inspect publish/subscribe topics in DeltaFi.

Topics are used for communication between different components in the DeltaFi
system. Publishers send data to topics, and subscribers receive data from topics.
This command allows you to view topic lists and flow relationships.

Examples:
  deltafi topic list                    # List all topics
  deltafi topic flows my-topic          # Show flows using a topic`,
	GroupID:            "flow",
	SilenceUsage:       true,
	DisableSuggestions: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listTopicsCmd = &cobra.Command{
	Use:          "list",
	Short:        "List all topics",
	Long:         `Display a list of all topics in the DeltaFi system with their participant counts.`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listAllTopics(cmd)
	},
}

var topicFlowsCmd = &cobra.Command{
	Use:          "flows [topic-name]",
	Short:        "Show flows using a topic",
	Long:         `Display all flows (both publishers and subscribers) that use a specific topic.`,
	Args:         cobra.ExactArgs(1),
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		values, err := getAllTopicNames()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return showTopicFlows(cmd, args[0])
	},
}

var topicDownstreamCmd = &cobra.Command{
	Use:          "downstream [topic-name]",
	Short:        "Show downstream flows for a topic",
	Long:         `Display a graph of all flows downstream of a specific topic, showing the data flow path just like the graph command for a data source.`,
	Args:         cobra.ExactArgs(1),
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		values, err := getAllTopicNames()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		topicName := args[0]

		_, err := validateTopicExists(topicName)
		if err != nil {
			return err
		}

		return generateDownstreamGraph(topicName, false)
	},
}

var topicUpstreamCmd = &cobra.Command{
	Use:          "upstream [topic-name]",
	Short:        "Show upstream flow paths to a topic",
	Long:         `Display a complete set of graphs showing all possible paths from data sources to a specific topic, including all transforms and intermediate topics. Only shows branches that eventually lead to the specified topic. The graph stops at the target topic and does not show subscribers.`,
	Args:         cobra.ExactArgs(1),
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		values, err := getAllTopicNames()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		topicName := args[0]

		_, err := validateTopicExists(topicName)
		if err != nil {
			return err
		}

		return generateUpstreamGraph(topicName, false)
	},
}

var topicGraphCmd = &cobra.Command{
	Use:          "graph [topic-name]",
	Short:        "Show both upstream and downstream flow graphs for a topic",
	Long:         `Display both the upstream and downstream flow graphs for a specific topic. The upstream graph shows all possible paths from data sources to the topic, and the downstream graph shows all flows downstream of the topic. Each section is clearly delineated in the output.`,
	Args:         cobra.ExactArgs(1),
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		values, err := getAllTopicNames()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		topicName := args[0]

		_, err := validateTopicExists(topicName)
		if err != nil {
			return err
		}

		// Generate upstream graph with border
		if err := generateUpstreamGraph(topicName, true); err != nil {
			return err
		}

		// Generate downstream graph with border
		fmt.Println() // Add spacing between sections
		return generateDownstreamGraph(topicName, true)
	},
}

var topicDataSourcesCmd = &cobra.Command{
	Use:          "data-sources [topic-name]",
	Short:        "List data sources that could send data to a topic",
	Long:         `Display a list of all data sources that could potentially send data to a specific topic through the flow graph. This shows the upstream data sources that can reach the topic via transforms.`,
	Args:         cobra.ExactArgs(1),
	SilenceUsage: true,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		values, err := getAllTopicNames()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(values, toComplete)
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		topicName := args[0]

		_, err := validateTopicExists(topicName)
		if err != nil {
			return err
		}

		return listTopicDataSources(cmd, topicName)
	},
}

func listAllTopics(cmd *cobra.Command) error {
	resp, err := graphql.GetAllTopics()
	if err != nil {
		return wrapInError("Error getting topics", err)
	}

	json, _ := cmd.Flags().GetBool("json")
	plain, _ := cmd.Flags().GetBool("plain")

	if json {
		return printJSON(resp.GetAllTopics, plain)
	}

	if len(resp.GetAllTopics) == 0 {
		fmt.Println("No topics found.")
		return nil
	}

	// Sort topics by name
	topics := resp.GetAllTopics
	sort.Slice(topics, func(i, j int) bool {
		return topics[i].GetName() < topics[j].GetName()
	})

	// Create table rows
	var rows [][]string
	for _, topic := range topics {
		publisherCount := len(topic.GetPublishers())
		subscriberCount := len(topic.GetSubscribers())

		rows = append(rows, []string{
			topic.GetName(),
			fmt.Sprintf("%d", publisherCount),
			fmt.Sprintf("%d", subscriberCount),
		})
	}

	columns := []string{"Topic Name", "Publishers", "Subscribers"}
	t := api.NewTable(columns, rows)

	renderAsSimpleTable(t, plain)
	return nil
}

func showTopicFlows(cmd *cobra.Command, topicName string) error {
	targetTopic, err := findTopicByName(topicName)
	if err != nil {
		return wrapInError("Error getting topics", err)
	}

	if targetTopic == nil {
		return fmt.Errorf("topic '%s' not found", topicName)
	}

	json, _ := cmd.Flags().GetBool("json")
	plain, _ := cmd.Flags().GetBool("plain")

	if json {
		// Combine publishers and subscribers for JSON output
		allFlows := struct {
			Publishers  []graphql.GetAllTopicsGetAllTopicsTopicPublishersTopicParticipant  `json:"publishers"`
			Subscribers []graphql.GetAllTopicsGetAllTopicsTopicSubscribersTopicParticipant `json:"subscribers"`
		}{
			Publishers:  targetTopic.GetPublishers(),
			Subscribers: targetTopic.GetSubscribers(),
		}
		return printJSON(allFlows, plain)
	}

	fmt.Printf("Flows using topic '%s':\n\n", styles.InfoStyle.Bold(true).Render(topicName))

	totalFlows := len(targetTopic.GetPublishers()) + len(targetTopic.GetSubscribers())
	if totalFlows == 0 {
		fmt.Println("No flows found for this topic.")
		return nil
	}

	// Show publishers
	if len(targetTopic.GetPublishers()) > 0 {
		fmt.Printf("Publishers (%d):\n", len(targetTopic.GetPublishers()))
		renderTopicParticipants(targetTopic.GetPublishers(), plain)
		fmt.Println()
	}

	// Show subscribers
	if len(targetTopic.GetSubscribers()) > 0 {
		fmt.Printf("Subscribers (%d):\n", len(targetTopic.GetSubscribers()))
		renderTopicParticipants(targetTopic.GetSubscribers(), plain)
	}

	return nil
}

// renderTopicParticipants renders a table of topic participants (publishers or subscribers)
func renderTopicParticipants(participants interface{}, plain bool) {
	var rows [][]string

	switch p := participants.(type) {
	case []graphql.GetAllTopicsGetAllTopicsTopicPublishersTopicParticipant:
		for _, participant := range p {
			condition := "None"
			if participant.GetCondition() != nil {
				condition = *participant.GetCondition()
			}
			rows = append(rows, []string{
				participant.GetName(),
				string(participant.GetType()),
				formatFlowState(participant.GetState()),
				condition,
			})
		}
	case []graphql.GetAllTopicsGetAllTopicsTopicSubscribersTopicParticipant:
		for _, participant := range p {
			condition := "None"
			if participant.GetCondition() != nil {
				condition = *participant.GetCondition()
			}
			rows = append(rows, []string{
				participant.GetName(),
				string(participant.GetType()),
				formatFlowState(participant.GetState()),
				condition,
			})
		}
	}

	if len(rows) == 0 {
		return
	}

	// Sort by name
	sort.Slice(rows, func(i, j int) bool {
		return rows[i][0] < rows[j][0]
	})

	columns := []string{"Flow Name", "Type", "State", "Condition"}
	t := api.NewTable(columns, rows)

	if plain {
		renderAsSimpleTable(t, plain)
	} else {
		renderAsSimpleTableWithWidth(t, getTerminalWidth())
	}
}

// formatFlowState formats a flow state with appropriate styling
func formatFlowState(state graphql.FlowState) string {
	switch state {
	case graphql.FlowStateRunning:
		return styles.SuccessStyle.Render("RUNNING")
	case graphql.FlowStateStopped:
		return styles.ErrorStyle.Render("STOPPED")
	case graphql.FlowStatePaused:
		return styles.WarningStyle.Render("PAUSED")
	case graphql.FlowStateInvalid:
		return styles.ErrorStyle.Render("INVALID")
	default:
		return styles.InfoStyle.Render(string(state))
	}
}

// canReachTopic checks if a source topic can reach the target topic through transforms
func canReachTopic(flowGraph *graphql.GetFlowGraphResponse, sourceTopic, targetTopic string, visited map[string]bool) bool {
	if sourceTopic == targetTopic {
		return true
	}

	if visited[sourceTopic] {
		return false
	}
	visited[sourceTopic] = true

	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			// Check if transform subscribes to this topic
			subscribesToTopic := false
			for _, sub := range transform.GetSubscribe() {
				if sub.GetTopic() == sourceTopic {
					subscribesToTopic = true
					break
				}
			}

			if subscribesToTopic && transform.GetPublish() != nil {
				for _, rule := range transform.GetPublish().GetRules() {
					if rule != nil && canReachTopic(flowGraph, rule.GetTopic(), targetTopic, visited) {
						return true
					}
				}
			}
		}
	}

	return false
}

// displayFilteredFlowGraph displays a flow graph that only includes paths leading to the target topic
func displayFilteredFlowGraph(flowName, targetTopic string) error {
	flowGraph, err := graphql.GetFlowGraph()
	if err != nil {
		return err
	}

	// Find the source node
	var sourceFlow interface{} // Can be either RestDataSource or TimedDataSource
	var sourceTopic string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if source.GetName() == flowName {
				sourceFlow = source
				sourceTopic = source.GetTopic()
				break
			}
		}
		if sourceFlow == nil {
			for _, source := range flows.GetTimedDataSources() {
				if source.GetName() == flowName {
					sourceFlow = source
					sourceTopic = source.GetTopic()
					break
				}
			}
		}
		if sourceFlow == nil {
			for _, source := range flows.GetOnErrorDataSources() {
				if source.GetName() == flowName {
					sourceFlow = source
					sourceTopic = source.GetTopic()
					break
				}
			}
		}
	}

	if sourceFlow == nil {
		return fmt.Errorf("source node not found for flow %s", flowName)
	}

	// Create root node
	root := &flowNode{
		name:     "Flow Graph",
		nodeType: "root",
	}

	// Create source node
	var sourceNode *flowNode
	switch s := sourceFlow.(type) {
	case *graphql.GetFlowGraphGetFlowsRestDataSourcesRestDataSource:
		sourceNode = &flowNode{
			name:          s.GetName(),
			nodeType:      "source",
			state:         flowStateToString(s.GetFlowStatus().State),
			testMode:      s.GetFlowStatus().TestMode,
			enabled:       isFlowEnabled(s.GetFlowStatus().State),
			isLastSibling: true,
		}
	case *graphql.GetFlowGraphGetFlowsTimedDataSourcesTimedDataSource:
		sourceNode = &flowNode{
			name:          s.GetName(),
			nodeType:      "source",
			state:         flowStateToString(s.GetFlowStatus().State),
			testMode:      s.GetFlowStatus().TestMode,
			enabled:       isFlowEnabled(s.GetFlowStatus().State),
			isLastSibling: true,
		}
	case *graphql.GetFlowGraphGetFlowsOnErrorDataSourcesOnErrorDataSource:
		sourceNode = &flowNode{
			name:          s.GetName(),
			nodeType:      "source",
			state:         flowStateToString(s.GetFlowStatus().State),
			testMode:      s.GetFlowStatus().TestMode,
			enabled:       isFlowEnabled(s.GetFlowStatus().State),
			isLastSibling: true,
		}
	}

	root.children = append(root.children, sourceNode)

	// Track visited nodes to handle cycles
	visitedTopics := make(map[string]*flowNode)
	visitedTransforms := make(map[string]*flowNode)
	visitedSinks := make(map[string]*flowNode)

	// Track topics that will be repeated
	repeatedTopics := make(map[string]bool)
	findRepeatedTopics(flowGraph, sourceTopic, make(map[string]bool), repeatedTopics)

	// Build the filtered graph starting from the source topic
	buildFilteredGraphFromTopic(flowGraph, sourceTopic, sourceNode, visitedTopics, visitedTransforms, visitedSinks, repeatedTopics, targetTopic)

	// Print the tree
	printTree(root)
	return nil
}

// buildFilteredGraphFromTopic builds a graph that only includes paths leading to the target topic
func buildFilteredGraphFromTopic(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	parentNode *flowNode,
	visitedTopics map[string]*flowNode,
	visitedTransforms map[string]*flowNode,
	visitedSinks map[string]*flowNode,
	repeatedTopics map[string]bool,
	targetTopic string,
) {
	// If we've already visited this topic, create a cycle node and stop traversing
	if _, exists := visitedTopics[topic]; exists {
		cycleNode := &flowNode{
			name:     topic,
			nodeType: "topic",
			visited:  true, // Mark as visited to indicate this is a cycle
		}
		parentNode.children = append(parentNode.children, cycleNode)
		return
	}

	// Create topic node
	topicNode := &flowNode{
		name:       topic,
		nodeType:   "topic",
		willRepeat: repeatedTopics[topic], // Mark if this topic will be repeated later
	}
	visitedTopics[topic] = topicNode
	parentNode.children = append(parentNode.children, topicNode)

	// Stop building the graph if we've reached the target topic
	if topic == targetTopic {
		return
	}

	var transformNodes []*flowNode
	var sinkNodes []*flowNode

	// Find all transforms that subscribe to this topic
	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			if _, exists := visitedTransforms[transform.GetName()]; exists {
				continue
			}

			// Check if transform subscribes to this topic
			subscribesToTopic := false
			for _, sub := range transform.GetSubscribe() {
				if sub.GetTopic() == topic {
					subscribesToTopic = true
					break
				}
			}

			if subscribesToTopic {
				// Check if this transform can reach the target topic
				canReachTarget := false
				if transform.GetPublish() != nil {
					for _, rule := range transform.GetPublish().GetRules() {
						if rule != nil && canReachTopic(flowGraph, rule.GetTopic(), targetTopic, make(map[string]bool)) {
							canReachTarget = true
							break
						}
					}
				}

				// Only include transforms that can reach the target topic
				if canReachTarget {
					transformNode := &flowNode{
						name:     transform.GetName(),
						nodeType: "transform",
						state:    flowStateToString(transform.GetFlowStatus().State),
						testMode: transform.GetFlowStatus().TestMode,
						enabled:  isFlowEnabled(transform.GetFlowStatus().State),
					}
					visitedTransforms[transform.GetName()] = transformNode
					transformNodes = append(transformNodes, transformNode)

					// Process transform's published topics (only those that lead to target)
					if transform.GetPublish() != nil {
						for _, rule := range transform.GetPublish().GetRules() {
							if rule != nil && canReachTopic(flowGraph, rule.GetTopic(), targetTopic, make(map[string]bool)) {
								buildFilteredGraphFromTopic(flowGraph, rule.GetTopic(), transformNode, visitedTopics, visitedTransforms, visitedSinks, repeatedTopics, targetTopic)
							}
						}
					}
				}
			}
		}

		// Don't include sinks - stop at the target topic
	}

	// Add transforms and sinks to topic node's children
	topicNode.children = append(topicNode.children, transformNodes...)
	topicNode.children = append(topicNode.children, sinkNodes...)

	// Mark last siblings
	if len(topicNode.children) > 0 {
		topicNode.children[len(topicNode.children)-1].isLastSibling = true
	}
}

// getAllTopicNames returns a slice of all topic names for shell completion
func getAllTopicNames() ([]string, error) {
	resp, err := graphql.GetAllTopics()
	if err != nil {
		return nil, err
	}

	var values []string
	for _, topic := range resp.GetAllTopics {
		values = append(values, topic.GetName())
	}
	return values, nil
}

// findTopicByName searches for a topic by name and returns a pointer to it, or nil if not found
func findTopicByName(topicName string) (*graphql.GetAllTopicsGetAllTopicsTopic, error) {
	resp, err := graphql.GetAllTopics()
	if err != nil {
		return nil, err
	}

	for _, topic := range resp.GetAllTopics {
		if topic.GetName() == topicName {
			return &topic, nil
		}
	}
	return nil, nil
}

// validateTopicExists validates that a topic exists and returns the topic object
func validateTopicExists(topicName string) (*graphql.GetAllTopicsGetAllTopicsTopic, error) {
	targetTopic, err := findTopicByName(topicName)
	if err != nil {
		return nil, wrapInError("Error getting topics", err)
	}
	if targetTopic == nil {
		return nil, fmt.Errorf("topic '%s' not found", topicName)
	}
	return targetTopic, nil
}

// createBorderedSection creates a bordered section with a title and content using lipgloss
func createBorderedSection(title string, content string) string {
	titleStyle := lipgloss.NewStyle().
		Foreground(styles.Blue).
		Bold(true)

	borderStyle := lipgloss.NewStyle().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(styles.Surface2).
		PaddingLeft(2).
		PaddingRight(2)

	// Combine title and content, trimming trailing whitespace
	fullContent := titleStyle.Render(title)
	if content != "" {
		// Trim trailing whitespace and line breaks from content
		trimmedContent := strings.TrimRight(content, " \t\n\r")
		fullContent += "\n" + trimmedContent
	}

	// Apply border to the entire content
	return borderStyle.Render(fullContent)
}

// generateUpstreamGraph generates and displays the upstream graph for a topic
func generateUpstreamGraph(topicName string, withBorder bool) error {
	flowGraph, err := graphql.GetFlowGraph()
	if err != nil {
		return wrapInError("Error getting flow graph", err)
	}

	title := fmt.Sprintf("Upstream flow paths to '%s'", styles.InfoStyle.Bold(true).Render(topicName))

	// Find all data sources that can reach this topic
	var sourceNames []string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if canReachTopic(flowGraph, source.GetTopic(), topicName, make(map[string]bool)) {
				sourceNames = append(sourceNames, source.GetName())
			}
		}
		for _, source := range flows.GetTimedDataSources() {
			if canReachTopic(flowGraph, source.GetTopic(), topicName, make(map[string]bool)) {
				sourceNames = append(sourceNames, source.GetName())
			}
		}
		for _, source := range flows.GetOnErrorDataSources() {
			if canReachTopic(flowGraph, source.GetTopic(), topicName, make(map[string]bool)) {
				sourceNames = append(sourceNames, source.GetName())
			}
		}
	}

	var graphContent strings.Builder
	var lastErr error

	if len(sourceNames) == 0 {
		graphContent.WriteString("No data sources found that can reach this topic.")
	} else {
		// Display filtered graphs for each source that can reach the topic
		for i, sourceName := range sourceNames {
			// Capture the output of displayFilteredFlowGraph
			// We need to temporarily redirect stdout to capture the output
			oldStdout := os.Stdout
			r, w, _ := os.Pipe()
			os.Stdout = w

			err := displayFilteredFlowGraph(sourceName, topicName)
			if err != nil {
				fmt.Printf("Error generating graph for %s: %v\n", sourceName, err)
				lastErr = err
			}

			w.Close()
			os.Stdout = oldStdout

			// Read the captured output
			var buf bytes.Buffer
			io.Copy(&buf, r)
			graphContent.WriteString(buf.String())

			// Add separator between graphs (but not after the last one)
			if i < len(sourceNames)-1 {
				graphContent.WriteString(strings.Repeat("─", 50) + "\n")
			}
		}
	}

	if withBorder {
		fmt.Println(createBorderedSection(title, graphContent.String()))
	} else {
		fmt.Printf("%s:\n", title)
		fmt.Print(graphContent.String())
	}

	return lastErr
}

// generateDownstreamGraph generates and displays the downstream graph for a topic
func generateDownstreamGraph(topicName string, withBorder bool) error {
	flowGraph, err := graphql.GetFlowGraph()
	if err != nil {
		return wrapInError("Error getting flow graph", err)
	}

	title := fmt.Sprintf("Downstream flow graph for topic '%s'", styles.InfoStyle.Bold(true).Render(topicName))

	// Create a root node for the topic
	root := &flowNode{
		name:     topicName,
		nodeType: "topic",
	}

	visitedTopics := make(map[string]*flowNode)
	visitedTransforms := make(map[string]*flowNode)
	visitedSinks := make(map[string]*flowNode)
	repeatedTopics := make(map[string]bool)
	findRepeatedTopics(flowGraph, topicName, make(map[string]bool), repeatedTopics)

	buildGraphFromTopic(flowGraph, topicName, root, visitedTopics, visitedTransforms, visitedSinks, repeatedTopics)

	// Capture the output of printTree
	var graphContent strings.Builder
	oldStdout := os.Stdout
	r, w, _ := os.Pipe()
	os.Stdout = w

	printTree(root)

	w.Close()
	os.Stdout = oldStdout

	// Read the captured output
	io.Copy(&graphContent, r)

	if withBorder {
		fmt.Println(createBorderedSection(title, graphContent.String()))
	} else {
		fmt.Printf("%s:\n", title)
		fmt.Print(graphContent.String())
	}

	return nil
}

// listTopicDataSources lists all data sources that could send data to a specified topic
func listTopicDataSources(cmd *cobra.Command, topicName string) error {
	flowGraph, err := graphql.GetFlowGraph()
	if err != nil {
		return wrapInError("Error getting flow graph", err)
	}

	// Find all data sources that can reach this topic
	var sourceNames []string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if canReachTopic(flowGraph, source.GetTopic(), topicName, make(map[string]bool)) {
				sourceNames = append(sourceNames, source.GetName())
			}
		}
		for _, source := range flows.GetTimedDataSources() {
			if canReachTopic(flowGraph, source.GetTopic(), topicName, make(map[string]bool)) {
				sourceNames = append(sourceNames, source.GetName())
			}
		}
		for _, source := range flows.GetOnErrorDataSources() {
			if canReachTopic(flowGraph, source.GetTopic(), topicName, make(map[string]bool)) {
				sourceNames = append(sourceNames, source.GetName())
			}
		}
	}

	json, _ := cmd.Flags().GetBool("json")
	plain, _ := cmd.Flags().GetBool("plain")

	if json {
		dataSources := struct {
			TopicName   string   `json:"topic_name"`
			DataSources []string `json:"data_sources"`
			Count       int      `json:"count"`
		}{
			TopicName:   topicName,
			DataSources: sourceNames,
			Count:       len(sourceNames),
		}
		return printJSON(dataSources, plain)
	}

	if plain {
		for _, sourceName := range sourceNames {
			fmt.Println(sourceName)
		}
		return nil
	}

	// Sort data source names for consistent output
	sort.Strings(sourceNames)

	fmt.Printf("Data sources that could send data to topic '%s':\n\n", styles.InfoStyle.Bold(true).Render(topicName))

	// Create table rows
	var rows [][]string
	for _, sourceName := range sourceNames {
		rows = append(rows, []string{sourceName})
	}

	columns := []string{"Data Source Name"}
	t := api.NewTable(columns, rows)

	renderAsSimpleTable(t, plain)

	return nil
}

func init() {
	rootCmd.AddCommand(topicCmd)
	topicCmd.AddCommand(listTopicsCmd)
	topicCmd.AddCommand(topicFlowsCmd)

	topicCmd.AddCommand(topicDownstreamCmd)
	topicCmd.AddCommand(topicUpstreamCmd)
	topicCmd.AddCommand(topicGraphCmd)
	topicCmd.AddCommand(topicDataSourcesCmd)

	listTopicsCmd.Flags().BoolP("json", "j", false, "Output in JSON format")
	listTopicsCmd.Flags().BoolP("plain", "p", false, "Plain output format")
	topicFlowsCmd.Flags().BoolP("json", "j", false, "Output in JSON format")
	topicFlowsCmd.Flags().BoolP("plain", "p", false, "Plain output format")
	topicDataSourcesCmd.Flags().BoolP("json", "j", false, "Output in JSON format")
	topicDataSourcesCmd.Flags().BoolP("plain", "p", false, "Plain output format")

}
