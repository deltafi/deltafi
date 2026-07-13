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
// ABOUTME: Tests for events MCP tool URL/query-string construction
// ABOUTME: Verifies filter encoding, paging defaults, and value-type validation

package tools

import (
	"net/url"
	"strings"
	"testing"
)

func parseEventsPath(t *testing.T, path string) url.Values {
	t.Helper()
	idx := strings.Index(path, "?")
	if idx == -1 {
		return url.Values{}
	}
	values, err := url.ParseQuery(path[idx+1:])
	if err != nil {
		t.Fatalf("invalid query string %q: %v", path, err)
	}
	return values
}

func TestBuildEventsPath_NoArgs(t *testing.T) {
	req := newCallToolRequest(map[string]any{})
	got, err := buildEventsPath("/api/v2/events", req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "/api/v2/events" {
		t.Errorf("expected bare base path, got %q", got)
	}
}

func TestBuildEventsPath_OffsetSize(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"offset": float64(40),
		"size":   float64(10),
	})
	got, err := buildEventsPath("/api/v2/events", req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	values := parseEventsPath(t, got)
	if values.Get("offset") != "40" {
		t.Errorf("offset: got %q", values.Get("offset"))
	}
	if values.Get("size") != "10" {
		t.Errorf("size: got %q", values.Get("size"))
	}
}

func TestBuildEventsPath_FiltersMixedTypes(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"filters": map[string]any{
			"severity":     "error",
			"acknowledged": false,
			"id":           float64(1024),
			"content":      nil,
		},
	})
	got, err := buildEventsPath("/api/v2/events", req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	values := parseEventsPath(t, got)
	if values.Get("severity") != "error" {
		t.Errorf("severity: got %q", values.Get("severity"))
	}
	if values.Get("acknowledged") != "false" {
		t.Errorf("acknowledged: got %q", values.Get("acknowledged"))
	}
	if values.Get("id") != "1024" {
		t.Errorf("id: got %q", values.Get("id"))
	}
	if _, present := values["content"]; present {
		t.Errorf("nil filter should be skipped, got %v", values["content"])
	}
}

func TestBuildEventsPath_RejectsUnsupportedFilterValue(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"filters": map[string]any{
			"content": []any{"a", "b"},
		},
	})
	_, err := buildEventsPath("/api/v2/events", req)
	if err == nil {
		t.Fatal("expected error for array filter value")
	}
	if !strings.Contains(err.Error(), "content") {
		t.Errorf("expected error to mention the offending key, got %v", err)
	}
}

func TestBuildEventsPath_RejectsUnsupportedFilter(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"filters": map[string]any{"severty": "ERROR"},
	})
	_, err := buildEventsPath("/api/v2/events", req)
	if err == nil || !strings.Contains(err.Error(), "unsupported event filter") {
		t.Fatalf("expected unsupported-filter error, got %v", err)
	}
}

func TestBuildEventsPath_RejectsNonObjectFilters(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"filters": "not-an-object",
	})
	_, err := buildEventsPath("/api/v2/events", req)
	if err == nil {
		t.Fatal("expected error when filters is not an object")
	}
}

func TestBuildEventsPath_FloatPreservesInteger(t *testing.T) {
	req := newCallToolRequest(map[string]any{
		"filters": map[string]any{
			"id": float64(7),
		},
	})
	got, err := buildEventsPath("/api/v2/events", req)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	values := parseEventsPath(t, got)
	if values.Get("id") != "7" {
		t.Errorf("expected integer-style encoding 7, got %q", values.Get("id"))
	}
}
