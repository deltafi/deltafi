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

var transformCmd = &cobra.Command{
	Use:     "transform",
	Short:   "Manage the transform flows in DeltaFi",
	Long:    `Manage the transform flows in DeltaFi`,
	GroupID: "flow",
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listTransformFlows = &cobra.Command{
	Use:   "list",
	Short: "List transform flows",
	Long:  `Get the list of transform flows.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listAll(cmd)
	},
}

var getTransformFlow = &cobra.Command{
	Use:               "get",
	Short:             "Get a transform flow",
	Long:              `Get the details of the specified transform.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return get(cmd, args[0])
	},
}

var loadTransformFlow = &cobra.Command{
	Use:   "load",
	Short: "Create or update a transform flow",
	Long: `Creates or update a transform flow with the given input.
If the a flow already exists with the same name this will replace the flow.
Otherwise, this command will create a new transform flow with the given name.`,
	Aliases: []string{"transforms"},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		var transformFlowPlan graphql.TransformFlowPlanInput
		var err = parseFile(cmd, &transformFlowPlan)
		if err != nil {
			return err
		}

		resp, err := graphql.SaveTransform(transformFlowPlan)

		if err != nil {
			return wrapInError("Error saving transform", err)
		}

		return prettyPrint(cmd, resp.SaveTransformFlowPlan.TransformFlowFields)
	},
}

var validateTransformCmd = &cobra.Command{
	Use:               "validate",
	Short:             "Validate a transform flow",
	Long:              `Validate the transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return validateTransform(cmd, args[0])
	},
}

var startTransformFlow = &cobra.Command{
	Use:   "start [flowNames...]",
	Short: "Start transform flows",
	Long: `Start one or more transform flows with the given names.
If --all is specified, starts all transform flows, ignoring any explicitly listed flows.`,
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

		if all {
			names, err := fetchTransformNames()
			if err != nil {
				return wrapInError("Error fetching transform names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			err := startFlow(graphql.FlowTypeTransform, flowName)
			if err != nil {
				fmt.Printf("Error starting transform flow %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
}

var stopTransformFlow = &cobra.Command{
	Use:   "stop [flowNames...]",
	Short: "Stop transform flows",
	Long: `Stop one or more transform flows with the given names.
If --all is specified, stops all transform flows, ignoring any explicitly listed flows.`,
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		all, _ := cmd.Flags().GetBool("all")

		if all {
			names, err := fetchTransformNames()
			if err != nil {
				return wrapInError("Error fetching transform names", err)
			}
			args = names
		} else if len(args) == 0 {
			return fmt.Errorf("at least one flow name must be specified when --all is not used")
		}

		var lastErr error
		for _, flowName := range args {
			err := stopFlow(graphql.FlowTypeTransform, flowName)
			if err != nil {
				fmt.Printf("Error stopping transform flow %s: %v\n", flowName, err)
				lastErr = err
			}
		}
		return lastErr
	},
}

var pauseTransformFlow = &cobra.Command{
	Use:               "pause",
	Short:             "Pause a transform flow",
	Long:              `Pause a transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return pauseFlow(graphql.FlowTypeTransform, args[0])
	},
}

var setTestMode = &cobra.Command{
	Use:               "test-mode",
	Short:             "Enable or disable test mode",
	Long:              `Enable or disable test mode for a given transform.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		on, _ := cmd.Flags().GetBool("enable")
		off, _ := cmd.Flags().GetBool("disable")

		if !on && !off {
			fmt.Println("Either --disable or --enable must be set on the test-mode command")
			return cmd.Usage()
		}

		if on {
			return enableTransformTestMode(args[0])
		} else {
			return disableTransformTestMode(args[0])
		}
	},
}

var deleteTransformFlow = &cobra.Command{
	Use:               "delete [transformName]",
	Short:             "Delete a transform",
	Long:              `Delete the specified transform if it is removable.`,
	ValidArgsFunction: getTransformNames,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		if len(args) == 0 {
			return cmd.Usage()
		}
		return deleteTransform(cmd, args[0])
	},
}

func get(cmd *cobra.Command, name string) error {
	var resp, err = graphql.GetTransform(name)

	if err != nil {
		return wrapInError("Error during getting the transform "+name, err)
	}

	return prettyPrint(cmd, resp.GetTransformFlow.TransformFlowFields)
}

func validateTransform(cmd *cobra.Command, name string) error {
	var resp, err = graphql.ValidateTransform(name)

	if err != nil {
		return wrapInError("Error during getting the transform "+name, err)
	}

	return prettyPrint(cmd, resp.ValidateTransformFlow.TransformFlowFields)
}

func listAll(cmd *cobra.Command) error {
	var resp, err = graphql.ListTransforms()
	if err != nil {
		return wrapInError("Error getting the list of transforms", err)
	}

	var rows [][]string

	for _, transform := range resp.GetAllFlows.GetTransform() {
		rows = append(rows, []string{
			transform.GetName(),
			string(transform.GetFlowStatus().State),
			strconv.FormatBool(transform.GetFlowStatus().TestMode),
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

func enableTransformTestMode(flowName string) error {
	_, err := graphql.EnableTransformTestMode(flowName)
	if err != nil {
		return wrapInError("Could not enable test mode for transform "+flowName, err)
	}
	fmt.Println("Successfully enabled test mode for transform " + flowName)
	return nil
}

func disableTransformTestMode(flowName string) error {
	_, err := graphql.DisableTransformTestMode(flowName)
	if err != nil {
		return wrapInError("Could not disable test mode for transform "+flowName, err)
	}
	fmt.Println("Successfully disabled test mode for transform " + flowName)
	return nil
}

func getTransformNames(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	suggestions, err := fetchTransformNames()
	if err != nil {
		// TODO - log this somewhere, how should this be handled?
		//fmt.Println("Error fetching suggestions:", err)
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	return suggestions, cobra.ShellCompDirectiveNoFileComp
}

func fetchTransformNames() ([]string, error) {
	var resp, err = graphql.ListTransforms()
	if err != nil {
		return nil, err
	}

	names := make([]string, len(resp.GetAllFlows.GetTransform()))
	for i, obj := range resp.GetAllFlows.GetTransform() {
		names[i] = obj.GetName()
	}
	return names, nil
}

func deleteTransform(cmd *cobra.Command, name string) error {
	// First check if it exists and get its state
	resp, err := graphql.GetTransform(name)
	if err != nil {
		return newTransformError(fmt.Sprintf("Error getting transform %s: %v", name, err))
	}

	if resp.GetTransformFlow.FlowStatus.State == graphql.FlowStateRunning {
		fmt.Printf("Transform %s is currently running. Do you want to stop it? [y/N] ", name)
		var response string
		fmt.Scanln(&response)
		if response != "y" && response != "Y" {
			return newTransformError("Operation cancelled")
		}

		// Stop if running
		err := stopFlow(graphql.FlowTypeTransform, name)
		if err != nil {
			return newTransformError(fmt.Sprintf("Error stopping transform %s: %v", name, err))
		}
	}

	// Now try to delete it
	deleteResp, err := graphql.DeleteTransform(name)
	if err != nil {
		return newTransformError(fmt.Sprintf("Error deleting transform %s: %v", name, err))
	}
	if !deleteResp.RemoveTransformFlowPlan {
		return newTransformError(fmt.Sprintf("Unable to delete transform"))
	}
	fmt.Printf("Successfully deleted transform %s\n", name)
	return nil
}

// TransformError represents an error from transform commands
type TransformError struct {
	message string
}

func (e *TransformError) Error() string {
	return e.message
}

func newTransformError(message string) *TransformError {
	return &TransformError{message: message}
}

func init() {
	rootCmd.AddCommand(transformCmd)
	transformCmd.AddCommand(listTransformFlows)
	transformCmd.AddCommand(getTransformFlow)
	transformCmd.AddCommand(loadTransformFlow)
	transformCmd.AddCommand(startTransformFlow)
	transformCmd.AddCommand(stopTransformFlow)
	transformCmd.AddCommand(pauseTransformFlow)
	transformCmd.AddCommand(validateTransformCmd)
	transformCmd.AddCommand(setTestMode)
	transformCmd.AddCommand(deleteTransformFlow)

	listTransformFlows.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	setTestMode.Flags().BoolP("enable", "y", false, "Turn on test mode")
	setTestMode.Flags().BoolP("disable", "n", false, "Turn off test mode")
	setTestMode.MarkFlagsMutuallyExclusive("enable", "disable")

	startTransformFlow.Flags().Bool("all", false, "Start all transform flows")
	stopTransformFlow.Flags().Bool("all", false, "Stop all transform flows")

	AddFormatFlag(getTransformFlow, validateTransformCmd)
	AddLoadFlags(loadTransformFlow)
}
