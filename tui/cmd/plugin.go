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
	"os"
	"slices"
	"sort"
	"strings"

	"github.com/charmbracelet/lipgloss"
	"github.com/spf13/cobra"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/components"
	"github.com/deltafi/tui/internal/ui/styles"
)

var pluginCmd = &cobra.Command{
	Use:          "plugin",
	Short:        "Plugin management commands",
	Long:         `Plugin management commands`,
	GroupID:      "flow",
	SilenceUsage: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cmd.Help()
		return fmt.Errorf("subcommand is required")
	},
}

var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List installed plugins",
	Long:  `List installed plugins`,
	Run: func(cmd *cobra.Command, args []string) {
		RequireRunningDeltaFi()

		resp, err := graphql.GetPlugins()

		if err != nil {
			panic(err)
		}

		rows := [][]string{}

		for _, plugin := range resp.Plugins {
			//image := ""
			var image strings.Builder
			if plugin.ImageName != nil {
				if plugin.ImageTag != nil {
					image.WriteString(*plugin.ImageName + ":" + *plugin.ImageTag)
				} else {
					image.WriteString(*plugin.ImageName)
				}
			}
			rows = append(rows, []string{
				plugin.DisplayName,
				image.String(),
				plugin.PluginCoordinates.Version,
			})
			sort.Slice(rows, func(i, j int) bool {
				return rows[i][0] < rows[j][0]
			})
		}
		columns := []string{"Name", "Image", "Version"}

		plain := cmd.Flags().Lookup("plain").Value.String() == "true"

		t := api.NewTable(columns, rows)

		renderAsSimpleTable(t, plain)
	},
}

var installCmd = &cobra.Command{
	Use:   "install [flags] <image>",
	Short: "Install one or more plugins",
	Long:  `Install one or more plugins`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		app := app.GetInstance()

		if len(args) < 1 {
			fmt.Println()
			return fmt.Errorf("invalid arguments: %s", app.FormatError("Please specify an image to install"))
		}

		var errored bool = false

		for _, image := range args {

			secret := cmd.Flags().Lookup("secret").Value.String()

			resp, err := graphql.InstallPlugin(image, secret)

			if err != nil {
				fmt.Println("\n         Error: " + app.FormatError(err.Error()))
				fmt.Println(app.FAIL(image))
				errored = true
			} else {
				if !resp.InstallPlugin.Success {
					fmt.Println("\n         Error: " + app.FormatErrors(resp.InstallPlugin.Errors))
					fmt.Println(app.FAIL(image))
					errored = true
				} else {
					fmt.Println(app.OK(image))
				}
			}
		}

		if errored {
			os.Exit(1)
		}

		return nil
	},
}

var uninstallCmd = &cobra.Command{
	Use:   "uninstall [flags] [<name>]",
	Short: "Uninstall one or more plugins",
	Long:  `Uninstall one or more plugins`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		app := app.GetInstance()

		if len(args) < 1 {
			fmt.Println()
			return fmt.Errorf("invalid arguments: %s", app.FormatError("Please specify at least one plugin to uninstall"))
		}

		var errored bool = false
		resp, err := graphql.GetPlugins()

		if err != nil {
			fmt.Println("         Error: " + app.FormatError(err.Error()))
			fmt.Println(app.FAIL("Unable to obtain list of installed plugins"))
			return nil
		}

		var uninstalled = []string{}
		var allPlugins = []string{}

		for _, plugin := range resp.Plugins {
			displayName := plugin.DisplayName
			allPlugins = append(allPlugins, displayName)
			if slices.Contains(args, displayName) {
				// Attempt to uninstall permanent plugin
				if plugin.PluginCoordinates.GroupId == "" || plugin.PluginCoordinates.ArtifactId == "" || plugin.PluginCoordinates.Version == "" {
					fmt.Println("         Error: (" + displayName + ") " + app.FormatError("Cannot determine coordinates of plugin: "+displayName))
					errored = true
					continue
				}

				resp, err := graphql.UninstallPlugin(plugin.PluginCoordinates.GroupId, plugin.PluginCoordinates.ArtifactId, plugin.PluginCoordinates.Version)

				// Command returns an error
				if err != nil {
					fmt.Println("         Error: (" + displayName + ") " + app.FormatError(err.Error()))
					errored = true
					continue
				}

				for _, info := range resp.UninstallPlugin.Info {
					fmt.Println("         " + *info)
				}

				// Uninstall was not successful
				if !resp.UninstallPlugin.Success {
					fmt.Println("         Error: (" + displayName + ") " + app.FormatErrors(resp.UninstallPlugin.Errors))
					errored = true
					continue
				}

				uninstalled = append(uninstalled, displayName)
			}
		}

		for _, name := range args {
			if slices.Contains(uninstalled, name) {
				fmt.Println(app.OK(name + " uninstalled successfully"))
			} else {
				if slices.Contains(allPlugins, name) {
					fmt.Println(app.FAIL("Unable to uninstall " + name))
				} else {
					fmt.Println(app.FAIL("Unable to find plugin named " + name))
				}
				errored = true
			}
		}

		if errored {
			os.Exit(1)
		}

		return nil
	},
}

var describeCmd = &cobra.Command{
	Use:   "describe [flags] <plugin-name> [<action-name>]",
	Short: "Describe a plugin or specific action",
	Long:  `Display detailed information about a specific plugin or action`,
	Args: func(cmd *cobra.Command, args []string) error {
		actionFlag := cmd.Flags().Lookup("action").Value.String() == "true"
		if actionFlag && len(args) != 2 {
			return fmt.Errorf("requires exactly 2 arguments when --action flag is used")
		}
		if !actionFlag && len(args) != 1 {
			return fmt.Errorf("requires exactly 1 argument when --action flag is not used")
		}
		return nil
	},
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		actionFlag := cmd.Flags().Lookup("action").Value.String() == "true"

		resp, err := graphql.GetPlugins()
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		// If we're completing the first argument (plugin name)
		if len(args) == 0 {
			var values []string
			for _, plugin := range resp.Plugins {
				values = append(values, plugin.DisplayName)
			}
			return escapedCompletions(values, toComplete)
		}

		// If we're completing the second argument (action name) and action flag is set
		if len(args) == 1 && actionFlag {
			pluginName := args[0]
			details, err := graphql.GetPluginDetails()
			if err != nil {
				return nil, cobra.ShellCompDirectiveError
			}

			// Find the requested plugin
			var targetPlugin *graphql.GetPluginDetailsPluginsPlugin
			for _, plugin := range details.Plugins {
				if plugin.DisplayName == pluginName {
					targetPlugin = &plugin
					break
				}
			}

			if targetPlugin == nil {
				return nil, cobra.ShellCompDirectiveError
			}

			var actionNames []string
			for _, action := range targetPlugin.Actions {
				actionNames = append(actionNames, action.Name)
			}

			return escapedCompletions(actionNames, toComplete)
		}

		return nil, cobra.ShellCompDirectiveNoFileComp
	},
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		pluginName := args[0]
		actionFlag := cmd.Flags().Lookup("action").Value.String() == "true"
		resp, err := graphql.GetPluginDetails()

		if err != nil {
			return wrapInError("Error fetching plugin details", err)
		}

		// Find the requested plugin
		var targetPlugin *graphql.GetPluginDetailsPluginsPlugin
		for _, plugin := range resp.Plugins {
			if plugin.DisplayName == pluginName {
				targetPlugin = &plugin
				break
			}
		}

		if targetPlugin == nil {
			return fmt.Errorf("plugin %s not found", pluginName)
		}

		verbose := cmd.Flags().Lookup("verbose").Value.String() == "true"

		var image strings.Builder
		if targetPlugin.ImageName != nil {
			if targetPlugin.ImageTag != nil {
				image.WriteString(*targetPlugin.ImageName + ":" + *targetPlugin.ImageTag)
			} else {
				image.WriteString(*targetPlugin.ImageName)
			}
		}

		// If action flag is set, show only the specific action details
		if actionFlag {
			actionName := args[1]
			var targetAction *graphql.GetPluginDetailsPluginsPluginActionsActionDescriptor
			for i := range targetPlugin.Actions {
				if targetPlugin.Actions[i].Name == actionName {
					targetAction = &targetPlugin.Actions[i]
					break
				}
			}

			if targetAction == nil {
				return fmt.Errorf("action %s not found in plugin %s", actionName, pluginName)
			}

			fmt.Printf("Plugin: %s\n", targetPlugin.DisplayName)
			if image.String() != "" {
				fmt.Printf("Image:  %s\n", image.String())
			}
			fmt.Printf("Action: %s\n", targetAction.Name)

			if targetAction.ActionOptions.Description != nil {
				fmt.Printf("\n%s\n", *targetAction.ActionOptions.Description)
			}

			if targetAction.DocsMarkdown != nil {
				fmt.Println()
				fmt.Println(renderMarkdown(*targetAction.DocsMarkdown, getTerminalWidth()-4))
			}

			return nil
		}

		// Print plugin details (existing code)
		fmt.Printf("Name:               %s\n", targetPlugin.DisplayName)
		if targetPlugin.Description != "" {
			fmt.Printf("Description:        %s\n", targetPlugin.Description)
		}
		fmt.Printf("Action Kit Version: %s\n", targetPlugin.ActionKitVersion)

		if image.String() != "" {
			fmt.Printf("Image:              %s\n", image.String())
		}

		// Print actions table
		if len(targetPlugin.Actions) > 0 {
			fmt.Println()
			actionRows := [][]string{}

			for _, action := range targetPlugin.Actions {
				var description string
				actionWidth := 0
				for _, action := range targetPlugin.Actions {
					if actionWidth < len(action.Name) {
						actionWidth = len(action.Name)
					}
				}
				if verbose {
					width := getTerminalWidth() - actionWidth - 2
					if width < 10 {
						width = 10
					}
					if action.DocsMarkdown != nil {
						description = renderMarkdown(*action.DocsMarkdown, width)
					}
				} else {
					description = ""
				}

				if description == "" {
					if action.ActionOptions.Description != nil {
						description = *action.ActionOptions.Description
					} else {
						description = "No description available"
					}
				}

				actionRows = append(actionRows, []string{
					action.Name,
					description,
				})
			}

			// Sort actions by name
			sort.Slice(actionRows, func(i, j int) bool {
				return actionRows[i][0] < actionRows[j][0]
			})

			actionColumns := []string{"Action Name", "Description"}
			t := api.NewTable(actionColumns, actionRows)
			st := components.NewSimpleTable(&t).Verbose(verbose)
			st.Width(getTerminalWidth() + 4)
			if !verbose {
				st.OddRowStyle = lipgloss.NewStyle().Background(styles.Base).Padding(0, 1)
			}
			fmt.Println(st.Render())
		}

		// Print variables table
		if len(targetPlugin.Variables) > 0 {
			fmt.Println()
			variableRows := [][]string{}
			for _, variable := range targetPlugin.Variables {
				value := ""
				if variable.Value != nil {
					value = *variable.Value
				} else {
					if variable.DefaultValue != nil {
						value = *variable.DefaultValue
					}
				}
				variableRows = append(variableRows, []string{
					variable.Name,
					value,
					string(variable.DataType),
					variable.Description,
				})
			}
			sort.Slice(variableRows, func(i, j int) bool {
				return variableRows[i][0] < variableRows[j][0]
			})
			variableColumns := []string{"Variable", "Value", "Type", "Description"}
			t := api.NewTable(variableColumns, variableRows)
			st := components.NewSimpleTable(&t).Width(getTerminalWidth() + 4)
			st.OddRowStyle = lipgloss.NewStyle().Background(styles.Base).Padding(0, 1)
			fmt.Println(st.Render())
		}

		return nil
	},
}

func init() {
	rootCmd.AddCommand(pluginCmd)
	pluginCmd.AddCommand(listCmd)
	pluginCmd.AddCommand(installCmd)
	pluginCmd.AddCommand(uninstallCmd)
	pluginCmd.AddCommand(describeCmd)

	listCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")

	installCmd.Flags().StringP("secret", "s", "", "Image pull secret (optional)")
	describeCmd.Flags().BoolP("verbose", "v", false, "Show verbose output with more details")
	describeCmd.Flags().BoolP("action", "a", false, "Show details for a specific action")
}
