/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: CLI tool that converts between *_action_params.go and *_action_params.json files.
// ABOUTME: Both directions work file-by-file via go/ast — no compilation of the plugin needed.

package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

const usage = `usage: actionkit-schemagen <command> <actions-dir>

Commands:
  json     Read each *_action_params.go file in the actions directory and write
           the matching *_action_params.json schema next to it.

  params   Read each *_action_params.json file in the actions directory and write
           the matching *_action_params.go source next to it. Overwrites the .go
           file; fields are emitted in alphabetical order with the kit's tag
           convention (json/description/default).

Examples:
  go run gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2/cmd/actionkit-schemagen json ./actions
  go run gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2/cmd/actionkit-schemagen params ./actions
`

func main() {
	if len(os.Args) < 3 {
		fmt.Fprint(os.Stderr, usage)
		os.Exit(2)
	}

	cmd := os.Args[1]
	dir := os.Args[2]

	if _, err := os.Stat(dir); err != nil {
		fmt.Fprintf(os.Stderr, "actions directory not readable: %v\n", err)
		os.Exit(1)
	}

	switch cmd {
	case "json":
		if err := runJSONFromParams(dir); err != nil {
			fmt.Fprintf(os.Stderr, "jsonFromParams: %v\n", err)
			os.Exit(1)
		}
	case "params":
		if err := runParamsFromJSON(dir); err != nil {
			fmt.Fprintf(os.Stderr, "paramsFromJson: %v\n", err)
			os.Exit(1)
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n\n%s", cmd, usage)
		os.Exit(2)
	}
}

// runJSONFromParams walks the actions directory for *_action_params.go files
// and writes the matching *_action_params.json next to each.
func runJSONFromParams(dir string) error {
	files, err := filepath.Glob(filepath.Join(dir, "*_action_params.go"))
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return fmt.Errorf("no *_action_params.go files found in %s", dir)
	}
	for _, src := range files {
		schema, err := SchemaFromParamsFile(src)
		if err != nil {
			return fmt.Errorf("%s: %w", src, err)
		}
		data, err := json.MarshalIndent(schema, "", "  ")
		if err != nil {
			return fmt.Errorf("%s: marshal: %w", src, err)
		}
		out := strings.TrimSuffix(src, ".go") + ".json"
		if err := os.WriteFile(out, append(data, '\n'), 0644); err != nil {
			return fmt.Errorf("%s: write: %w", out, err)
		}
		fmt.Printf("Wrote %s\n", out)
	}
	return nil
}

// runParamsFromJSON walks the actions directory for *_action_params.json files
// and writes the matching *_action_params.go next to each.
func runParamsFromJSON(dir string) error {
	files, err := filepath.Glob(filepath.Join(dir, "*_action_params.json"))
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return fmt.Errorf("no *_action_params.json files found in %s", dir)
	}
	for _, src := range files {
		source, err := ParamsFileFromSchema(src)
		if err != nil {
			return fmt.Errorf("%s: %w", src, err)
		}
		out := strings.TrimSuffix(src, ".json") + ".go"
		if err := os.WriteFile(out, source, 0644); err != nil {
			return fmt.Errorf("%s: write: %w", out, err)
		}
		fmt.Printf("Wrote %s\n", out)
	}
	return nil
}
