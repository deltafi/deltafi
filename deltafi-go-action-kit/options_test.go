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
// ABOUTME: Tests for ActionOptions types and helper functions.
// ABOUTME: Verifies JSON serialization, SimpleFilters, SimpleErrors, and omitempty behavior.

package actionkit

import (
	"encoding/json"
	"testing"
)

func TestSimpleFilters(t *testing.T) {
	filters := SimpleFilters("filter one", "filter two")
	if len(filters) != 2 {
		t.Fatalf("len = %d, want 2", len(filters))
	}
	if filters[0].Description != "filter one" {
		t.Errorf("filters[0].Description = %q", filters[0].Description)
	}
	if filters[1].Description != "filter two" {
		t.Errorf("filters[1].Description = %q", filters[1].Description)
	}
	if filters[0].Conditions != nil {
		t.Error("filters[0].Conditions should be nil")
	}
}

func TestSimpleErrors(t *testing.T) {
	errors := SimpleErrors("err1")
	if len(errors) != 1 {
		t.Fatalf("len = %d, want 1", len(errors))
	}
	if errors[0].Description != "err1" {
		t.Errorf("errors[0].Description = %q", errors[0].Description)
	}
}

func TestSimpleFilters_Empty(t *testing.T) {
	filters := SimpleFilters()
	if len(filters) != 0 {
		t.Fatalf("len = %d, want 0", len(filters))
	}
}

func TestActionOptions_JSONSerialization(t *testing.T) {
	opts := ActionOptions{
		Description: "Test action",
		InputSpec: &InputSpec{
			ContentSummary: "One text input",
			ContentSpecs: []ContentSpec{
				{Name: "input.txt", MediaType: "text/plain", Description: "The input file"},
			},
			MetadataSummary: "Requires index metadata",
			MetadataDescriptions: []KeyedDescription{
				{Key: "index", Description: "Item index"},
			},
		},
		OutputSpec: &OutputSpec{
			ContentSummary:     "One transformed output",
			Passthrough:        true,
			AnnotationsSummary: "Adds language",
			AnnotationDescriptions: []KeyedDescription{
				{Key: "language", Description: "Content language"},
			},
		},
		Filters: SimpleFilters("Filtered on prefix"),
		Errors: []DescriptionWithConditions{
			{Description: "Parse error", Conditions: []string{"Invalid JSON input"}},
		},
		Notes:   []string{"This is a reference implementation"},
		Details: "Extended documentation here",
	}

	data, err := json.Marshal(opts)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if parsed["description"] != "Test action" {
		t.Errorf("description = %v", parsed["description"])
	}

	inputSpec := parsed["inputSpec"].(map[string]any)
	if inputSpec["contentSummary"] != "One text input" {
		t.Errorf("inputSpec.contentSummary = %v", inputSpec["contentSummary"])
	}
	contentSpecs := inputSpec["contentSpecs"].([]any)
	if len(contentSpecs) != 1 {
		t.Fatalf("contentSpecs len = %d", len(contentSpecs))
	}
	cs := contentSpecs[0].(map[string]any)
	if cs["name"] != "input.txt" {
		t.Errorf("contentSpec.name = %v", cs["name"])
	}

	outputSpec := parsed["outputSpec"].(map[string]any)
	if outputSpec["passthrough"] != true {
		t.Errorf("outputSpec.passthrough = %v", outputSpec["passthrough"])
	}
	if outputSpec["annotationsSummary"] != "Adds language" {
		t.Errorf("outputSpec.annotationsSummary = %v", outputSpec["annotationsSummary"])
	}

	filters := parsed["filters"].([]any)
	if len(filters) != 1 {
		t.Fatalf("filters len = %d", len(filters))
	}

	errors := parsed["errors"].([]any)
	if len(errors) != 1 {
		t.Fatalf("errors len = %d", len(errors))
	}
	errEntry := errors[0].(map[string]any)
	conditions := errEntry["conditions"].([]any)
	if len(conditions) != 1 || conditions[0] != "Invalid JSON input" {
		t.Errorf("error conditions = %v", conditions)
	}

	notes := parsed["notes"].([]any)
	if len(notes) != 1 || notes[0] != "This is a reference implementation" {
		t.Errorf("notes = %v", notes)
	}

	if parsed["details"] != "Extended documentation here" {
		t.Errorf("details = %v", parsed["details"])
	}
}

func TestActionOptions_OmitEmpty(t *testing.T) {
	opts := ActionOptions{
		Description: "Minimal",
	}

	data, err := json.Marshal(opts)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if parsed["description"] != "Minimal" {
		t.Errorf("description = %v", parsed["description"])
	}

	// All optional fields should be omitted
	for _, key := range []string{"inputSpec", "outputSpec", "filters", "errors", "notes", "details"} {
		if _, exists := parsed[key]; exists {
			t.Errorf("expected %q to be omitted", key)
		}
	}
}

func TestActionOptions_WithConditions(t *testing.T) {
	d := DescriptionWithConditions{
		Description: "Content too large",
		Conditions:  []string{"Size > 100MB", "No streaming enabled"},
	}

	data, err := json.Marshal(d)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	json.Unmarshal(data, &parsed)

	if parsed["description"] != "Content too large" {
		t.Errorf("description = %v", parsed["description"])
	}
	conditions := parsed["conditions"].([]any)
	if len(conditions) != 2 {
		t.Fatalf("conditions len = %d", len(conditions))
	}
}

func TestDescriptionWithConditions_OmitsNilConditions(t *testing.T) {
	d := DescriptionWithConditions{Description: "simple"}

	data, err := json.Marshal(d)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	json.Unmarshal(data, &parsed)

	if _, exists := parsed["conditions"]; exists {
		t.Error("conditions should be omitted when nil")
	}
}
