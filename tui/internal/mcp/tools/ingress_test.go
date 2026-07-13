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
// ABOUTME: Tests for ingress MCP tool local-filesystem path resolution
// ABOUTME: Verifies tilde expansion, absolute path normalization, and rejection of empty paths

package tools

import (
	"path/filepath"
	"strings"
	"testing"

	"github.com/mark3labs/mcp-go/mcp"
)

func TestResolveLocalPath_Empty(t *testing.T) {
	_, err := resolveLocalPath("")
	if err == nil {
		t.Fatal("expected error for empty path")
	}
}

func TestDecodeInlineContent_AllowsEmptyPayloads(t *testing.T) {
	for _, args := range []map[string]any{{"content": ""}, {"content_base64": ""}} {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: args}}
		data, errResult := decodeInlineContent(req)
		if errResult != nil {
			t.Fatalf("empty payload was rejected: %s", resultText(t, errResult))
		}
		if len(data) != 0 {
			t.Fatalf("expected empty payload, got %q", data)
		}
	}
}

func TestIngressMetadata_MarshalsObject(t *testing.T) {
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{
		"metadata": map[string]any{"source": "mcp", "attempt": float64(2)},
	}}}
	metadata, errResult := ingressMetadata(req)
	if errResult != nil {
		t.Fatalf("metadata was rejected: %s", resultText(t, errResult))
	}
	if metadata != `{"attempt":2,"source":"mcp"}` {
		t.Fatalf("unexpected metadata JSON: %s", metadata)
	}
}

func TestResolveLocalPath_Tilde(t *testing.T) {
	t.Setenv("HOME", "/tmp/fakehome")

	got, err := resolveLocalPath("~")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "/tmp/fakehome" {
		t.Errorf("expected /tmp/fakehome, got %q", got)
	}
}

func TestResolveLocalPath_TildeWithSubpath(t *testing.T) {
	t.Setenv("HOME", "/tmp/fakehome")

	got, err := resolveLocalPath("~/data/file.txt")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "/tmp/fakehome/data/file.txt" {
		t.Errorf("expected /tmp/fakehome/data/file.txt, got %q", got)
	}
}

func TestResolveLocalPath_Absolute(t *testing.T) {
	got, err := resolveLocalPath("/etc/hostname")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "/etc/hostname" {
		t.Errorf("expected /etc/hostname, got %q", got)
	}
}

func TestResolveLocalPath_Relative(t *testing.T) {
	got, err := resolveLocalPath("relative/path.txt")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !filepath.IsAbs(got) {
		t.Errorf("expected absolute path, got %q", got)
	}
	if !strings.HasSuffix(got, "relative/path.txt") {
		t.Errorf("expected suffix relative/path.txt, got %q", got)
	}
}

func TestResolveLocalPath_TildeOnlyExpandsLeading(t *testing.T) {
	t.Setenv("HOME", "/tmp/fakehome")

	got, err := resolveLocalPath("data/~/file.txt")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if strings.Contains(got, "/tmp/fakehome") {
		t.Errorf("middle ~ should not expand, got %q", got)
	}
	if !strings.HasSuffix(got, "data/~/file.txt") {
		t.Errorf("expected suffix data/~/file.txt, got %q", got)
	}
}
