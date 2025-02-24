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
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/spf13/cobra"
	"sort"
	"strconv"
)

var transformCmd = &cobra.Command{
	Use:     "transform",
	Short:   "Manage the transform flows in DeltaFi",
	Long:    `Manage the transform flows in DeltaFi`,
	GroupID: "flow",
	Args:    cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		fmt.Println("Unknown subcommand " + args[0])
		return cmd.Usage()
	},
}

var listTransformFlows = &cobra.Command{
	Use:   "list",
	Short: "List transform flows",
	Long:  `Get the list of transform flows.`,
	RunE: func(cmd *cobra.Command, args []string) error {
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
		return validateTransform(cmd, args[0])
	},
}

var startTransformFlow = &cobra.Command{
	Use:               "start",
	Short:             "Start a transform flow",
	Long:              `Start a transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		return startFlow(graphql.FlowTypeTransform, args[0])
	},
}

var stopTransformFlow = &cobra.Command{
	Use:               "stop",
	Short:             "Stop a transform flow",
	Long:              `Stop a transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		return stopFlow(graphql.FlowTypeTransform, args[0])
	},
}

var pauseTransformFlow = &cobra.Command{
	Use:               "pause",
	Short:             "Pause a transform flow",
	Long:              `Pause a transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		return pauseFlow(graphql.FlowTypeTransform, args[0])
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

func getTransformNames(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	suggestions, err := fetchRemoteTransformNames()
	if err != nil {
		// TODO - log this somewhere, how should this be handled?
		//fmt.Println("Error fetching suggestions:", err)
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	return suggestions, cobra.ShellCompDirectiveNoFileComp
}

func fetchRemoteTransformNames() ([]string, error) {
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

func init() {
	rootCmd.AddCommand(transformCmd)
	transformCmd.AddCommand(listTransformFlows)
	transformCmd.AddCommand(getTransformFlow)
	transformCmd.AddCommand(loadTransformFlow)
	transformCmd.AddCommand(startTransformFlow)
	transformCmd.AddCommand(stopTransformFlow)
	transformCmd.AddCommand(pauseTransformFlow)
	transformCmd.AddCommand(validateTransformCmd)

	listTransformFlows.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	AddFormatFlag(getTransformFlow, validateTransformCmd)
	AddLoadFlags(loadTransformFlow)
}
