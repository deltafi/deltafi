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
	"strconv"
	"strings"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var dataSourceCmd = &cobra.Command{
	Use:                "data-source",
	Short:              "Manage the data sources in DeltaFi",
	Long:               `Manage the data sources in DeltaFi`,
	GroupID:            "flow",
	SilenceUsage:       true,
	DisableSuggestions: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listDataSourceFlows = &cobra.Command{
	Use:          "list",
	Short:        "List data sources",
	Long:         `Get the list of data sources.`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listAllDataSources(cmd)
	},
}

var getDataSourceFlow = &cobra.Command{
	Use:               "get",
	Short:             "Get a data source",
	Long:              `Get the details of the specified data source.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSourceNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return getDataSource(cmd, args[0])
	},
}

var loadRestDataSourceFlow = &cobra.Command{
	Use:   "load-rest",
	Short: "Create or update a REST data source",
	Long: `Creates or update a REST data source with the given input.
If a data source already exists with the same name this will replace it.
Otherwise, this command will create a new REST data source with the given name.`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		var restDataSourcePlan graphql.RestDataSourcePlanInput
		var err = parseFile(cmd, &restDataSourcePlan)
		if err != nil {
			return err
		}

		resp, err := graphql.SaveRestDataSource(restDataSourcePlan)

		if err != nil {
			return wrapInError("Error saving REST data source", err)
		}

		return prettyPrint(cmd, resp)
	},
}

var loadTimedDataSourceFlow = &cobra.Command{
	Use:   "load-timed",
	Short: "Create or update a timed data source",
	Long: `Creates or update a timed data source with the given input.
If a data source already exists with the same name this will replace it.
Otherwise, this command will create a new timed data source with the given name.`,
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		var timedDataSourcePlan graphql.TimedDataSourcePlanInput
		var err = parseFile(cmd, &timedDataSourcePlan)
		if err != nil {
			return err
		}

		resp, err := graphql.SaveTimedDataSource(timedDataSourcePlan)

		if err != nil {
			return wrapInError("Error saving timed data source", err)
		}

		return prettyPrint(cmd, resp)
	},
}

var startDataSourceFlow = &cobra.Command{
	Use:   "start [flowNames...]",
	Short: "Start data sources",
	Long: `Start one or more data sources with the given names.
If --all is specified, starts all data sources, ignoring any explicitly listed flows.
If -a/--all-actions is specified, starts the data source and all connected actions.`,
	ValidArgsFunction: getDataSourceNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")
		allActions, _ := cmd.Flags().GetBool("all-actions")

		if all {
			names, err := fetchRemoteDataSourceNames()
			if err != nil {
				return wrapInError("Error fetching data source names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			// Try REST data source first
			err := startFlow(graphql.FlowTypeRestDataSource, flowName)
			if err == nil {
				if allActions {
					err = startAllConnectedActions(flowName)
					if err != nil {
						fmt.Printf("Error starting connected actions for %s: %v\n", flowName, err)
						lastErr = err
					}
				}
				continue
			}

			// If REST fails, try timed data source
			err = startFlow(graphql.FlowTypeTimedDataSource, flowName)
			if err != nil {
				fmt.Printf("Error starting data source %s: %v\n", flowName, err)
				lastErr = err
			} else if allActions {
				err = startAllConnectedActions(flowName)
				if err != nil {
					fmt.Printf("Error starting connected actions for %s: %v\n", flowName, err)
					lastErr = err
				}
			}
		}
		return lastErr
	},
}

var stopDataSourceFlow = &cobra.Command{
	Use:   "stop [flowNames...]",
	Short: "Stop data sources",
	Long: `Stop one or more data sources with the given names.
If --all is specified, stops all data sources, ignoring any explicitly listed flows.
If -a/--all-actions is specified, stops the data source and all connected actions.`,
	ValidArgsFunction: getDataSourceNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")
		allActions, _ := cmd.Flags().GetBool("all-actions")

		if all {
			names, err := fetchRemoteDataSourceNames()
			if err != nil {
				return wrapInError("Error fetching data source names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			// Try REST data source first
			err := stopFlow(graphql.FlowTypeRestDataSource, flowName)
			if err == nil {
				if allActions {
					err = stopAllConnectedActions(flowName)
					if err != nil {
						fmt.Printf("Error stopping connected actions for %s: %v\n", flowName, err)
						lastErr = err
					}
				}
				continue
			}

			// If REST fails, try timed data source
			err = stopFlow(graphql.FlowTypeTimedDataSource, flowName)
			if err != nil {
				fmt.Printf("Error stopping data source %s: %v\n", flowName, err)
				lastErr = err
			} else if allActions {
				err = stopAllConnectedActions(flowName)
				if err != nil {
					fmt.Printf("Error stopping connected actions for %s: %v\n", flowName, err)
					lastErr = err
				}
			}
		}
		return lastErr
	},
}

var pauseDataSourceFlow = &cobra.Command{
	Use:               "pause [flowName]",
	Short:             "Pause a data source",
	Long:              `Pause a data source with the given name. If -a/--all-actions is specified, pauses the data source and all connected actions.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSourceNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		allActions, _ := cmd.Flags().GetBool("all-actions")

		// Try REST data source first
		err := pauseFlow(graphql.FlowTypeRestDataSource, args[0])
		if err == nil {
			if allActions {
				err = pauseAllConnectedActions(args[0])
				if err != nil {
					return fmt.Errorf("Error pausing connected actions for %s: %v", args[0], err)
				}
			}
			return nil
		}

		// If REST fails, try timed data source
		err = pauseFlow(graphql.FlowTypeTimedDataSource, args[0])
		if err != nil {
			return fmt.Errorf("Error pausing data source %s: %v", args[0], err)
		}
		if allActions {
			err = pauseAllConnectedActions(args[0])
			if err != nil {
				return fmt.Errorf("Error pausing connected actions for %s: %v", args[0], err)
			}
		}
		return nil
	},
}

var setDataSourceTestMode = &cobra.Command{
	Use:               "test-mode",
	Short:             "Enable or disable test mode",
	Long:              `Enable or disable test mode for a given data source.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSourceNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		on, _ := cmd.Flags().GetBool("enable")
		off, _ := cmd.Flags().GetBool("disable")

		if !on && !off {
			fmt.Println("Either --disable or --enable must be set on the test-mode command")
			return fmt.Errorf("Either --disable or --enable must be set")
		}

		if on {
			return enableDataSourceTestMode(args[0])
		} else {
			return disableDataSourceTestMode(args[0])
		}
	},
}

var deleteDataSourceFlow = &cobra.Command{
	Use:               "delete [dataSourceName]",
	Short:             "Delete a data source",
	Long:              `Delete the specified data source if it is removable.`,
	ValidArgsFunction: getDataSourceNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		if len(args) == 0 {
			return cmd.Usage()
		}
		return deleteDataSource(cmd, args[0])
	},
}

// DataSourceError represents an error from data source commands
type DataSourceError struct {
	message string
}

func (e *DataSourceError) Error() string {
	return e.message
}

func newDataSourceError(message string) *DataSourceError {
	return &DataSourceError{message: message}
}

func getDataSource(cmd *cobra.Command, name string) error {
	// Try REST data source first
	restResp, err := graphql.GetRestDataSource(name)
	if err == nil {
		return prettyPrint(cmd, restResp)
	}

	// Try timed data source
	timedResp, err := graphql.GetTimedDataSource(name)
	if err == nil {
		return prettyPrint(cmd, timedResp)
	}

	return wrapInError("Error getting data source "+name, err)
}

func formatDataSourceState(state string) string {
	switch strings.ToUpper(state) {
	case "RUNNING":
		return styles.SuccessStyle.Render(state)
	case "STOPPED":
		return styles.ErrorStyle.Render(state)
	case "PAUSED":
		return styles.WarningStyle.Render(state)
	default:
		return state
	}
}

func listAllDataSources(cmd *cobra.Command) error {
	var resp, err = graphql.ListDataSources()
	if err != nil {
		return wrapInError("Error getting the list of data sources", err)
	}

	var rows [][]string

	// Add REST data sources
	for _, dataSource := range resp.GetAllFlows.GetRestDataSource() {
		actionCounts, _ := countActionsInFlow(dataSource.GetName())
		rows = append(rows, []string{
			dataSource.GetName(),
			"REST",
			formatDataSourceState(string(dataSource.GetFlowStatus().State)),
			strconv.FormatBool(dataSource.GetFlowStatus().TestMode),
			formatActionCounts(actionCounts),
		})
	}

	// Add timed data sources
	for _, dataSource := range resp.GetAllFlows.GetTimedDataSource() {
		actionCounts, _ := countActionsInFlow(dataSource.GetName())
		rows = append(rows, []string{
			dataSource.GetName(),
			"Timed",
			formatDataSourceState(string(dataSource.GetFlowStatus().State)),
			strconv.FormatBool(dataSource.GetFlowStatus().TestMode),
			formatActionCounts(actionCounts),
		})
	}

	sort.Slice(rows, func(i, j int) bool {
		return rows[i][0] < rows[j][0]
	})

	columns := []string{"Name", "Type", "State", "Test Mode", "Downstream"}

	plain, _ := cmd.Flags().GetBool("plain")

	t := api.NewTable(columns, rows)

	renderAsSimpleTable(t, plain)
	return nil
}

func enableDataSourceTestMode(flowName string) error {
	// Try REST data source first
	_, err := graphql.EnableRestDataSourceTestMode(flowName)
	if err == nil {
		fmt.Println("Successfully enabled test mode for REST data source " + flowName)
		return nil
	}

	// Try timed data source
	_, err = graphql.EnableTimedDataSourceTestMode(flowName)
	if err == nil {
		fmt.Println("Successfully enabled test mode for timed data source " + flowName)
		return nil
	}

	return wrapInError("Could not enable test mode for data source "+flowName, err)
}

func disableDataSourceTestMode(flowName string) error {
	// Try REST data source first
	_, err := graphql.DisableRestDataSourceTestMode(flowName)
	if err == nil {
		fmt.Println("Successfully disabled test mode for REST data source " + flowName)
		return nil
	}

	// Try timed data source
	_, err = graphql.DisableTimedDataSourceTestMode(flowName)
	if err == nil {
		fmt.Println("Successfully disabled test mode for timed data source " + flowName)
		return nil
	}

	return wrapInError("Could not disable test mode for data source "+flowName, err)
}

func getDataSourceNames(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	suggestions, err := fetchRemoteDataSourceNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	return suggestions, cobra.ShellCompDirectiveNoFileComp
}

func fetchRemoteDataSourceNames() ([]string, error) {
	var resp, err = graphql.ListDataSources()
	if err != nil {
		return nil, err
	}

	var names []string

	// Add REST data source names
	for _, obj := range resp.GetAllFlows.GetRestDataSource() {
		names = append(names, obj.GetName())
	}

	// Add timed data source names
	for _, obj := range resp.GetAllFlows.GetTimedDataSource() {
		names = append(names, obj.GetName())
	}

	return names, nil
}

func deleteDataSource(cmd *cobra.Command, name string) error {
	// First check if it's a REST data source and get its state
	restResp, err := graphql.GetRestDataSource(name)
	if err == nil {
		if restResp.GetRestDataSource.FlowStatus.State == graphql.FlowStateRunning {
			fmt.Printf("Data source %s is currently running. Do you want to stop it? [y/N] ", name)
			var response string
			fmt.Scanln(&response)
			if response != "y" && response != "Y" {
				return newDataSourceError("Operation cancelled")
			}
		}

		// Stop if running
		if restResp.GetRestDataSource.FlowStatus.State == graphql.FlowStateRunning {
			err := stopFlow(graphql.FlowTypeRestDataSource, name)
			if err != nil {
				return newDataSourceError(fmt.Sprintf("Error stopping REST data source %s: %v", name, err))
			}
		}

		// Now try to delete it
		deleteResp, err := graphql.DeleteRestDataSource(name)
		if err != nil {
			return newDataSourceError(fmt.Sprintf("Error deleting REST data source %s: %v", name, err))
		}
		if !deleteResp.RemoveRestDataSourcePlan {
			return newDataSourceError(fmt.Sprintf("Unable to delete REST data source"))
		}
		fmt.Printf("Successfully deleted REST data source %s\n", name)
		return nil
	}

	// If not a REST data source, try timed data source
	timedResp, err := graphql.GetTimedDataSource(name)
	if err == nil {
		if timedResp.GetTimedDataSource.FlowStatus.State == graphql.FlowStateRunning {
			fmt.Printf("Data source %s is currently running. Do you want to stop it? [y/N] ", name)
			var response string
			fmt.Scanln(&response)
			if response != "y" && response != "Y" {
				return newDataSourceError("Operation cancelled")
			}
		}

		// Stop if running
		if timedResp.GetTimedDataSource.FlowStatus.State == graphql.FlowStateRunning {
			err := stopFlow(graphql.FlowTypeTimedDataSource, name)
			if err != nil {
				return newDataSourceError(fmt.Sprintf("Error stopping timed data source %s: %v", name, err))
			}
		}

		// Now try to delete it
		deleteResp, err := graphql.DeleteTimedDataSource(name)
		if err != nil {
			return newDataSourceError(fmt.Sprintf("Error deleting timed data source %s: %v", name, err))
		}
		if !deleteResp.RemoveTimedDataSourcePlan {
			return newDataSourceError(fmt.Sprintf("Unable to delete timed data source"))
		}
		fmt.Printf("Successfully deleted timed data source %s\n", name)
		return nil
	}

	return newDataSourceError(fmt.Sprintf("Error deleting data source %s: %v", name, err))
}

type actionCounts struct {
	running int
	stopped int
	paused  int
}

func countActionsInFlow(flowName string) (*actionCounts, error) {
	flowGraph, err := graphql.GetFlowGraphData(flowName)
	if err != nil {
		return nil, err
	}

	counts := &actionCounts{}
	visitedTopics := make(map[string]bool)
	visitedTransforms := make(map[string]bool)
	visitedSinks := make(map[string]bool)

	// Find the source node and its topic
	var sourceTopic string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if source.GetName() == flowName {
				sourceTopic = source.GetTopic()
				break
			}
		}
		if sourceTopic == "" {
			for _, source := range flows.GetTimedDataSources() {
				if source.GetName() == flowName {
					sourceTopic = source.GetTopic()
					break
				}
			}
		}
		if sourceTopic != "" {
			break
		}
	}

	if sourceTopic == "" {
		return counts, nil
	}

	// Recursively traverse the flow tree starting from the source topic
	countActionsFromTopic(flowGraph, sourceTopic, counts, visitedTopics, visitedTransforms, visitedSinks)

	return counts, nil
}

func countActionsFromTopic(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	counts *actionCounts,
	visitedTopics map[string]bool,
	visitedTransforms map[string]bool,
	visitedSinks map[string]bool,
) {
	// Skip if we've already visited this topic
	if visitedTopics[topic] {
		return
	}
	visitedTopics[topic] = true

	// Find all transforms that subscribe to this topic
	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			if visitedTransforms[transform.GetName()] {
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
				visitedTransforms[transform.GetName()] = true
				state := transform.GetFlowStatus().State
				switch state {
				case graphql.FlowStateRunning:
					counts.running++
				case graphql.FlowStateStopped:
					counts.stopped++
				case graphql.FlowStatePaused:
					counts.paused++
				}

				// Process transform's published topics
				if transform.GetPublish() != nil {
					for _, rule := range transform.GetPublish().GetRules() {
						if rule != nil {
							countActionsFromTopic(flowGraph, rule.GetTopic(), counts, visitedTopics, visitedTransforms, visitedSinks)
						}
					}
				}
			}
		}

		// Find all sinks that subscribe to this topic
		for _, sink := range flows.GetDataSinks() {
			if visitedSinks[sink.GetName()] {
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
				visitedSinks[sink.GetName()] = true
				state := sink.GetFlowStatus().State
				switch state {
				case graphql.FlowStateRunning:
					counts.running++
				case graphql.FlowStateStopped:
					counts.stopped++
				case graphql.FlowStatePaused:
					counts.paused++
				}
			}
		}
	}
}

func formatActionCounts(counts *actionCounts) string {
	if counts == nil {
		return ""
	}

	running := styles.SuccessStyle.Render(fmt.Sprintf("▶ %d", counts.running))
	stopped := styles.ErrorStyle.Render(fmt.Sprintf("■ %d", counts.stopped))
	paused := styles.WarningStyle.Render(fmt.Sprintf("⏸ %d", counts.paused))

	return fmt.Sprintf("%s %s %s", running, stopped, paused)
}

func startAllConnectedActions(flowName string) error {
	flowGraph, err := graphql.GetFlowGraphData(flowName)
	if err != nil {
		return err
	}

	visitedTopics := make(map[string]bool)
	visitedTransforms := make(map[string]bool)
	visitedSinks := make(map[string]bool)

	// Find the source node and its topic
	var sourceTopic string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if source.GetName() == flowName {
				sourceTopic = source.GetTopic()
				break
			}
		}
		if sourceTopic == "" {
			for _, source := range flows.GetTimedDataSources() {
				if source.GetName() == flowName {
					sourceTopic = source.GetTopic()
					break
				}
			}
		}
		if sourceTopic != "" {
			break
		}
	}

	if sourceTopic == "" {
		return nil
	}

	return startActionsFromTopic(flowGraph, sourceTopic, visitedTopics, visitedTransforms, visitedSinks)
}

func stopAllConnectedActions(flowName string) error {
	flowGraph, err := graphql.GetFlowGraphData(flowName)
	if err != nil {
		return err
	}

	visitedTopics := make(map[string]bool)
	visitedTransforms := make(map[string]bool)
	visitedSinks := make(map[string]bool)

	// Find the source node and its topic
	var sourceTopic string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if source.GetName() == flowName {
				sourceTopic = source.GetTopic()
				break
			}
		}
		if sourceTopic == "" {
			for _, source := range flows.GetTimedDataSources() {
				if source.GetName() == flowName {
					sourceTopic = source.GetTopic()
					break
				}
			}
		}
		if sourceTopic != "" {
			break
		}
	}

	if sourceTopic == "" {
		return nil
	}

	return stopActionsFromTopic(flowGraph, sourceTopic, visitedTopics, visitedTransforms, visitedSinks)
}

func pauseAllConnectedActions(flowName string) error {
	flowGraph, err := graphql.GetFlowGraphData(flowName)
	if err != nil {
		return err
	}

	visitedTopics := make(map[string]bool)
	visitedTransforms := make(map[string]bool)
	visitedSinks := make(map[string]bool)

	// Find the source node and its topic
	var sourceTopic string
	for _, flows := range flowGraph.GetFlows {
		for _, source := range flows.GetRestDataSources() {
			if source.GetName() == flowName {
				sourceTopic = source.GetTopic()
				break
			}
		}
		if sourceTopic == "" {
			for _, source := range flows.GetTimedDataSources() {
				if source.GetName() == flowName {
					sourceTopic = source.GetTopic()
					break
				}
			}
		}
		if sourceTopic != "" {
			break
		}
	}

	if sourceTopic == "" {
		return nil
	}

	return pauseActionsFromTopic(flowGraph, sourceTopic, visitedTopics, visitedTransforms, visitedSinks)
}

func startActionsFromTopic(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	visitedTopics map[string]bool,
	visitedTransforms map[string]bool,
	visitedSinks map[string]bool,
) error {
	// Skip if we've already visited this topic
	if visitedTopics[topic] {
		return nil
	}
	visitedTopics[topic] = true

	// Find all transforms that subscribe to this topic
	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			if visitedTransforms[transform.GetName()] {
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
				visitedTransforms[transform.GetName()] = true
				err := startFlow(graphql.FlowTypeTransform, transform.GetName())
				if err != nil {
					return fmt.Errorf("Error starting transform %s: %v", transform.GetName(), err)
				}

				// Process transform's published topics
				if transform.GetPublish() != nil {
					for _, rule := range transform.GetPublish().GetRules() {
						if rule != nil {
							err = startActionsFromTopic(flowGraph, rule.GetTopic(), visitedTopics, visitedTransforms, visitedSinks)
							if err != nil {
								return err
							}
						}
					}
				}
			}
		}

		// Find all sinks that subscribe to this topic
		for _, sink := range flows.GetDataSinks() {
			if visitedSinks[sink.GetName()] {
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
				visitedSinks[sink.GetName()] = true
				err := startFlow(graphql.FlowTypeDataSink, sink.GetName())
				if err != nil {
					return fmt.Errorf("Error starting sink %s: %v", sink.GetName(), err)
				}
			}
		}
	}
	return nil
}

func stopActionsFromTopic(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	visitedTopics map[string]bool,
	visitedTransforms map[string]bool,
	visitedSinks map[string]bool,
) error {
	// Skip if we've already visited this topic
	if visitedTopics[topic] {
		return nil
	}
	visitedTopics[topic] = true

	// Find all transforms that subscribe to this topic
	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			if visitedTransforms[transform.GetName()] {
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
				visitedTransforms[transform.GetName()] = true
				err := stopFlow(graphql.FlowTypeTransform, transform.GetName())
				if err != nil {
					return fmt.Errorf("Error stopping transform %s: %v", transform.GetName(), err)
				}

				// Process transform's published topics
				if transform.GetPublish() != nil {
					for _, rule := range transform.GetPublish().GetRules() {
						if rule != nil {
							err = stopActionsFromTopic(flowGraph, rule.GetTopic(), visitedTopics, visitedTransforms, visitedSinks)
							if err != nil {
								return err
							}
						}
					}
				}
			}
		}

		// Find all sinks that subscribe to this topic
		for _, sink := range flows.GetDataSinks() {
			if visitedSinks[sink.GetName()] {
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
				visitedSinks[sink.GetName()] = true
				err := stopFlow(graphql.FlowTypeDataSink, sink.GetName())
				if err != nil {
					return fmt.Errorf("Error stopping sink %s: %v", sink.GetName(), err)
				}
			}
		}
	}
	return nil
}

func pauseActionsFromTopic(
	flowGraph *graphql.GetFlowGraphResponse,
	topic string,
	visitedTopics map[string]bool,
	visitedTransforms map[string]bool,
	visitedSinks map[string]bool,
) error {
	// Skip if we've already visited this topic
	if visitedTopics[topic] {
		return nil
	}
	visitedTopics[topic] = true

	// Find all transforms that subscribe to this topic
	for _, flows := range flowGraph.GetFlows {
		for _, transform := range flows.GetTransformFlows() {
			if visitedTransforms[transform.GetName()] {
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
				visitedTransforms[transform.GetName()] = true
				err := pauseFlow(graphql.FlowTypeTransform, transform.GetName())
				if err != nil {
					return fmt.Errorf("Error pausing transform %s: %v", transform.GetName(), err)
				}

				// Process transform's published topics
				if transform.GetPublish() != nil {
					for _, rule := range transform.GetPublish().GetRules() {
						if rule != nil {
							err = pauseActionsFromTopic(flowGraph, rule.GetTopic(), visitedTopics, visitedTransforms, visitedSinks)
							if err != nil {
								return err
							}
						}
					}
				}
			}
		}

		// Find all sinks that subscribe to this topic
		for _, sink := range flows.GetDataSinks() {
			if visitedSinks[sink.GetName()] {
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
				visitedSinks[sink.GetName()] = true
				err := pauseFlow(graphql.FlowTypeDataSink, sink.GetName())
				if err != nil {
					return fmt.Errorf("Error pausing sink %s: %v", sink.GetName(), err)
				}
			}
		}
	}
	return nil
}

func init() {
	rootCmd.AddCommand(dataSourceCmd)

	dataSourceCmd.AddCommand(listDataSourceFlows)
	dataSourceCmd.AddCommand(getDataSourceFlow)
	dataSourceCmd.AddCommand(loadRestDataSourceFlow)
	dataSourceCmd.AddCommand(loadTimedDataSourceFlow)
	dataSourceCmd.AddCommand(startDataSourceFlow)
	dataSourceCmd.AddCommand(stopDataSourceFlow)
	dataSourceCmd.AddCommand(pauseDataSourceFlow)
	dataSourceCmd.AddCommand(setDataSourceTestMode)
	dataSourceCmd.AddCommand(deleteDataSourceFlow)

	startDataSourceFlow.Flags().Bool("all", false, "Start all data sources")
	startDataSourceFlow.Flags().BoolP("all-actions", "a", false, "Start the data source and all connected actions")
	stopDataSourceFlow.Flags().Bool("all", false, "Stop all data sources")
	stopDataSourceFlow.Flags().BoolP("all-actions", "a", false, "Stop the data source and all connected actions")
	pauseDataSourceFlow.Flags().BoolP("all-actions", "a", false, "Pause the data source and all connected actions")

	setDataSourceTestMode.Flags().Bool("enable", false, "Enable test mode")
	setDataSourceTestMode.Flags().Bool("disable", false, "Disable test mode")
}
