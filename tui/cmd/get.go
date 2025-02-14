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
	"encoding/json"
	"fmt"
	"github.com/spf13/cobra"
	"sigs.k8s.io/yaml"
)

var GetCmd = &cobra.Command{
	Use:   "get",
	Short: "Get the resources of a given type",
	Long: `Get the resources loaded in DeltaFi For example:

# Get a specific resource by name
deltafi2 get transform passthrough-transform

# Get a list of the resources by resource type
deltafi2 get transforms
`,
	Args: cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		fmt.Println("There are no resources of type " + args[0])
		return cmd.Usage()
	},
}

func init() {
	rootCmd.AddCommand(GetCmd)
	GetCmd.PersistentFlags().StringP("format", "o", "json", "Output format (json or yaml)")
	_ = GetCmd.RegisterFlagCompletionFunc("format", formatCompletion)
}

func formatCompletion(_ *cobra.Command, _ []string, _ string) ([]string, cobra.ShellCompDirective) {
	return []string{"json", "yaml"}, cobra.ShellCompDirectiveNoFileComp
}

func prettyPrint(cmd *cobra.Command, data interface{}) error {
	format, _ := cmd.Flags().GetString("format")
	switch format {
	case "json":
		return printJSON(data)
	case "yaml":
		return printYAML(data)
	default:
		return newError("Invalid argument "+format, "Please use json or yaml")
	}
}

func printJSON(data interface{}) error {
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return wrapInError("Error reading Json", err)
	}
	fmt.Println(string(jsonData))
	return nil
}

func printYAML(data interface{}) error {
	yamlData, err := yaml.Marshal(data)
	if err != nil {
		return wrapInError("Error reading YAML", err)
	}

	fmt.Println(string(yamlData))
	return nil
}
