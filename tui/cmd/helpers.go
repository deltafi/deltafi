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

	tea "github.com/charmbracelet/bubbletea"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/ui/components"
)

func renderAsSimpleTable(t api.Table, plain bool) {
	table := components.NewSimpleTable(&t)

	if plain {
		fmt.Println(table.RenderPlain())
	} else {
		fmt.Println(table.Render())
	}
}

func runProgram(model tea.Model) {
	p := tea.NewProgram(model)
	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
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
