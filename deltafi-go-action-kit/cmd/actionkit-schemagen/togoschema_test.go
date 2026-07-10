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
// ABOUTME: Verifies the AST-based schema generator matches the kit's reflect-based GenerateSchema.

package main

import (
	"encoding/json"
	"testing"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
	"gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2/cmd/actionkit-schemagen/fixtures"
)

// TestSchemaFromParamsFile_MatchesReflect asserts that the AST-generated schema
// for the fixture file is byte-for-byte identical to the schema produced by
// the kit's reflect-based GenerateSchema for the same type.
func TestSchemaFromParamsFile_MatchesReflect(t *testing.T) {
	got, err := SchemaFromParamsFile("fixtures/sample_action_params.go")
	if err != nil {
		t.Fatalf("SchemaFromParamsFile: %v", err)
	}
	want := actionkit.GenerateSchema(fixtures.SampleParams{})

	gotJSON, err := json.MarshalIndent(got, "", "  ")
	if err != nil {
		t.Fatalf("marshal got: %v", err)
	}
	wantJSON, err := json.MarshalIndent(want, "", "  ")
	if err != nil {
		t.Fatalf("marshal want: %v", err)
	}

	if string(gotJSON) != string(wantJSON) {
		t.Errorf("schema mismatch.\n--- ast ---\n%s\n--- reflect ---\n%s", gotJSON, wantJSON)
	}
}
