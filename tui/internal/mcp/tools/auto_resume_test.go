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
// ABOUTME: Tests for resume policy MCP tool JSON-to-input decoding and validation
// ABOUTME: Covers required-field enforcement, optional fields, back-off mapping, id parsing, and empty-list replace_all handling

package tools

import (
	"context"
	"strings"
	"testing"

	"github.com/google/uuid"
	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
)

func TestDecodeResumePolicyInput_Minimal(t *testing.T) {
	policy, err := decodeResumePolicyInput(map[string]any{
		"name":        "retry-timeouts",
		"maxAttempts": float64(3),
		"backOff":     map[string]any{"delay": float64(1000)},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if policy.Name != "retry-timeouts" {
		t.Errorf("name: got %q", policy.Name)
	}
	if policy.MaxAttempts != 3 {
		t.Errorf("maxAttempts: got %d", policy.MaxAttempts)
	}
	if policy.BackOff.Delay != 1000 {
		t.Errorf("backOff.delay: got %d", policy.BackOff.Delay)
	}
	if policy.Id != nil {
		t.Errorf("expected nil id, got %v", policy.Id)
	}
}

func TestDecodeResumePolicyInput_FullPayload(t *testing.T) {
	id := uuid.New()
	mult := 2
	maxDelay := 60000
	priority := 10
	enabled := true
	policy, err := decodeResumePolicyInput(map[string]any{
		"id":             id.String(),
		"name":           "retry-503",
		"errorSubstring": "503",
		"dataSource":     "rest-source",
		"action":         "MyAction",
		"maxAttempts":    float64(5),
		"priority":       float64(priority),
		"enabled":        enabled,
		"backOff": map[string]any{
			"delay":      float64(2000),
			"maxDelay":   float64(maxDelay),
			"multiplier": float64(mult),
			"random":     true,
		},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if policy.Id == nil || *policy.Id != id {
		t.Errorf("id: got %v", policy.Id)
	}
	if policy.ErrorSubstring == nil || *policy.ErrorSubstring != "503" {
		t.Errorf("errorSubstring: got %v", policy.ErrorSubstring)
	}
	if policy.DataSource == nil || *policy.DataSource != "rest-source" {
		t.Errorf("dataSource: got %v", policy.DataSource)
	}
	if policy.Action == nil || *policy.Action != "MyAction" {
		t.Errorf("action: got %v", policy.Action)
	}
	if policy.Priority == nil || *policy.Priority != priority {
		t.Errorf("priority: got %v", policy.Priority)
	}
	if policy.Enabled == nil || *policy.Enabled != enabled {
		t.Errorf("enabled: got %v", policy.Enabled)
	}
	if policy.BackOff.MaxDelay == nil || *policy.BackOff.MaxDelay != maxDelay {
		t.Errorf("backOff.maxDelay: got %v", policy.BackOff.MaxDelay)
	}
	if policy.BackOff.Multiplier == nil || *policy.BackOff.Multiplier != mult {
		t.Errorf("backOff.multiplier: got %v", policy.BackOff.Multiplier)
	}
	if policy.BackOff.Random == nil || !*policy.BackOff.Random {
		t.Errorf("backOff.random: got %v", policy.BackOff.Random)
	}
}

func TestDecodeResumePolicyInput_NotAnObject(t *testing.T) {
	_, err := decodeResumePolicyInput("nope")
	if err == nil {
		t.Fatal("expected error for non-object input")
	}
}

func TestDecodeResumePolicyInput_Nil(t *testing.T) {
	_, err := decodeResumePolicyInput(nil)
	if err == nil {
		t.Fatal("expected error for nil input")
	}
}

func TestDecodeResumePolicyInput_MissingName(t *testing.T) {
	_, err := decodeResumePolicyInput(map[string]any{
		"maxAttempts": float64(3),
		"backOff":     map[string]any{"delay": float64(1000)},
	})
	if err == nil || !strings.Contains(err.Error(), "name") {
		t.Fatalf("expected name-required error, got %v", err)
	}
}

func TestDecodeResumePolicyInput_ZeroMaxAttempts(t *testing.T) {
	_, err := decodeResumePolicyInput(map[string]any{
		"name":        "no-retries",
		"maxAttempts": float64(0),
		"backOff":     map[string]any{"delay": float64(1000)},
	})
	if err == nil || !strings.Contains(err.Error(), "maxAttempts") {
		t.Fatalf("expected maxAttempts validation error, got %v", err)
	}
}

func TestDecodeResumePolicyInput_ZeroBackOffDelay(t *testing.T) {
	_, err := decodeResumePolicyInput(map[string]any{
		"name":        "instant",
		"maxAttempts": float64(3),
		"backOff":     map[string]any{"delay": float64(0)},
	})
	if err == nil || !strings.Contains(err.Error(), "backOff.delay") {
		t.Fatalf("expected backOff.delay validation error, got %v", err)
	}
}

func TestLoadResumePolicies_EmptyWithoutReplaceAll(t *testing.T) {
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{
		"policies": []any{},
	}}}
	result, err := loadResumePolicies(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected error result for empty policies without replace_all")
	}
	if msg := resultText(t, result); !strings.Contains(msg, "replace_all") {
		t.Fatalf("expected replace_all hint in error, got %q", msg)
	}
}

func TestLoadResumePolicies_EmptyReplaceAllFalse(t *testing.T) {
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{
		"policies":    []any{},
		"replace_all": false,
	}}}
	result, err := loadResumePolicies(context.Background(), req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected error result for empty policies with replace_all=false")
	}
}

func TestDecodeResumePolicyInput_InvalidUUID(t *testing.T) {
	_, err := decodeResumePolicyInput(map[string]any{
		"id":          "not-a-uuid",
		"name":        "retry-503",
		"maxAttempts": float64(3),
		"backOff":     map[string]any{"delay": float64(1000)},
	})
	if err == nil {
		t.Fatal("expected error for invalid id")
	}
}

func TestLoadResumePoliciesRequest(t *testing.T) {
	var gotReplace bool
	var gotPolicies []graphql.ResumePolicyInput
	original := loadResumePoliciesMutation
	loadResumePoliciesMutation = func(replaceAll bool, policies []graphql.ResumePolicyInput) (*graphql.LoadResumePoliciesResponse, error) {
		gotReplace = replaceAll
		gotPolicies = policies
		return &graphql.LoadResumePoliciesResponse{}, nil
	}
	t.Cleanup(func() { loadResumePoliciesMutation = original })

	result, err := loadResumePolicies(context.Background(), callToolRequest(map[string]any{
		"policies": []any{
			map[string]any{
				"name":        "retry-timeouts",
				"maxAttempts": float64(3),
				"backOff":     map[string]any{"delay": float64(1000)},
			},
		},
	}))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", resultText(t, result), err)
	}
	if gotReplace {
		t.Errorf("replaceAll: expected false")
	}
	if len(gotPolicies) != 1 || gotPolicies[0].Name != "retry-timeouts" || gotPolicies[0].MaxAttempts != 3 {
		t.Fatalf("unexpected policies dispatched: %+v", gotPolicies)
	}
}

func TestDecodeResumePolicyInput_RejectsUnknownField(t *testing.T) {
	_, err := decodeResumePolicyInput(map[string]any{
		"name": "retry", "maxAttempts": float64(3),
		"backOff": map[string]any{"delay": float64(1000)}, "dataSorce": "typo",
	})
	if err == nil || !strings.Contains(err.Error(), "unknown field") {
		t.Fatalf("expected unknown-field error, got %v", err)
	}
}
