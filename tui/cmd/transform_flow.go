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
	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/spf13/cobra"
	"sort"
	"strconv"
)

var getTransformFlow = &cobra.Command{
	Use:   "transform",
	Short: "Get transform flows",
	Long: `Get the list of transform flows when no argument is given.
When a name is given get the details of the specified transform.`,
	Aliases:           []string{"transforms"},
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {

		var err error
		if len(args) == 0 {
			err = listAll(cmd)
		} else {
			err = get(cmd, args[0])
		}

		return err
	},
}

var loadTransformFlow = &cobra.Command{
	Use:   "transform",
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

		return prettyPrint(cmd, resp.SaveTransformFlowPlan)
	},
}

var startTransformFlow = &cobra.Command{
	Use:               "transform",
	Short:             "Start a transform flow",
	Long:              `Start a transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		return startFlow(graphql.FlowTypeTransform, args[0])
	},
}

var stopTransformFlow = &cobra.Command{
	Use:               "transform",
	Short:             "Stop a transform flow",
	Long:              `Stop a transform flow with the given name.`,
	Args:              cobra.MinimumNArgs(1),
	ValidArgsFunction: getTransformNames,
	RunE: func(cmd *cobra.Command, args []string) error {
		return stopFlow(graphql.FlowTypeTransform, args[0])
	},
}

var pauseTransformFlow = &cobra.Command{
	Use:               "transform",
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

	return prettyPrint(cmd, resp.GetTransformFlow)
}

func listAll(cmd *cobra.Command) error {
	var resp, err = graphql.ListTransforms()
	if err != nil {
		return wrapInError("Error getting the list of transforms", err)
	}

	rows := [][]string{}

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

func getTransformNames(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
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
	GetCmd.AddCommand(getTransformFlow)
	LoadCmd.AddCommand(loadTransformFlow)
	StartCmd.AddCommand(startTransformFlow)
	StopCmd.AddCommand(stopTransformFlow)
	PauseCmd.AddCommand(pauseTransformFlow)
	getTransformFlow.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
}
