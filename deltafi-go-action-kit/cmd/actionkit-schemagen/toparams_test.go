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
// ABOUTME: Verifies that round-tripping Go → JSON → Go → JSON converges (idempotence from the second JSON onward).

package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

// TestRoundTripGoJSONGoJSON exercises the full loop and asserts that the
// second JSON matches the first JSON. The Go form changes (fields alphabetize,
// comments drop), but the schema content is stable from the first JSON onward.
func TestRoundTripGoJSONGoJSON(t *testing.T) {
	// Step 1: Go (fixture) → JSON
	schema1, err := SchemaFromParamsFile("fixtures/sample_action_params.go")
	if err != nil {
		t.Fatalf("ast→json: %v", err)
	}
	json1, err := json.MarshalIndent(schema1, "", "  ")
	if err != nil {
		t.Fatalf("marshal json1: %v", err)
	}

	// Step 2: JSON → Go (write to a tempdir alongside a copy of the JSON)
	tmp := t.TempDir()
	jsonPath := filepath.Join(tmp, "sample_action_params.json")
	if err := os.WriteFile(jsonPath, json1, 0644); err != nil {
		t.Fatalf("write json1: %v", err)
	}
	source, err := ParamsFileFromSchema(jsonPath)
	if err != nil {
		t.Fatalf("json→go: %v", err)
	}
	goPath := filepath.Join(tmp, "sample_action_params.go")
	if err := os.WriteFile(goPath, source, 0644); err != nil {
		t.Fatalf("write go: %v", err)
	}

	// Step 3: Regenerated Go → JSON again
	schema2, err := SchemaFromParamsFile(goPath)
	if err != nil {
		t.Fatalf("ast→json (second pass): %v\nregenerated source:\n%s", err, source)
	}
	json2, err := json.MarshalIndent(schema2, "", "  ")
	if err != nil {
		t.Fatalf("marshal json2: %v", err)
	}

	if string(json1) != string(json2) {
		t.Errorf("round-trip not idempotent.\n--- first ---\n%s\n--- second ---\n%s\n--- regenerated go ---\n%s",
			json1, json2, source)
	}
}
