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
// ABOUTME: Tests for action registration builder methods and join action behavior.
// ABOUTME: Verifies Registration.Describe, WithParams, WithOptions, and Join merging logic.

package actionkit

import (
	"testing"
)

// Test action implementations using the convention-based method signatures.

type noParams struct{}

type testTransformAction struct{}

func (a *testTransformAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df
}

type testEgressAction struct{}

func (a *testEgressAction) Egress(df *EgressDeltafile, params noParams) *EgressDeltafile {
	return df
}

type testTimedIngressAction struct{}

func (a *testTimedIngressAction) Ingress(deltafile *TimedIngressDeltafile, params noParams) *TimedIngressDeltafile {
	return deltafile
}

func TestRegistration_Builder(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	RegisterTransform("TestAction", &testTransformAction{})
	reg := registered[0]
	reg.Describe("test description").WithParams(noParams{})

	if reg.description != "test description" {
		t.Errorf("description = %q, want %q", reg.description, "test description")
	}
	if reg.paramDefaults == nil {
		t.Error("paramDefaults should not be nil")
	}
}

func TestRegistration_DefaultDescription(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	RegisterTransform("TestAction", &testTransformAction{})
	reg := registered[0]

	if reg.description != "" {
		t.Errorf("description = %q, want empty", reg.description)
	}
}

func TestRegistration_WithOptions(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	reg := RegisterTransform("TestAction", &testTransformAction{})
	reg.WithOptions(ActionOptions{
		Description: "My action",
		InputSpec:   &InputSpec{ContentSummary: "text input"},
		Filters:     SimpleFilters("prefix match"),
	})

	if reg.options == nil {
		t.Fatal("options should not be nil")
	}
	if reg.options.Description != "My action" {
		t.Errorf("options.Description = %q", reg.options.Description)
	}
	// WithOptions should auto-populate description when it was empty
	if reg.description != "My action" {
		t.Errorf("description = %q, want %q", reg.description, "My action")
	}
}

func TestRegistration_WithOptions_PreservesExplicitDescribe(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	reg := RegisterTransform("TestAction", &testTransformAction{})
	reg.Describe("explicit desc").WithOptions(ActionOptions{
		Description: "options desc",
	})

	// Describe() was called first, so it should not be overwritten
	if reg.description != "explicit desc" {
		t.Errorf("description = %q, want %q", reg.description, "explicit desc")
	}
	// Options should still be set
	if reg.options.Description != "options desc" {
		t.Errorf("options.Description = %q", reg.options.Description)
	}
}

// --- JoinAction ---

type testJoinAction struct{}

func (a *testJoinAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df
}

func (a *testJoinAction) Join(inputs []*Deltafile, joined *Deltafile, params noParams) *Deltafile {
	// Merge content from all inputs
	for _, input := range inputs {
		joined.AddContentList(input.GetContentList())
		joined.AddMetadataMap(input.GetMetadataMap())
	}
	return joined
}

func TestJoinAction_MergesContentAndMetadata(t *testing.T) {
	tc := NewTestContext("d1")

	input0 := tc.NewDeltafile(
		ActionContent{Name: "file1.txt", MediaType: "text/plain"},
	)
	input0.AddMetadata("key1", "val1").AddMetadata("shared", "first")

	input1 := tc.NewDeltafile(
		ActionContent{Name: "file2.txt", MediaType: "text/plain"},
		ActionContent{Name: "file3.txt", MediaType: "text/plain"},
	)
	input1.AddMetadata("key2", "val2").AddMetadata("shared", "second")

	joined := tc.NewDeltafile()

	action := &testJoinAction{}
	result := action.Join([]*Deltafile{input0, input1}, joined, noParams{})

	// Content from all inputs should be in joined output
	newContent := result.GetNewContentList()
	if len(newContent) != 3 {
		t.Fatalf("expected 3 content items, got %d", len(newContent))
	}
	if newContent[0].Name != "file1.txt" {
		t.Errorf("content[0] = %q", newContent[0].Name)
	}
	if newContent[2].Name != "file3.txt" {
		t.Errorf("content[2] = %q", newContent[2].Name)
	}

	// Metadata: later inputs override
	meta := result.GetMetadataMap()
	if meta["key1"] != "val1" {
		t.Errorf("metadata key1 = %q", meta["key1"])
	}
	if meta["key2"] != "val2" {
		t.Errorf("metadata key2 = %q", meta["key2"])
	}
	if meta["shared"] != "second" {
		t.Errorf("metadata shared = %q, want 'second' (later overrides)", meta["shared"])
	}
}

func TestJoinAction_SingleInput(t *testing.T) {
	tc := NewTestContext("d1")

	input := tc.NewDeltafile(
		ActionContent{Name: "only.txt"},
	)
	input.AddMetadata("k", "v")

	joined := tc.NewDeltafile()

	action := &testJoinAction{}
	result := action.Join([]*Deltafile{input}, joined, noParams{})

	newContent := result.GetNewContentList()
	if len(newContent) != 1 || newContent[0].Name != "only.txt" {
		t.Errorf("content = %v", newContent)
	}
	meta := result.GetMetadataMap()
	if meta["k"] != "v" {
		t.Errorf("metadata k = %q", meta["k"])
	}
}

type testJoinErrorAction struct{}

func (a *testJoinErrorAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df
}

func (a *testJoinErrorAction) Join(inputs []*Deltafile, joined *Deltafile, params noParams) *Deltafile {
	return joined.Errorf("join failed: %d inputs", len(inputs))
}

func TestJoinAction_ReturnsError(t *testing.T) {
	tc := NewTestContext("d1")
	input := tc.NewDeltafile(ActionContent{Name: "f.txt"})
	joined := tc.NewDeltafile()

	result := (&testJoinErrorAction{}).Join([]*Deltafile{input}, joined, noParams{})

	if !result.hasError {
		t.Fatal("expected error state on returned Deltafile")
	}
	if result.errCause != "join failed: 1 inputs" {
		t.Errorf("cause = %q", result.errCause)
	}
}

type testJoinFilterAction struct{}

func (a *testJoinFilterAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df
}

func (a *testJoinFilterAction) Join(inputs []*Deltafile, joined *Deltafile, params noParams) *Deltafile {
	return joined.Filterf("not enough inputs")
}

func TestJoinAction_ReturnsFilter(t *testing.T) {
	tc := NewTestContext("d1")
	input := tc.NewDeltafile(ActionContent{Name: "f.txt"})
	joined := tc.NewDeltafile()

	result := (&testJoinFilterAction{}).Join([]*Deltafile{input}, joined, noParams{})

	if !result.hasFilter {
		t.Fatal("expected filter state on returned Deltafile")
	}
	if result.filterMessage != "not enough inputs" {
		t.Errorf("message = %q", result.filterMessage)
	}
}
