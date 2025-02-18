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

	"github.com/spf13/cobra"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
)

var pluginCmd = &cobra.Command{
	Use:     "plugin",
	Short:   "Plugin management commands",
	Long:    `Plugin management commands`,
	GroupID: "flow",
}

var listCmd = &cobra.Command{
	Use:   "list",
	Short: "List installed plugins",
	Long:  `List installed plugins`,
	Run: func(cmd *cobra.Command, args []string) {

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

func init() {
	rootCmd.AddCommand(pluginCmd)
	pluginCmd.AddCommand(listCmd)
	pluginCmd.AddCommand(installCmd)
	pluginCmd.AddCommand(uninstallCmd)

	listCmd.Flags().BoolP("plain", "p", false, "Plain output, omitting table borders")

	installCmd.Flags().StringP("secret", "s", "", "Image pull secret (optional)")

}
