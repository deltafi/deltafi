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
	"strings"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var systemPropertiesCmd = &cobra.Command{
	Use:     "properties",
	Short:   "Manage DeltaFi system properties",
	Long:    `View and modify DeltaFi system properties`,
	GroupID: "deltafi",
}

var listPropertiesCmd = &cobra.Command{
	Use:   "list",
	Short: "List system properties",
	Long:  `List all system properties`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		resp, err := graphql.GetPropertySets()
		if err != nil {
			return wrapInError("Error fetching property sets", err)
		}

		plain, _ := cmd.Flags().GetBool("plain")
		verbose, _ := cmd.Flags().GetBool("verbose")

		for _, set := range resp.GetPropertySets {
			// Create table for each property set
			table := &api.Table{
				Columns: []string{"Key", "Value"},
				Rows:    make([][]string, 0),
			}

			if verbose {
				table.Columns = []string{"Key", "Override", "Default", "Description"}
			}

			// Add properties to table
			type property struct {
				Key          string
				Value        string
				DefaultValue string
				Description  string
				Source       graphql.PropertySource
				DataType     graphql.DataType
			}
			props := make([]property, 0)
			for _, prop := range set.Properties {
				value := ""
				if prop.Value != nil {
					value = *prop.Value
				}
				defaultValue := ""
				if prop.DefaultValue != nil {
					defaultValue = *prop.DefaultValue
				}
				description := prop.Description
				source := prop.PropertySource
				dataType := prop.DataType
				props = append(props, property{prop.Key, value, defaultValue, description, source, dataType})
			}

			// Sort properties by key
			sort.Slice(props, func(i, j int) bool {
				return props[i].Key < props[j].Key
			})

			for _, prop := range props {
				value := prop.Value
				if prop.DataType == graphql.DataTypeBoolean {
					if value == "true" {
						value = styles.SuccessStyle.Bold(true).Render("✓")
					} else if value == "false" {
						value = styles.ErrorStyle.Bold(true).Render("✗")
					}
				}
				if prop.DataType == graphql.DataTypeNumber {
					value = styles.ItalicStyle.Render(value)
				}

				if verbose && prop.Source != graphql.PropertySourceCustom {
					value = ""
				}
				row := []string{prop.Key, value}
				if verbose {
					defaultValue := prop.DefaultValue
					description := prop.Description

					// Always show default value
					if prop.DataType == graphql.DataTypeBoolean {
						if defaultValue == "true" {
							defaultValue = styles.SuccessStyle.Bold(true).Render("✓")
						} else if defaultValue == "false" {
							defaultValue = styles.ErrorStyle.Bold(true).Render("✗")
						}
					}

					// Only show override value for CUSTOM properties
					if prop.Source != graphql.PropertySourceCustom {
						value = "" // Show nothing for non-CUSTOM properties
					}

					row = append(row, defaultValue, description)
				}
				table.Rows = append(table.Rows, row)
			}

			// Create and render table
			simpleTable := components.NewSimpleTable(table)
			if !plain {
				width := getTerminalWidth()
				simpleTable = simpleTable.Width(width)
			}
			if verbose {
				simpleTable = simpleTable.Verbose(true)
				simpleTable.OddRowStyle = simpleTable.BaseStyle.Foreground(styles.Subtext0)
			}
			if plain {
				fmt.Println(simpleTable.RenderPlain())
			} else {
				fmt.Println(simpleTable.Render())
			}
			fmt.Println() // Add spacing between tables
		}

		return nil
	},
}

var setPropertyCmd = &cobra.Command{
	Use:   "set [key] [value]",
	Short: "Set a system property",
	Long:  `Set a system property to the specified value`,
	Args:  cobra.ExactArgs(2),
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		if len(args) > 0 {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}

		resp, err := graphql.GetPropertySets()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		var keys []string
		for _, set := range resp.GetPropertySets {
			for _, prop := range set.Properties {
				keys = append(keys, prop.Key)
			}
		}

		// Sort keys for consistent completion
		sort.Strings(keys)

		// Filter keys based on what user has typed
		var matches []string
		for _, key := range keys {
			if strings.HasPrefix(key, toComplete) {
				matches = append(matches, key)
			}
		}

		return matches, cobra.ShellCompDirectiveNoFileComp
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		key := args[0]
		value := args[1]

		updates := []graphql.KeyValueInput{{
			Key:   key,
			Value: &value,
		}}

		resp, err := graphql.UpdateProperties(updates)
		if err != nil {
			return wrapInError("Error updating property", err)
		}
		if resp.UpdateProperties {
			fmt.Printf("Successfully set property %s to %s\n", key, value)
		} else {
			return fmt.Errorf("failed to set property %s", key)
		}
		return nil
	},
}

var getPropertyCmd = &cobra.Command{
	Use:   "get [key]",
	Short: "Get a system property value",
	Long:  `Get the value of a system property by its key`,
	Args:  cobra.ExactArgs(1),
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		if len(args) > 0 {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}

		resp, err := graphql.GetPropertySets()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		var keys []string
		for _, set := range resp.GetPropertySets {
			for _, prop := range set.Properties {
				keys = append(keys, prop.Key)
			}
		}

		// Sort keys for consistent completion
		sort.Strings(keys)

		// Filter keys based on what user has typed
		var matches []string
		for _, key := range keys {
			if strings.HasPrefix(key, toComplete) {
				matches = append(matches, key)
			}
		}

		return matches, cobra.ShellCompDirectiveNoFileComp
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		key := args[0]

		resp, err := graphql.GetPropertySets()
		if err != nil {
			return wrapInError("Error fetching property sets", err)
		}

		// Search for the property across all sets
		for _, set := range resp.GetPropertySets {
			for _, prop := range set.Properties {
				if prop.Key == key {
					if prop.Value != nil {
						fmt.Println(*prop.Value)
					} else {
						fmt.Println("")
					}
					return nil
				}
			}
		}

		return fmt.Errorf("property %s not found", key)
	},
}

func init() {
	rootCmd.AddCommand(systemPropertiesCmd)
	systemPropertiesCmd.AddCommand(listPropertiesCmd)
	systemPropertiesCmd.AddCommand(setPropertyCmd)
	systemPropertiesCmd.AddCommand(getPropertyCmd)

	// Add flags
	listPropertiesCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")
	listPropertiesCmd.Flags().BoolP("verbose", "v", false, "Include default and description columns")
}
