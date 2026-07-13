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
// ABOUTME: Tests for flow MCP tool argument validation
// ABOUTME: Covers deleteFlow's required-argument and flow-type error paths

package tools

import (
	"context"
	"strings"
	"testing"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
)

func callToolRequest(args map[string]any) mcp.CallToolRequest {
	req := mcp.CallToolRequest{}
	req.Params.Arguments = args
	return req
}

func errorResultText(t *testing.T, result *mcp.CallToolResult) string {
	t.Helper()
	if !result.IsError {
		t.Fatalf("expected an error result")
	}
	if len(result.Content) == 0 {
		t.Fatalf("result has no content")
	}
	text, ok := result.Content[0].(mcp.TextContent)
	if !ok {
		t.Fatalf("content is not TextContent: %T", result.Content[0])
	}
	return text.Text
}

func TestDeleteFlowMissingName(t *testing.T) {
	result, err := deleteFlow(context.Background(), callToolRequest(map[string]any{
		"type": "transform",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); got != "name parameter is required" {
		t.Errorf("message: got %q", got)
	}
}

func TestDeleteFlowMissingType(t *testing.T) {
	result, err := deleteFlow(context.Background(), callToolRequest(map[string]any{
		"name": "my-flow",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); got != "type parameter is required" {
		t.Errorf("message: got %q", got)
	}
}

func TestDeleteFlowUnknownType(t *testing.T) {
	result, err := deleteFlow(context.Background(), callToolRequest(map[string]any{
		"name": "my-flow",
		"type": "bogus",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	got := errorResultText(t, result)
	if !strings.Contains(got, "unknown flow type: bogus") {
		t.Errorf("message should name the bad type: got %q", got)
	}
	for _, valid := range []string{"transform", "rest-data-source", "timed-data-source", "on-error-data-source", "data-sink"} {
		if !strings.Contains(got, valid) {
			t.Errorf("message should list valid type %q: got %q", valid, got)
		}
	}
}

func TestLoadFlowTransformSuccess(t *testing.T) {
	var got graphql.TransformFlowPlanInput
	original := saveTransformFlow
	saveTransformFlow = func(input graphql.TransformFlowPlanInput) (*graphql.SaveTransformResponse, error) {
		got = input
		return &graphql.SaveTransformResponse{}, nil
	}
	t.Cleanup(func() { saveTransformFlow = original })

	result, err := loadFlow(context.Background(), callToolRequest(map[string]any{
		"config": map[string]any{
			"type":        "TRANSFORM",
			"name":        "my-transform",
			"description": "does things",
		},
	}))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", resultText(t, result), err)
	}
	if got.Name != "my-transform" || got.Type != "TRANSFORM" {
		t.Fatalf("save called with unexpected input: %+v", got)
	}
}

func TestLoadFlowRejectsUnknownField(t *testing.T) {
	called := false
	original := saveTransformFlow
	saveTransformFlow = func(input graphql.TransformFlowPlanInput) (*graphql.SaveTransformResponse, error) {
		called = true
		return &graphql.SaveTransformResponse{}, nil
	}
	t.Cleanup(func() { saveTransformFlow = original })

	result, err := loadFlow(context.Background(), callToolRequest(map[string]any{
		"config": map[string]any{
			"type":       "TRANSFORM",
			"name":       "my-transform",
			"subscribbe": []any{}, // typo of "subscribe" — would silently drop routing
		},
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if called {
		t.Fatal("save mutation ran despite an unknown config field; typo'd fields are silently dropped")
	}
	if !result.IsError {
		t.Fatal("expected an error result for unknown config field")
	}
	if msg := resultText(t, result); !strings.Contains(msg, "unknown") {
		t.Fatalf("expected unknown-field error, got %q", msg)
	}
}
