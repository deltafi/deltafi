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

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/spf13/cobra"
)

var dataSinkCmd = &cobra.Command{
	Use:     "data-sink",
	Short:   "Manage the data sinks in DeltaFi",
	Long:    `Manage the data sinks in DeltaFi`,
	GroupID: "flow",
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listDataSinkFlows = &cobra.Command{
	Use:   "list",
	Short: "List data sinks",
	Long:  `Get the list of data sinks.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listAllDataSinks(cmd)
	},
}

var getDataSinkFlow = &cobra.Command{
	Use:               "get",
	Short:             "Get a data sink",
	Long:              `Get the details of the specified data sink.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSinkNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return getDataSink(cmd, args[0])
	},
}

var loadDataSinkFlow = &cobra.Command{
	Use:   "load [files...]",
	Short: "Create or update a data sink",
	Long: `Create or update data sinks from configuration files.

If a sink already exists with the same name, it will be replaced.
Otherwise, a new data sink will be created.

Examples:
  deltafi data-sink load sink.json
  deltafi data-sink load sink1.yaml sink2.yaml
  deltafi data-sink load *.yaml`,
	Args:         cobra.MinimumNArgs(1),
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		var lastErr error
		for _, filename := range args {
			var dataSinkPlan graphql.DataSinkPlanInput
			if err := loadFile(filename, &dataSinkPlan); err != nil {
				printLoadError(filename, err)
				lastErr = err
				continue
			}

			resp, err := graphql.SaveDataSink(dataSinkPlan)
			if err != nil {
				printLoadError(filename, err)
				lastErr = err
				continue
			}

			printLoadSuccess(filename)
			if err := prettyPrint(cmd, resp); err != nil {
				lastErr = err
			}
		}
		return lastErr
	},
}

var startDataSinkFlow = &cobra.Command{
	Use:   "start [flowNames...]",
	Short: "Start data sinks",
	Long: `Start one or more data sinks with the given names.
If --all is specified, starts all data sinks, ignoring any explicitly listed flows.`,
	ValidArgsFunction: getDataSinkNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

		if all {
			names, err := fetchDataSinkNames()
			if err != nil {
				return wrapInError("Error fetching data sink names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			err := startFlow(graphql.FlowTypeDataSink, flowName)
			if err != nil {
				fmt.Printf("Error starting data sink %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
}

var stopDataSinkFlow = &cobra.Command{
	Use:   "stop [flowNames...]",
	Short: "Stop data sinks",
	Long: `Stop one or more data sinks with the given names.
If --all is specified, stops all data sinks, ignoring any explicitly listed flows.`,
	ValidArgsFunction: getDataSinkNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

		if all {
			names, err := fetchDataSinkNames()
			if err != nil {
				return wrapInError("Error fetching data sink names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			err := stopFlow(graphql.FlowTypeDataSink, flowName)
			if err != nil {
				fmt.Printf("Error stopping data sink %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
}

var pauseDataSinkFlow = &cobra.Command{
	Use:               "pause",
	Short:             "Pause a data sink",
	Long:              `Pause a data sink with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSinkNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return pauseFlow(graphql.FlowTypeDataSink, args[0])
	},
}

var setDataSinkTestMode = &cobra.Command{
	Use:               "test-mode",
	Short:             "Enable or disable test mode",
	Long:              `Enable or disable test mode for a given data sink.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSinkNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		on, _ := cmd.Flags().GetBool("enable")
		off, _ := cmd.Flags().GetBool("disable")

		if !on && !off {
			fmt.Println("Either --disable or --enable must be set on the test-mode command")
			return cmd.Usage()
		}

		if on {
			return enableDataSinkTestMode(args[0])
		} else {
			return disableDataSinkTestMode(args[0])
		}
	},
}

var deleteDataSinkFlow = &cobra.Command{
	Use:               "delete [dataSinkName]",
	Short:             "Delete a data sink",
	Long:              `Delete the specified data sink if it is removable.`,
	ValidArgsFunction: getDataSinkNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		if len(args) == 0 {
			return cmd.Usage()
		}
		return deleteDataSink(cmd, args[0])
	},
}

func getDataSink(cmd *cobra.Command, name string) error {
	resp, err := graphql.GetDataSink(name)

	if err != nil {
		return wrapInError("Error getting data sink "+name, err)
	}

	return prettyPrint(cmd, resp)
}

func listAllDataSinks(cmd *cobra.Command) error {
	resp, err := graphql.ListDataSinks()
	if err != nil {
		return wrapInError("Error getting the list of data sinks", err)
	}

	var rows [][]string

	for _, dataSink := range resp.GetAllFlows.GetDataSink() {
		state := string(dataSink.GetFlowStatus().State)
		if !dataSink.GetFlowStatus().Valid {
			state = "INVALID"
		}
		rows = append(rows, []string{
			dataSink.GetName(),
			state,
			strconv.FormatBool(dataSink.GetFlowStatus().TestMode),
		})
		sort.Slice(rows, func(i, j int) bool {
			return rows[i][0] < rows[j][0]
		})
	}
	columns := []string{"Name", "State", "Test Mode"}

	plain, _ := cmd.Flags().GetBool("plain")

	t := api.NewTable(columns, rows)

	renderAsSimpleTable(t, plain)
	return nil
}

func enableDataSinkTestMode(flowName string) error {
	_, err := graphql.EnableDataSinkTestMode(flowName)
	if err != nil {
		return wrapInError("Could not enable test mode for data sink "+flowName, err)
	}
	fmt.Println("Successfully enabled test mode for data sink " + flowName)
	return nil
}

func disableDataSinkTestMode(flowName string) error {
	_, err := graphql.DisableDataSinkTestMode(flowName)
	if err != nil {
		return wrapInError("Could not disable test mode for data sink "+flowName, err)
	}
	fmt.Println("Successfully disabled test mode for data sink " + flowName)
	return nil
}

func getDataSinkNames(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	suggestions, err := fetchDataSinkNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	return suggestions, cobra.ShellCompDirectiveNoFileComp
}

func fetchDataSinkNames() ([]string, error) {
	resp, err := graphql.ListDataSinks()
	if err != nil {
		return nil, err
	}

	names := make([]string, len(resp.GetAllFlows.GetDataSink()))
	for i, obj := range resp.GetAllFlows.GetDataSink() {
		names[i] = obj.GetName()
	}
	return names, nil
}

func deleteDataSink(cmd *cobra.Command, name string) error {
	// First check if it exists and get its state
	resp, err := graphql.GetDataSink(name)
	if err != nil {
		return newDataSinkError(fmt.Sprintf("Error getting data sink %s: %v", name, err))
	}

	if resp.GetDataSink.FlowStatus.State == graphql.FlowStateRunning {
		fmt.Printf("Data sink %s is currently running. Do you want to stop it? [y/N] ", name)
		var response string
		fmt.Scanln(&response)
		if response != "y" && response != "Y" {
			return newDataSinkError("Operation cancelled")
		}

		// Stop if running
		err := stopFlow(graphql.FlowTypeDataSink, name)
		if err != nil {
			return newDataSinkError(fmt.Sprintf("Error stopping data sink %s: %v", name, err))
		}
	}

	// Now try to delete it
	deleteResp, err := graphql.DeleteDataSink(name)
	if err != nil {
		return newDataSinkError(fmt.Sprintf("Error deleting data sink %s: %v", name, err))
	}
	if !deleteResp.RemoveDataSinkPlan {
		return newDataSinkError(fmt.Sprintf("Unable to delete data sink"))
	}
	fmt.Printf("Successfully deleted data sink %s\n", name)
	return nil
}

// DataSinkError represents an error from data sink commands
type DataSinkError struct {
	message string
}

func (e *DataSinkError) Error() string {
	return e.message
}

func newDataSinkError(message string) *DataSinkError {
	return &DataSinkError{message: message}
}

func init() {
	rootCmd.AddCommand(dataSinkCmd)

	dataSinkCmd.AddCommand(listDataSinkFlows)
	dataSinkCmd.AddCommand(getDataSinkFlow)
	dataSinkCmd.AddCommand(loadDataSinkFlow)
	dataSinkCmd.AddCommand(startDataSinkFlow)
	dataSinkCmd.AddCommand(stopDataSinkFlow)
	dataSinkCmd.AddCommand(pauseDataSinkFlow)
	dataSinkCmd.AddCommand(setDataSinkTestMode)
	dataSinkCmd.AddCommand(deleteDataSinkFlow)

	startDataSinkFlow.Flags().Bool("all", false, "Start all data sinks")
	stopDataSinkFlow.Flags().Bool("all", false, "Stop all data sinks")

	setDataSinkTestMode.Flags().Bool("enable", false, "Enable test mode")
	setDataSinkTestMode.Flags().Bool("disable", false, "Disable test mode")

	AddFormatFlag(getDataSinkFlow)
}
