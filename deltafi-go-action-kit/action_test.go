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
// ABOUTME: Tests for action type detection.
// ABOUTME: Verifies detectActionType correctly identifies TransformAction, EgressAction, TimedIngressAction.

package actionkit

import (
	"context"
	"testing"
)

// Test action implementations
type testTransformAction struct{}

func (a *testTransformAction) Transform(ctx context.Context, input TransformInput) Result {
	return NewTransformResult(input.ActionContext)
}

type testEgressAction struct{}

func (a *testEgressAction) Egress(ctx context.Context, input EgressInput) Result {
	return NewEgressResult(input.ActionContext)
}

type testTimedIngressAction struct{}

func (a *testTimedIngressAction) Ingress(ctx context.Context, input IngressInput) Result {
	return NewIngressResult(input.ActionContext)
}

type notAnAction struct{}

func TestDetectActionType_Transform(t *testing.T) {
	actionType, err := detectActionType(&testTransformAction{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if actionType != ActionTypeTransform {
		t.Errorf("got %q, want %q", actionType, ActionTypeTransform)
	}
}

func TestDetectActionType_Egress(t *testing.T) {
	actionType, err := detectActionType(&testEgressAction{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if actionType != ActionTypeEgress {
		t.Errorf("got %q, want %q", actionType, ActionTypeEgress)
	}
}

func TestDetectActionType_TimedIngress(t *testing.T) {
	actionType, err := detectActionType(&testTimedIngressAction{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if actionType != ActionTypeTimedIngress {
		t.Errorf("got %q, want %q", actionType, ActionTypeTimedIngress)
	}
}

func TestDetectActionType_NotAnAction(t *testing.T) {
	_, err := detectActionType(&notAnAction{})
	if err == nil {
		t.Error("expected error for non-action type")
	}
}

func TestDetectActionType_String(t *testing.T) {
	_, err := detectActionType("not an action")
	if err == nil {
		t.Error("expected error for string")
	}
}

func TestRegistration_Builder(t *testing.T) {
	reg := &Registration{Name: "TestAction", Action: &testTransformAction{}}
	reg.Describe("test description").WithParams(struct{ Name string }{})

	if reg.description != "test description" {
		t.Errorf("description = %q, want %q", reg.description, "test description")
	}
	if reg.paramType == nil {
		t.Error("paramType should not be nil")
	}
}

func TestRegistration_DefaultDescription(t *testing.T) {
	reg := &Registration{Name: "TestAction", Action: &testTransformAction{}}

	if reg.description != "" {
		t.Errorf("description = %q, want empty", reg.description)
	}
}

func TestRegistration_WithOptions(t *testing.T) {
	reg := &Registration{Name: "TestAction", Action: &testTransformAction{}}
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
	reg := &Registration{Name: "TestAction", Action: &testTransformAction{}}
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

type testJoinAction struct {
	testTransformAction
}

func (a *testJoinAction) Join(ctx context.Context, inputs []TransformInput) TransformInput {
	return DefaultJoin(inputs)
}

func TestJoinActionInterface(t *testing.T) {
	action := &testJoinAction{}

	// Should implement both TransformAction and JoinAction
	if _, ok := any(action).(TransformAction); !ok {
		t.Error("testJoinAction should implement TransformAction")
	}
	if _, ok := any(action).(JoinAction); !ok {
		t.Error("testJoinAction should implement JoinAction")
	}
}

func TestJoinActionNotImplemented(t *testing.T) {
	action := &testTransformAction{}
	if _, ok := any(action).(JoinAction); ok {
		t.Error("testTransformAction should not implement JoinAction")
	}
}

func TestDefaultJoin(t *testing.T) {
	actx := &ActionContext{DID: "d1", ActionName: "JoinTest"}
	inputs := []TransformInput{
		{
			ActionContext: actx,
			Content: []ActionContent{
				{Name: "file1.txt", MediaType: "text/plain"},
			},
			Metadata: map[string]string{"key1": "val1", "shared": "first"},
			Params:   map[string]any{"p": "v"},
		},
		{
			ActionContext: actx,
			Content: []ActionContent{
				{Name: "file2.txt", MediaType: "text/plain"},
				{Name: "file3.txt", MediaType: "text/plain"},
			},
			Metadata: map[string]string{"key2": "val2", "shared": "second"},
			Params:   map[string]any{"p": "v"},
		},
	}

	merged := DefaultJoin(inputs)

	// Content should be concatenated
	if len(merged.Content) != 3 {
		t.Fatalf("expected 3 content items, got %d", len(merged.Content))
	}
	if merged.Content[0].Name != "file1.txt" {
		t.Errorf("content[0] = %q", merged.Content[0].Name)
	}
	if merged.Content[2].Name != "file3.txt" {
		t.Errorf("content[2] = %q", merged.Content[2].Name)
	}

	// Metadata: later inputs override
	if merged.Metadata["key1"] != "val1" {
		t.Errorf("metadata key1 = %q", merged.Metadata["key1"])
	}
	if merged.Metadata["key2"] != "val2" {
		t.Errorf("metadata key2 = %q", merged.Metadata["key2"])
	}
	if merged.Metadata["shared"] != "second" {
		t.Errorf("metadata shared = %q, want 'second' (later overrides)", merged.Metadata["shared"])
	}

	// ActionContext and Params from first input
	if merged.ActionContext != actx {
		t.Error("ActionContext should be from first input")
	}
	if merged.Params["p"] != "v" {
		t.Errorf("Params p = %v", merged.Params["p"])
	}
}

func TestDefaultJoin_SingleInput(t *testing.T) {
	actx := &ActionContext{DID: "d1"}
	inputs := []TransformInput{
		{
			ActionContext: actx,
			Content:       []ActionContent{{Name: "only.txt"}},
			Metadata:      map[string]string{"k": "v"},
		},
	}

	merged := DefaultJoin(inputs)

	if len(merged.Content) != 1 || merged.Content[0].Name != "only.txt" {
		t.Errorf("content = %v", merged.Content)
	}
	if merged.Metadata["k"] != "v" {
		t.Errorf("metadata k = %q", merged.Metadata["k"])
	}
}
