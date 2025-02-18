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
	"os"
	"path/filepath"
	"sigs.k8s.io/yaml"
	"strings"
)

var LoadCmd = &cobra.Command{
	Use:     "load <resource>",
	Short:   "Load creates or updates a DeltaFi resource",
	GroupID: "flow",
	Long: `Load creates or updates a DeltaFi resource, such as a new data source or transform.

# Example creating a new transform flow
deltafi load transform --file path_to_file.yaml
`,
	Args: cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		fmt.Println("There are no resources of type " + args[0])
		return cmd.Usage()
	},
}

func init() {
	rootCmd.AddCommand(LoadCmd)
	LoadCmd.PersistentFlags().StringP("file", "f", "", "Path to file to load (file must have a json or yaml/yml extension)")
	_ = LoadCmd.MarkPersistentFlagFilename("file", "yaml", "yml", "json")
	_ = LoadCmd.MarkPersistentFlagRequired("file")

	LoadCmd.PersistentFlags().StringP("format", "o", "json", "Output format (json or yaml)")
	_ = LoadCmd.RegisterFlagCompletionFunc("format", formatCompletion)
}

func parseFile(cmd *cobra.Command, out interface{}) error {
	filename, _ := cmd.Flags().GetString("file")
	content, err := os.ReadFile(filename)
	if err != nil {
		return wrapInError("Could not read the file "+filename, err)
	}

	ext := strings.ToLower(filepath.Ext(filename))
	switch ext {
	case ".json":
		if err := json.Unmarshal(content, out); err != nil {
			return wrapInError("Error reading Json", err)
		}
	case ".yaml", ".yml":
		if err := yaml.Unmarshal(content, out); err != nil {
			return wrapInError("Error reading YAML", err)
		}
	default:
		return newError("Unsupported file extension: "+ext, "The extension must be json, yaml, or yml")
	}
	return nil
}
