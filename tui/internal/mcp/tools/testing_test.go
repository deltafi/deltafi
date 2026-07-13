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
// ABOUTME: Handler tests for integration-test MCP tools
// ABOUTME: Verifies inline and local-file configuration reaches GraphQL unchanged

package tools

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
)

func stubLoadIntegrationTest(t *testing.T, want string) {
	t.Helper()
	original := loadIntegrationTestRequest
	loadIntegrationTestRequest = func(config string) (*graphql.LoadIntegrationTestResponse, error) {
		if config != want {
			t.Fatalf("config = %q, want %q", config, want)
		}
		return &graphql.LoadIntegrationTestResponse{
			LoadIntegrationTest: graphql.LoadIntegrationTestLoadIntegrationTestResult{Success: true},
		}, nil
	}
	t.Cleanup(func() { loadIntegrationTestRequest = original })
}

func TestLoadIntegrationTestInlineRequest(t *testing.T) {
	const config = "name: inline-test\n"
	stubLoadIntegrationTest(t, config)
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"config_yaml": config}}}
	result, err := loadIntegrationTest(context.Background(), req)
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", result, err)
	}
}

func TestLoadIntegrationTestFileRequest(t *testing.T) {
	const config = "name: file-test\n"
	path := filepath.Join(t.TempDir(), "test.yaml")
	if err := os.WriteFile(path, []byte(config), 0o600); err != nil {
		t.Fatal(err)
	}
	stubLoadIntegrationTest(t, config)
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"config_path": path}}}
	result, err := loadIntegrationTest(context.Background(), req)
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", result, err)
	}
}
