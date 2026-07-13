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
// ABOUTME: Tests that the MCP server enforces tool input schemas on dispatch
// ABOUTME: Verifies wrong-typed and unknown arguments are rejected before handlers run

package mcp

import (
	"context"
	"encoding/json"
	"testing"

	"github.com/mark3labs/mcp-go/mcp"
	"github.com/mark3labs/mcp-go/server"
)

// dispatchToolCall sends a tools/call request to the server in-process.
func dispatchToolCall(t *testing.T, s *server.MCPServer, name string, args map[string]any) {
	t.Helper()
	params, err := json.Marshal(map[string]any{"name": name, "arguments": args})
	if err != nil {
		t.Fatalf("marshal params: %v", err)
	}
	msg, err := json.Marshal(map[string]any{
		"jsonrpc": "2.0",
		"id":      1,
		"method":  "tools/call",
		"params":  json.RawMessage(params),
	})
	if err != nil {
		t.Fatalf("marshal message: %v", err)
	}
	s.HandleMessage(context.Background(), msg)
}

func TestServerEnforcesInputSchema_RejectsWrongType(t *testing.T) {
	s, err := newServer([]string{"admin"})
	if err != nil {
		t.Fatalf("newServer: %v", err)
	}
	called := false
	s.AddTool(
		mcp.NewTool("probe_wrong_type", mcp.WithBoolean("flag", mcp.Required(), mcp.Description("a flag"))),
		func(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
			called = true
			return mcp.NewToolResultText("ok"), nil
		},
	)

	dispatchToolCall(t, s, "probe_wrong_type", map[string]any{"flag": "not-a-bool"})

	if called {
		t.Fatal("handler ran despite invalid argument type; server is not enforcing input schemas")
	}
}

func TestServerEnforcesInputSchema_RejectsUnknownArgument(t *testing.T) {
	s, err := newServer([]string{"admin"})
	if err != nil {
		t.Fatalf("newServer: %v", err)
	}
	called := false
	s.AddTool(
		mcp.NewTool("probe_unknown_arg", mcp.WithBoolean("flag", mcp.Required(), mcp.Description("a flag"))),
		func(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
			called = true
			return mcp.NewToolResultText("ok"), nil
		},
	)

	dispatchToolCall(t, s, "probe_unknown_arg", map[string]any{"flag": true, "bogus": 1})

	if called {
		t.Fatal("handler ran despite unknown argument; server is not enforcing strict top-level inputs")
	}
}
