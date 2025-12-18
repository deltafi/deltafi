// ABOUTME: TUI commands for managing auto resume rules
// ABOUTME: Provides list, get, export, load, delete, clear, apply, and dry-run subcommands

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
	"strconv"
	"strings"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/google/uuid"
	"github.com/spf13/cobra"
)

var autoResumeCmd = &cobra.Command{
	Use:     "auto-resume",
	Aliases: []string{"ar"},
	Short:   "Manage auto resume rules in DeltaFi",
	Long:    `Manage the auto resume rules that automatically retry errored DeltaFiles`,
	GroupID: "deltafi",
}

var autoResumeListCmd = &cobra.Command{
	Use:     "list",
	Aliases: []string{"ls"},
	Short:   "List all auto resume rules",
	Long:    `List all auto resume rules in DeltaFi`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return listAutoResumeRules(cmd)
	},
}

var autoResumeGetCmd = &cobra.Command{
	Use:               "get [name-or-id]",
	Short:             "Get an auto resume rule",
	Long:              `Get an auto resume rule by name or ID`,
	ValidArgsFunction: suggestAutoResumeNames,
	Args:              cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		rule, err := getAutoResumeRuleByNameOrID(args[0])
		if err != nil {
			return err
		}
		return prettyPrint(cmd, rule)
	},
}

var autoResumeExportCmd = &cobra.Command{
	Use:   "export",
	Short: "Export all auto resume rules",
	Long:  `Export all auto resume rules from DeltaFi`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return exportAutoResumeRules(cmd)
	},
}

var autoResumeLoadCmd = &cobra.Command{
	Use:   "load [files...]",
	Short: "Load auto resume rules from files",
	Long:  `Load auto resume rules from JSON or YAML files. Supports single rule or array of rules.`,
	Args:  cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return loadAutoResumeRules(cmd, args)
	},
}

var autoResumeDeleteCmd = &cobra.Command{
	Use:               "delete [name-or-id]",
	Aliases:           []string{"rm"},
	Short:             "Delete an auto resume rule",
	Long:              `Delete an auto resume rule by name or ID`,
	ValidArgsFunction: suggestAutoResumeNames,
	Args:              cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		force, _ := cmd.Flags().GetBool("force")
		if !force {
			if !ConfirmPrompt(fmt.Sprintf("Delete auto resume rule %q?", args[0])) {
				return nil
			}
		}
		return deleteAutoResumeRule(args[0])
	},
}

var autoResumeClearCmd = &cobra.Command{
	Use:   "clear",
	Short: "Delete all auto resume rules",
	Long:  `Delete all auto resume rules from DeltaFi`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		force, _ := cmd.Flags().GetBool("force")
		if !force {
			if !ConfirmPrompt("Delete ALL auto resume rules?") {
				return nil
			}
		}
		return clearAutoResumeRules()
	},
}

var autoResumeApplyCmd = &cobra.Command{
	Use:               "apply [names...]",
	Short:             "Apply auto resume rules to existing errors",
	Long:              `Apply the specified auto resume rules to existing errored DeltaFiles`,
	ValidArgsFunction: suggestAutoResumeNamesMulti,
	Args:              cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return applyAutoResumeRules(cmd, args)
	},
}

var autoResumeDryRunCmd = &cobra.Command{
	Use:   "dry-run [file]",
	Short: "Test an auto resume rule without saving",
	Long:  `Test an auto resume rule against existing errors without saving it`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()
		return dryRunAutoResumeRule(cmd, args[0])
	},
}

func listAutoResumeRules(cmd *cobra.Command) error {
	resp, err := graphql.GetAllResumePolicies()
	if err != nil {
		return wrapInError("Error getting auto resume rules", err)
	}

	var rows [][]string
	for _, rule := range resp.GetAllResumePolicies {
		dataSource := "*"
		if ds := rule.GetDataSource(); ds != nil {
			dataSource = *ds
		}
		action := "*"
		if a := rule.GetAction(); a != nil {
			action = *a
		}
		errorSubstring := "*"
		if es := rule.GetErrorSubstring(); es != nil {
			errorSubstring = truncateString(*es, 30)
		}
		priority := "-"
		if p := rule.GetPriority(); p != nil {
			priority = strconv.Itoa(*p)
		}

		rows = append(rows, []string{
			rule.GetName(),
			dataSource,
			action,
			errorSubstring,
			strconv.Itoa(rule.GetMaxAttempts()),
			priority,
			formatBackOff(rule.GetBackOff()),
		})
	}

	columns := []string{"Name", "Data Source", "Action", "Error Match", "Max Tries", "Priority", "Back Off"}
	plain, _ := cmd.Flags().GetBool("plain")
	t := api.NewTable(columns, rows)
	renderAsSimpleTable(t, plain)
	return nil
}

func formatBackOff(backOff graphql.GetAllResumePoliciesGetAllResumePoliciesResumePolicyBackOff) string {
	delay := backOff.GetDelay()
	result := fmt.Sprintf("%ds", delay)

	if maxDelay := backOff.GetMaxDelay(); maxDelay != nil && *maxDelay != delay {
		result += fmt.Sprintf(" (max %ds)", *maxDelay)
	}
	if multiplier := backOff.GetMultiplier(); multiplier != nil && *multiplier > 1 {
		result += fmt.Sprintf(" x%d", *multiplier)
	}
	if random := backOff.GetRandom(); random != nil && *random {
		result += " random"
	}
	return result
}

func truncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}

func exportAutoResumeRules(cmd *cobra.Command) error {
	resp, err := graphql.GetAllResumePolicies()
	if err != nil {
		return wrapInError("Error getting auto resume rules", err)
	}
	return prettyPrint(cmd, resp.GetAllResumePolicies)
}

func loadAutoResumeRules(cmd *cobra.Command, filenames []string) error {
	replaceAll, _ := cmd.Flags().GetBool("replace-all")

	var allPolicies []graphql.ResumePolicyInput
	var lastErr error

	for _, filename := range filenames {
		// Try loading as array first
		var policies []graphql.ResumePolicyInput
		if err := loadFile(filename, &policies); err == nil {
			allPolicies = append(allPolicies, policies...)
			continue
		}

		// Try loading as single policy
		var policy graphql.ResumePolicyInput
		if err := loadFile(filename, &policy); err != nil {
			printLoadError(filename, err)
			lastErr = err
			continue
		}
		allPolicies = append(allPolicies, policy)
	}

	if len(allPolicies) == 0 {
		if lastErr != nil {
			return lastErr
		}
		return newError("No rules to load", "No valid auto resume rules found in the provided files")
	}

	resp, err := graphql.LoadResumePolicies(replaceAll, allPolicies)
	if err != nil {
		return wrapInError("Error loading auto resume rules", err)
	}

	// Check for errors in results
	hasErrors := false
	for i, result := range resp.LoadResumePolicies {
		if !result.GetSuccess() {
			hasErrors = true
			name := "unknown"
			if i < len(allPolicies) {
				name = allPolicies[i].GetName()
			}
			for _, e := range result.GetErrors() {
				if e != nil {
					fmt.Printf("Error loading %s: %s\n", name, *e)
				}
			}
		}
	}

	if !hasErrors {
		fmt.Printf("Successfully loaded %d auto resume rule(s)\n", len(allPolicies))
	}

	return lastErr
}

func deleteAutoResumeRule(nameOrID string) error {
	id, err := resolveAutoResumeID(nameOrID)
	if err != nil {
		return err
	}

	resp, err := graphql.RemoveResumePolicy(id)
	if err != nil {
		return wrapInError("Error deleting auto resume rule", err)
	}

	if resp.RemoveResumePolicy {
		fmt.Printf("Auto resume rule %s deleted\n", nameOrID)
		return nil
	}

	return newError("Rule not deleted", "The auto resume rule "+nameOrID+" was not deleted")
}

func clearAutoResumeRules() error {
	// Load empty array with replaceAll=true to clear all rules
	resp, err := graphql.LoadResumePolicies(true, []graphql.ResumePolicyInput{})
	if err != nil {
		return wrapInError("Error clearing auto resume rules", err)
	}

	// The response should be empty since we loaded no rules
	_ = resp
	fmt.Println("All auto resume rules deleted")
	return nil
}

func applyAutoResumeRules(cmd *cobra.Command, names []string) error {
	resp, err := graphql.ApplyResumePolicies(names)
	if err != nil {
		return wrapInError("Error applying auto resume rules", err)
	}

	result := resp.ApplyResumePolicies
	if !result.GetSuccess() {
		for _, e := range result.GetErrors() {
			if e != nil {
				fmt.Printf("Error: %s\n", *e)
			}
		}
		return newError("Failed to apply rules", "One or more auto resume rules failed to apply")
	}

	for _, info := range result.GetInfo() {
		if info != nil {
			fmt.Println(*info)
		}
	}
	return nil
}

func dryRunAutoResumeRule(cmd *cobra.Command, filename string) error {
	var policy graphql.ResumePolicyInput
	if err := loadFile(filename, &policy); err != nil {
		return wrapInError("Error reading file", err)
	}

	resp, err := graphql.ResumePolicyDryRun(policy)
	if err != nil {
		return wrapInError("Error running dry run", err)
	}

	result := resp.ResumePolicyDryRun
	if !result.GetSuccess() {
		for _, e := range result.GetErrors() {
			if e != nil {
				fmt.Printf("Validation error: %s\n", *e)
			}
		}
		return newError("Dry run failed", "The auto resume rule is not valid")
	}

	for _, info := range result.GetInfo() {
		if info != nil {
			fmt.Println(*info)
		}
	}
	return nil
}

func resolveAutoResumeID(nameOrID string) (uuid.UUID, error) {
	// Try parsing as UUID first
	if id, err := uuid.Parse(nameOrID); err == nil {
		return id, nil
	}

	// Look up by name
	rule, err := getAutoResumeRuleByName(nameOrID)
	if err != nil {
		return uuid.UUID{}, err
	}
	return rule.GetId(), nil
}

func getAutoResumeRuleByNameOrID(nameOrID string) (*graphql.GetResumePolicyGetResumePolicy, error) {
	// Try parsing as UUID first
	if id, err := uuid.Parse(nameOrID); err == nil {
		resp, err := graphql.GetResumePolicy(id)
		if err != nil {
			return nil, wrapInError("Error getting auto resume rule", err)
		}
		if resp.GetResumePolicy == nil {
			return nil, newError("Rule not found", "No auto resume rule found with ID "+nameOrID)
		}
		return resp.GetResumePolicy, nil
	}

	// Look up by name
	return getAutoResumeRuleByName(nameOrID)
}

func getAutoResumeRuleByName(name string) (*graphql.GetResumePolicyGetResumePolicy, error) {
	resp, err := graphql.GetAllResumePolicies()
	if err != nil {
		return nil, wrapInError("Error getting auto resume rules", err)
	}

	for _, rule := range resp.GetAllResumePolicies {
		if rule.GetName() == name {
			// Fetch full details using the ID
			detailResp, err := graphql.GetResumePolicy(rule.GetId())
			if err != nil {
				return nil, wrapInError("Error getting auto resume rule details", err)
			}
			return detailResp.GetResumePolicy, nil
		}
	}

	return nil, newError("Rule not found", "No auto resume rule found with name "+name)
}

func fetchAutoResumeRuleNames() ([]string, error) {
	resp, err := graphql.GetAllResumePolicies()
	if err != nil {
		return nil, err
	}

	names := make([]string, 0, len(resp.GetAllResumePolicies))
	for _, rule := range resp.GetAllResumePolicies {
		names = append(names, rule.GetName())
	}
	return names, nil
}

func suggestAutoResumeNames(_ *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	if len(args) >= 1 {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}

	names, err := fetchAutoResumeRuleNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}

	return filterCompletions(names, toComplete), cobra.ShellCompDirectiveNoFileComp
}

func suggestAutoResumeNamesMulti(_ *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	names, err := fetchAutoResumeRuleNames()
	if err != nil {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}

	// Filter out already-used names
	usedNames := make(map[string]bool)
	for _, arg := range args {
		usedNames[arg] = true
	}

	var suggestions []string
	for _, name := range names {
		if !usedNames[name] {
			suggestions = append(suggestions, name)
		}
	}

	return filterCompletions(suggestions, toComplete), cobra.ShellCompDirectiveNoFileComp
}

func filterCompletions(names []string, toComplete string) []string {
	if toComplete == "" {
		return names
	}

	var filtered []string
	lower := strings.ToLower(toComplete)
	for _, name := range names {
		if strings.HasPrefix(strings.ToLower(name), lower) {
			filtered = append(filtered, name)
		}
	}
	return filtered
}

func init() {
	rootCmd.AddCommand(autoResumeCmd)
	autoResumeCmd.AddCommand(autoResumeListCmd)
	autoResumeCmd.AddCommand(autoResumeGetCmd)
	autoResumeCmd.AddCommand(autoResumeExportCmd)
	autoResumeCmd.AddCommand(autoResumeLoadCmd)
	autoResumeCmd.AddCommand(autoResumeDeleteCmd)
	autoResumeCmd.AddCommand(autoResumeClearCmd)
	autoResumeCmd.AddCommand(autoResumeApplyCmd)
	autoResumeCmd.AddCommand(autoResumeDryRunCmd)

	autoResumeListCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	autoResumeLoadCmd.Flags().BoolP("replace-all", "r", false, "Replace all existing rules")
	autoResumeDeleteCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")
	autoResumeClearCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")
	AddFormatFlag(autoResumeGetCmd)
	AddFormatFlag(autoResumeExportCmd)
}
