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
	"strings"

	"github.com/charmbracelet/lipgloss"
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var (
	// Node state styles
	runningStyle = styles.SuccessStyle
	stoppedStyle = lipgloss.NewStyle().Foreground(styles.Red) // Red
	loopStyle    = lipgloss.NewStyle().Foreground(styles.Blue)
	otherStyle   = lipgloss.NewStyle().Foreground(styles.Yellow)

	// Tree styles
	treeStyle  = lipgloss.NewStyle().Foreground(styles.Blue)
	topicStyle = lipgloss.NewStyle().Foreground(styles.DarkGray)
)

var flowGraphCmd = &cobra.Command{
	Use:     "graph [flowNames...]",
	Short:   "Display a graph of data flow paths",
	Long:    `Display a graph showing all possible paths data can take from one or more data sources through transforms and to data sinks.`,
	Args:    cobra.MinimumNArgs(0),
	GroupID: "flow",
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

		if all {
			names, err := fetchDataSourceNames()
			if err != nil {
				return wrapInError("Error fetching data source names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			fmt.Printf("\nGenerating flow graph for %s:\n", flowName)
			err := displayFlowGraph(cmd, flowName)
			if err != nil {
				fmt.Printf("Error generating graph for %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		flowGraph, err := graphql.GetFlowGraphData("")
		if err != nil {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}

		var sources []string
		for _, flows := range flowGraph.GetFlows {
			for _, source := range flows.GetRestDataSources() {
				sources = append(sources, source.GetName())
			}
			for _, source := range flows.GetTimedDataSources() {
				sources = append(sources, source.GetName())
			}
		}
		return sources, cobra.ShellCompDirectiveNoSpace | cobra.ShellCompDirectiveNoFileComp
	},
}

type flowNode struct {
	name          string
	nodeType      string // "source", "transform", "sink", "topic"
	state         string
	testMode      bool
	enabled       bool
	children      []*flowNode
	visited       bool // Used for cycle detection
	isLastSibling bool
	willRepeat    bool
}

func displayFlowGraph(cmd *cobra.Command, flowName string) error {
	flowGraph, err := graphql.GetFlowGraphData(flowName)
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
	}

	root.children = append(root.children, sourceNode)

	// Track visited nodes to handle cycles
	visitedTopics := make(map[string]*flowNode)
	visitedTransforms := make(map[string]*flowNode)
	visitedSinks := make(map[string]*flowNode)

	// Track topics that will be repeated
	repeatedTopics := make(map[string]bool)
	findRepeatedTopics(flowGraph, sourceTopic, make(map[string]bool), repeatedTopics)

	// Build the graph starting from the source topic
	buildGraphFromTopic(flowGraph, sourceTopic, sourceNode, visitedTopics, visitedTransforms, visitedSinks, repeatedTopics)

	// Print the tree
	printTree(root)
	return nil
}

func findRepeatedTopics(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	visited map[string]bool,
	repeated map[string]bool,
) {
	if visited[topic] {
		repeated[topic] = true
		return
	}
	visited[topic] = true

	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			// Check if transform subscribes to this topic
			subscribesToTopic := false
			for _, sub := range transform.GetSubscribe() {
				if sub.GetTopic() == topic {
					subscribesToTopic = true
					break
				}
			}

			if subscribesToTopic && transform.GetPublish() != nil {
				for _, rule := range transform.GetPublish().GetRules() {
					if rule != nil {
						findRepeatedTopics(flowGraph, rule.GetTopic(), visited, repeated)
					}
				}
			}
		}
	}
}

func buildGraphFromTopic(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	parentNode *flowNode,
	visitedTopics map[string]*flowNode,
	visitedTransforms map[string]*flowNode,
	visitedSinks map[string]*flowNode,
	repeatedTopics map[string]bool,
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
				transformNode := &flowNode{
					name:     transform.GetName(),
					nodeType: "transform",
					state:    flowStateToString(transform.GetFlowStatus().State),
					testMode: transform.GetFlowStatus().TestMode,
					enabled:  isFlowEnabled(transform.GetFlowStatus().State),
				}
				visitedTransforms[transform.GetName()] = transformNode
				transformNodes = append(transformNodes, transformNode)

				// Process transform's published topics
				if transform.GetPublish() != nil {
					for _, rule := range transform.GetPublish().GetRules() {
						if rule != nil {
							buildGraphFromTopic(flowGraph, rule.GetTopic(), transformNode, visitedTopics, visitedTransforms, visitedSinks, repeatedTopics)
						}
					}
				}
			}
		}

		// Find all sinks that subscribe to this topic
		for _, sink := range flows.GetDataSinks() {
			if _, exists := visitedSinks[sink.GetName()]; exists {
				continue
			}

			// Check if sink subscribes to this topic
			subscribesToTopic := false
			for _, sub := range sink.GetSubscribe() {
				if sub.GetTopic() == topic {
					subscribesToTopic = true
					break
				}
			}

			if subscribesToTopic {
				sinkNode := &flowNode{
					name:     sink.GetName(),
					nodeType: "sink",
					state:    flowStateToString(sink.GetFlowStatus().State),
					testMode: sink.GetFlowStatus().TestMode,
					enabled:  isFlowEnabled(sink.GetFlowStatus().State),
				}
				visitedSinks[sink.GetName()] = sinkNode
				sinkNodes = append(sinkNodes, sinkNode)
			}
		}
	}

	// Add transforms and sinks to topic node's children
	topicNode.children = append(topicNode.children, transformNodes...)
	topicNode.children = append(topicNode.children, sinkNodes...)

	// Mark last siblings
	if len(topicNode.children) > 0 {
		topicNode.children[len(topicNode.children)-1].isLastSibling = true
	}
}

func printTree(node *flowNode) {
	fmt.Println()
	printNode(node, "", true, make(map[string]bool))
	fmt.Println()
}

func printNode(node *flowNode, prefix string, isLast bool, visited map[string]bool) {
	if node == nil {
		return
	}

	if node.nodeType != "root" {
		nodeText := formatNode(node)
		if node.visited {
			nodeText = fmt.Sprintf("%s ", nodeText)
		}

		// Don't show connector for source node (first non-root node)
		if node.nodeType == "source" {
			fmt.Printf("%s\n", nodeText)
			// Start children with no prefix for source node
			prefix = ""
		} else {
			fmt.Printf("%s%s%s\n", prefix, getConnector(isLast), nodeText)
		}
	}

	// Don't traverse children for cycle nodes
	if node.visited {
		return
	}

	// Mark as visited for cycle detection
	visited[node.name] = true

	newPrefix := prefix
	if node.nodeType == "source" {
		if isLast {
			newPrefix = ""
		} else {
			newPrefix = treeStyle.Render("│")
		}
	} else {
		if isLast {
			newPrefix += "  "
		} else {
			newPrefix += treeStyle.Render("│") + " "
		}
	}

	for i, child := range node.children {
		printNode(child, newPrefix, i == len(node.children)-1, visited)
	}

	// Unmark as visited when backtracking
	delete(visited, node.name)
}

func getConnector(isLast bool) string {
	if isLast {
		return treeStyle.Render("└─")
	}
	return treeStyle.Render("├─")
}

func getNodeIcon(nodeType string) string {
	switch nodeType {
	case "source":
		return "◡"
	case "transform":
		return "◇"
	case "sink":
		return "▻"
	case "topic":
		return "◎"
	default:
		return "❓"
	}
}

func getStateIcon(state string) string {
	switch strings.ToUpper(state) {
	case "RUNNING":
		return runningStyle.Render("▶")
	case "STOPPED":
		return stoppedStyle.Render("■")
	default:
		return otherStyle.Render("⏸︎")
	}
}

func getTestModeIcon(testMode bool) string {
	if testMode {
		return "🧪"
	}
	return ""
}

func formatNode(node *flowNode) string {
	icon := getNodeIcon(node.nodeType)
	nodeName := node.name

	if node.nodeType == "topic" {
		topicName := topicStyle.Render(nodeName)
		if node.visited {
			return fmt.Sprintf("%s %s %s", icon, topicName, loopStyle.Render("⤴"))
		}
		if node.willRepeat {
			return fmt.Sprintf("%s %s %s", icon, topicName, loopStyle.Render("⬿"))
		}
		return fmt.Sprintf("%s %s", icon, topicName)
	}

	testModeIcon := getTestModeIcon(node.testMode)
	stateIcon := getStateIcon(node.state)

	return fmt.Sprintf("%s %s %s %s", icon, stateIcon, nodeName, testModeIcon)
}

func flowStateToString(state graphql.FlowState) string {
	return string(state)
}

func isFlowEnabled(state graphql.FlowState) bool {
	return state == graphql.FlowStateRunning
}

func init() {
	rootCmd.AddCommand(flowGraphCmd)
	flowGraphCmd.Flags().Bool("all", false, "Generate graphs for all data sources")
}
