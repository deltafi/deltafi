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
	"github.com/google/uuid"
	"github.com/spf13/cobra"
	"sort"
	"strconv"
)

var deletePoliciesCmd = &cobra.Command{
	Use:     "delete-policies",
	Aliases: []string{"policies"},
	Short:   "Manage the delete policies in DeltaFi",
	Long:    `Manage the delete policies in DeltaFi`,
	GroupID: "deltafi",
}

var deletePoliciesListCmd = &cobra.Command{
	Use:     "list",
	Short:   "List the delete policies in DeltaFi",
	Long:    `List the delete policies in DeltaFi`,
	Aliases: []string{"ls"},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listDeletePolicies(cmd)
	},
}

var importDeletePoliciesCmd = &cobra.Command{
	Use:   "import",
	Short: "Import delete policies in DeltaFi",
	Long:  `Import delete policies in DeltaFi`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return importDeletePolicies(cmd)
	},
}

var exportDeletePoliciesCmd = &cobra.Command{
	Use:   "export",
	Short: "Export delete policies from DeltaFi",
	Long:  `Export delete policies from DeltaFi`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return exportDeletePolicies(cmd)
	},
}

var updateDeletePoliciesCmd = &cobra.Command{
	Use:   "update",
	Short: "Update a delete policy in DeltaFi",
	Long:  `Update a delete policy using the values in the provided file`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return loadDeletePolicy(cmd)
	},
}

var getDeletePoliciesCmd = &cobra.Command{
	Use:               "get [policyName]",
	Short:             "Get a delete policy in DeltaFi",
	Long:              `Get a delete policy with the given name in DeltaFi`,
	ValidArgsFunction: suggestNames(IncludeAll),
	Args:              cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		policy, err := getPolicyByName(args[0])
		if err != nil {
			return err
		}

		return prettyPrint(cmd, policy)
	},
}

var startDeletePolicyCmd = &cobra.Command{
	Use:               "start [policyName]",
	Short:             "Start a delete policy in DeltaFi",
	Long:              `Start a delete policy with the given name in DeltaFi`,
	ValidArgsFunction: suggestNames(IncludeDisabled),
	Args:              cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		name := args[0]
		return toggleDeletePolicy(true, name)
	},
}

var stopDeletePolicyCmd = &cobra.Command{
	Use:               "stop [policyName]",
	Short:             "Stop a delete policy in DeltaFi",
	Long:              `Stop a delete policy with the given name in DeltaFi`,
	ValidArgsFunction: suggestNames(IncludeEnabled),
	Args:              cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		name := args[0]
		return toggleDeletePolicy(false, name)
	},
}

var removeDeletePolicyCmd = &cobra.Command{
	Use:               "delete [policyName]",
	Short:             "Remove a delete policy from DeltaFi",
	Long:              `Remove the delete policy with the given name from DeltaFi`,
	ValidArgsFunction: suggestNames(IncludeAll),
	Args:              cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return removeDeletePolicy(args[0])
	},
}

func listDeletePolicies(cmd *cobra.Command) error {
	var resp, err = graphql.GetDeletePolicies()
	if err != nil {
		return wrapInError("Error getting the list of delete policies", err)
	}

	var rows [][]string

	for _, policy := range resp.GetDeletePolicies {
		dataSource := "All"
		if f := policy.GetFlow(); f != nil {
			dataSource = *f
		}
		rows = append(rows, []string{
			policy.GetId().String(),
			policy.GetName(),
			dataSource,
			strconv.FormatBool(policy.GetEnabled()),
		})
		sort.Slice(rows, func(i, j int) bool {
			return rows[i][0] < rows[j][0]
		})
	}
	columns := []string{"ID", "Name", "Data Source", "Enabled"}

	plain, _ := cmd.Flags().GetBool("plain")

	t := api.NewTable(columns, rows)

	renderAsSimpleTable(t, plain)
	return nil
}

func exportDeletePolicies(cmd *cobra.Command) error {
	var resp, err = graphql.ExportDeletePolicies()

	if err != nil {
		return wrapInError("Error getting the delete policies", err)
	}

	return prettyPrint(cmd, resp.ExportDeletePolicies)
}

func importDeletePolicies(cmd *cobra.Command) error {
	replaceAll, _ := cmd.Flags().GetBool("replace-all")

	var policies graphql.DeletePoliciesInput
	var err = parseFile(cmd, &policies)
	if err != nil {
		return err
	}

	resp, err := graphql.ImportDeletePolicies(replaceAll, policies)
	if err != nil {
		return wrapInError("Error importing delete policies", err)
	}

	return prettyPrint(cmd, resp)
}

func loadDeletePolicy(cmd *cobra.Command) error {
	var policy graphql.TimedDeletePolicyInput
	var err = parseFile(cmd, &policy)
	if err != nil {
		return err
	}

	resp, err := graphql.LoadTimedDeletePolicy(policy)
	if err != nil {
		return wrapInError("Error loading delete policy", err)
	}

	return prettyPrint(cmd, resp)
}

func toggleDeletePolicy(enabled bool, name string) error {
	id, err := getIDByName(name)
	if err != nil {
		return err
	}

	resp, err := graphql.ToggleDeletePolicy(enabled, id)

	if err != nil {
		verb := "disabling"
		if enabled {
			verb = "enabling"
		}
		return wrapInError("Error "+verb+" the delete policy "+name+" ("+id.String()+")", err)
	}

	state := "disabled"
	if enabled {
		state = "enabled"
	}
	if resp.EnablePolicy {
		fmt.Println("Delete policy " + name + " (" + id.String() + ") is now " + state)
	} else {
		fmt.Println("Delete policy " + name + " (" + id.String() + ") did not change")
	}
	return nil
}

func removeDeletePolicy(name string) error {
	id, err := getIDByName(name)
	if err != nil {
		return err
	}

	resp, err := graphql.RemoveDeletePolicy(id)

	if err != nil {
		return wrapInError("Error removing the delete policy "+name+" ("+id.String()+")", err)
	}

	if resp.RemoveDeletePolicy {
		fmt.Println("Delete policy " + name + " (" + id.String() + ") was removed")
		return nil
	}

	return newError("Delete policy was not removed", "The delete policy "+name+" ("+id.String()+") was not removed")
}

type IncludeMode int

const (
	IncludeEnabled = iota
	IncludeDisabled
	IncludeAll
)

func suggestNames(includeMode IncludeMode) func(*cobra.Command, []string, string) ([]string, cobra.ShellCompDirective) {
	return func(_ *cobra.Command, args []string, _ string) ([]string, cobra.ShellCompDirective) {
		if len(args) >= 1 {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}

		suggestions, err := getNameSuggestions(includeMode)
		if err != nil {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}
		return suggestions, cobra.ShellCompDirectiveNoFileComp
	}
}

func getNameSuggestions(includeMode IncludeMode) ([]string, error) {
	policies, err := fetchRemoteDeletePolicyNames()
	if err != nil {
		return nil, err
	}

	matchEnabled := includeMode == IncludeEnabled
	names := make([]string, 0, len(policies))
	for _, policy := range policies {
		if includeMode == IncludeAll || matchEnabled == policy.GetEnabled() {
			names = append(names, policy.GetName())
		}
	}
	return names, nil
}

func getIDByName(name string) (uuid.UUID, error) {
	id, err := getPolicyByName(name)
	if err != nil {
		return uuid.UUID{}, err
	}
	return id.GetId(), nil
}

func getPolicyByName(policyName string) (graphql.GetDeletePoliciesGetDeletePoliciesDeletePolicy, error) {
	policies, err := fetchRemoteDeletePolicyNames()
	if err != nil {
		return nil, err
	}

	for _, policy := range policies {
		if policy.GetName() == policyName {
			return policy, nil
		}
	}

	return nil, newError("Missing delete policy", "Could not find delete policy named"+policyName)
}

func fetchRemoteDeletePolicyNames() ([]graphql.GetDeletePoliciesGetDeletePoliciesDeletePolicy, error) {
	var resp, err = graphql.GetDeletePolicies()
	if err != nil {
		return nil, err
	}

	return resp.GetDeletePolicies, nil
}

func init() {
	rootCmd.AddCommand(deletePoliciesCmd)
	deletePoliciesCmd.AddCommand(deletePoliciesListCmd)
	deletePoliciesCmd.AddCommand(exportDeletePoliciesCmd)
	deletePoliciesCmd.AddCommand(importDeletePoliciesCmd)
	deletePoliciesCmd.AddCommand(updateDeletePoliciesCmd)
	deletePoliciesCmd.AddCommand(startDeletePolicyCmd)
	deletePoliciesCmd.AddCommand(stopDeletePolicyCmd)
	deletePoliciesCmd.AddCommand(removeDeletePolicyCmd)
	deletePoliciesCmd.AddCommand(getDeletePoliciesCmd)

	importDeletePoliciesCmd.Flags().BoolP("replace-all", "r", false, "Replace all existing policies")
	deletePoliciesListCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	AddLoadFlags(importDeletePoliciesCmd)
	AddLoadFlags(updateDeletePoliciesCmd)
	AddFormatFlag(getDeletePoliciesCmd)
	AddFormatFlag(exportDeletePoliciesCmd)
}
