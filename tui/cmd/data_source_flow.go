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

var dataSourceCmd = &cobra.Command{
	Use:     "data-source",
	Short:   "Manage the data sources in DeltaFi",
	Long:    `Manage the data sources in DeltaFi`,
	GroupID: "flow",
	Args:    cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		fmt.Println("Unknown subcommand " + args[0])
		return cmd.Usage()
	},
}

var listDataSourceFlows = &cobra.Command{
	Use:   "list",
	Short: "List data sources",
	Long:  `Get the list of data sources.`,
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
If --all is specified, starts all data sources, ignoring any explicitly listed flows.`,
	ValidArgsFunction: getDataSourceNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

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
				continue
			}

			// If REST fails, try timed data source
			err = startFlow(graphql.FlowTypeTimedDataSource, flowName)
			if err != nil {
				fmt.Printf("Error starting data source %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
}

var stopDataSourceFlow = &cobra.Command{
	Use:   "stop [flowNames...]",
	Short: "Stop data sources",
	Long: `Stop one or more data sources with the given names.
If --all is specified, stops all data sources, ignoring any explicitly listed flows.`,
	ValidArgsFunction: getDataSourceNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

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
				continue
			}

			// If REST fails, try timed data source
			err = stopFlow(graphql.FlowTypeTimedDataSource, flowName)
			if err != nil {
				fmt.Printf("Error stopping data source %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
}

var pauseDataSourceFlow = &cobra.Command{
	Use:               "pause [flowName]",
	Short:             "Pause a data source",
	Long:              `Pause a data source with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getDataSourceNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		// Try REST data source first
		err := pauseFlow(graphql.FlowTypeRestDataSource, args[0])
		if err == nil {
			return nil
		}

		// If REST fails, try timed data source
		err = pauseFlow(graphql.FlowTypeTimedDataSource, args[0])
		if err != nil {
			return fmt.Errorf("Error pausing data source %s: %v", args[0], err)
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
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		on, _ := cmd.Flags().GetBool("enable")
		off, _ := cmd.Flags().GetBool("disable")

		if !on && !off {
			fmt.Println("Either --disable or --enable must be set on the test-mode command")
			return cmd.Usage()
		}

		if on {
			return enableDataSourceTestMode(args[0])
		} else {
			return disableDataSourceTestMode(args[0])
		}
	},
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

func listAllDataSources(cmd *cobra.Command) error {
	var resp, err = graphql.ListDataSources()
	if err != nil {
		return wrapInError("Error getting the list of data sources", err)
	}

	var rows [][]string

	// Add REST data sources
	for _, dataSource := range resp.GetAllFlows.GetRestDataSource() {
		rows = append(rows, []string{
			dataSource.GetName(),
			"REST",
			string(dataSource.GetFlowStatus().State),
			strconv.FormatBool(dataSource.GetFlowStatus().TestMode),
		})
	}

	// Add timed data sources
	for _, dataSource := range resp.GetAllFlows.GetTimedDataSource() {
		rows = append(rows, []string{
			dataSource.GetName(),
			"Timed",
			string(dataSource.GetFlowStatus().State),
			strconv.FormatBool(dataSource.GetFlowStatus().TestMode),
		})
	}

	sort.Slice(rows, func(i, j int) bool {
		return rows[i][0] < rows[j][0]
	})

	columns := []string{"Name", "Type", "State", "Test Mode"}

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

	startDataSourceFlow.Flags().Bool("all", false, "Start all data sources")
	stopDataSourceFlow.Flags().Bool("all", false, "Stop all data sources")

	setDataSourceTestMode.Flags().Bool("enable", false, "Enable test mode")
	setDataSourceTestMode.Flags().Bool("disable", false, "Disable test mode")
}
