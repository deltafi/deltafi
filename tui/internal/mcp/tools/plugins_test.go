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
// ABOUTME: Tests for plugin install wait-loop polling behavior
// ABOUTME: Verifies success, failure, timeout, context cancellation, and consecutive error handling

package tools

import (
	"context"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/types"
)

func resultText(t *testing.T, result *mcp.CallToolResult) string {
	t.Helper()
	tc, ok := result.Content[0].(mcp.TextContent)
	if !ok {
		t.Fatalf("expected TextContent, got %T", result.Content[0])
	}
	return tc.Text
}

func makePluginResponse(image, tag string, state graphql.PluginState, installErr *string) *graphql.GetPluginsResponse {
	return &graphql.GetPluginsResponse{
		Plugins: []graphql.GetPluginsPluginsPlugin{
			{
				DisplayName: "Test Plugin",
				ImageName:   new(image),
				ImageTag:    new(tag),
				PluginCoordinates: graphql.GetPluginsPluginsPluginPluginCoordinates{
					GroupId:    "org.deltafi",
					ArtifactId: "test-plugin",
					Version:    "1.0.0",
				},
				InstallState: state,
				InstallError: installErr,
			},
		},
	}
}

func TestPollPluginInstall_ImmediateSuccess(t *testing.T) {
	fetch := func() (*graphql.GetPluginsResponse, error) {
		return makePluginResponse("my-plugin", "latest", graphql.PluginStateInstalled, nil), nil
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 5*time.Second, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.IsError {
		t.Fatalf("expected success, got error: %v", result.Content)
	}
	text := resultText(t, result)
	if !strings.Contains(text, "installed successfully") {
		t.Errorf("expected success message, got: %s", text)
	}
}

func TestPollPluginInstall_UntaggedImageMatchesInstalledTag(t *testing.T) {
	fetch := func() (*graphql.GetPluginsResponse, error) {
		return makePluginResponse("my-plugin", "latest", graphql.PluginStateInstalled, nil), nil
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin", 5*time.Second, time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.IsError {
		t.Fatalf("expected untagged image to match installed plugin: %s", resultText(t, result))
	}
}

func TestParsePluginImageRef(t *testing.T) {
	tests := []struct {
		image, name, tag string
	}{
		{"plugin", "plugin", ""},
		{"plugin:latest", "plugin", "latest"},
		{"registry.example:5000/plugin", "registry.example:5000/plugin", ""},
		{"registry.example:5000/plugin:1.2", "registry.example:5000/plugin", "1.2"},
	}
	for _, tc := range tests {
		name, tag := types.ParseImageReference(tc.image)
		if name != tc.name || tag != tc.tag {
			t.Errorf("ParseImageReference(%q) = (%q, %q), want (%q, %q)", tc.image, name, tag, tc.name, tc.tag)
		}
	}
}

func TestPollPluginInstall_Failure(t *testing.T) {
	fetch := func() (*graphql.GetPluginsResponse, error) {
		return makePluginResponse("my-plugin", "latest", graphql.PluginStateFailed, new("image not found")), nil
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 5*time.Second, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected error result for failed install")
	}
	text := resultText(t, result)
	if !strings.Contains(text, "image not found") {
		t.Errorf("expected install error message, got: %s", text)
	}
}

func TestPollPluginInstall_ConsecutiveErrorsBail(t *testing.T) {
	callCount := 0
	fetch := func() (*graphql.GetPluginsResponse, error) {
		callCount++
		return nil, fmt.Errorf("connection refused")
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 5*time.Second, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected error result after consecutive failures")
	}
	text := resultText(t, result)
	if !strings.Contains(text, "connection refused") {
		t.Errorf("expected underlying error in message, got: %s", text)
	}
	if !strings.Contains(text, "3 attempts") {
		t.Errorf("expected attempt count in message, got: %s", text)
	}
	if callCount != 3 {
		t.Errorf("expected exactly 3 calls before bail, got %d", callCount)
	}
}

func TestPollPluginInstall_TransientErrorRecovery(t *testing.T) {
	callCount := 0
	fetch := func() (*graphql.GetPluginsResponse, error) {
		callCount++
		// Fail twice, then succeed
		if callCount <= 2 {
			return nil, fmt.Errorf("transient error")
		}
		return makePluginResponse("my-plugin", "latest", graphql.PluginStateInstalled, nil), nil
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 5*time.Second, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.IsError {
		t.Fatalf("expected success after transient errors, got error")
	}
	if callCount != 3 {
		t.Errorf("expected 3 calls (2 failures + 1 success), got %d", callCount)
	}
}

func TestPollPluginInstall_ContextCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // Cancel immediately

	fetch := func() (*graphql.GetPluginsResponse, error) {
		t.Fatal("fetch should not be called after context cancellation")
		return nil, nil
	}

	result, err := pollPluginInstall(ctx, "my-plugin:latest", 5*time.Second, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected error result for cancelled context")
	}
	text := resultText(t, result)
	if !strings.Contains(text, "cancelled") {
		t.Errorf("expected cancellation message, got: %s", text)
	}
}

func TestPollPluginInstall_Timeout(t *testing.T) {
	fetch := func() (*graphql.GetPluginsResponse, error) {
		// Plugin still installing, never reaches terminal state
		return makePluginResponse("my-plugin", "latest", graphql.PluginState("INSTALLING"), nil), nil
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 50*time.Millisecond, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected error result for timeout")
	}
	text := resultText(t, result)
	if !strings.Contains(text, "timed out") {
		t.Errorf("expected timeout message, got: %s", text)
	}
}

func TestPollPluginInstall_PluginNotInList(t *testing.T) {
	fetch := func() (*graphql.GetPluginsResponse, error) {
		// Return a different plugin
		return makePluginResponse("other-plugin", "latest", graphql.PluginStateInstalled, nil), nil
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 50*time.Millisecond, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.IsError {
		t.Fatal("expected timeout when plugin not found in list")
	}
	text := resultText(t, result)
	if !strings.Contains(text, "timed out") {
		t.Errorf("expected timeout message, got: %s", text)
	}
}

func TestPollPluginInstall_ErrorCounterResets(t *testing.T) {
	callCount := 0
	fetch := func() (*graphql.GetPluginsResponse, error) {
		callCount++
		// Fail twice, succeed once, fail twice more, then succeed with installed
		switch {
		case callCount <= 2:
			return nil, fmt.Errorf("error batch 1")
		case callCount == 3:
			return makePluginResponse("my-plugin", "latest", graphql.PluginState("INSTALLING"), nil), nil
		case callCount <= 5:
			return nil, fmt.Errorf("error batch 2")
		default:
			return makePluginResponse("my-plugin", "latest", graphql.PluginStateInstalled, nil), nil
		}
	}

	result, err := pollPluginInstall(context.Background(), "my-plugin:latest", 5*time.Second, 1*time.Millisecond, fetch)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.IsError {
		t.Fatalf("expected success — counter should reset after each successful poll")
	}
	if callCount != 6 {
		t.Errorf("expected 6 calls, got %d", callCount)
	}
}
