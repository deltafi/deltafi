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
// ABOUTME: Tests for shared MCP tool argument-parsing helpers
// ABOUTME: Covers UUID parsing, JSON decoding, and scalar-to-string coercion

package tools

import (
	"testing"

	"github.com/google/uuid"
	"github.com/mark3labs/mcp-go/mcp"
)

func TestOptionalNonNegativeInt(t *testing.T) {
	t.Run("absent returns nil", func(t *testing.T) {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{}}}
		got, err := optionalNonNegativeInt(req, "offset")
		if err != nil || got != nil {
			t.Fatalf("got (%v, %v), want (nil, nil)", got, err)
		}
	})
	t.Run("valid value", func(t *testing.T) {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"offset": float64(25)}}}
		got, err := optionalNonNegativeInt(req, "offset")
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if got == nil || *got != 25 {
			t.Fatalf("got %v, want 25", got)
		}
	})
	t.Run("fractional rejected", func(t *testing.T) {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"offset": float64(3.9)}}}
		got, err := optionalNonNegativeInt(req, "offset")
		if err == nil || got != nil {
			t.Fatalf("got (%v, %v), want (nil, error)", got, err)
		}
	})
	t.Run("negative rejected", func(t *testing.T) {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"offset": float64(-1)}}}
		got, err := optionalNonNegativeInt(req, "offset")
		if err == nil {
			t.Fatalf("expected error for negative, got value %v", got)
		}
		if got != nil {
			t.Errorf("expected nil value on error, got %v", *got)
		}
	})
	t.Run("wrong type rejected", func(t *testing.T) {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"offset": "25"}}}
		got, err := optionalNonNegativeInt(req, "offset")
		if err == nil || got != nil {
			t.Fatalf("got (%v, %v), want (nil, error)", got, err)
		}
	})
	t.Run("overflow rejected", func(t *testing.T) {
		req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"offset": 1e19}}}
		got, err := optionalNonNegativeInt(req, "offset")
		if err == nil || got != nil {
			t.Fatalf("got (%v, %v), want (nil, error) for out-of-range value", got, err)
		}
	})
}

func TestRequireNonEmptyString(t *testing.T) {
	cases := []struct {
		name    string
		args    map[string]any
		wantErr bool
	}{
		{"present", map[string]any{"x": "hello"}, false},
		{"empty", map[string]any{"x": ""}, true},
		{"missing", map[string]any{}, true},
		{"wrong type", map[string]any{"x": 5}, true},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: tc.args}}
			got, errResult := requireNonEmptyString(req, "x")
			if tc.wantErr {
				if errResult == nil {
					t.Fatalf("expected error result, got value %q", got)
				}
				if msg := resultText(t, errResult); msg != "x parameter is required" {
					t.Errorf("got %q", msg)
				}
				return
			}
			if errResult != nil {
				t.Fatalf("unexpected error result: %v", resultText(t, errResult))
			}
			if got != "hello" {
				t.Errorf("got %q", got)
			}
		})
	}
}

func TestRequireUUID_Valid(t *testing.T) {
	id := uuid.New()
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"id": id.String()}}}
	got, errResult := requireUUID(req, "id", "snapshot ID")
	if errResult != nil {
		t.Fatalf("unexpected error result: %v", resultText(t, errResult))
	}
	if got != id {
		t.Errorf("got %v, want %v", got, id)
	}
}

func TestRequireUUID_Missing(t *testing.T) {
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{}}}
	_, errResult := requireUUID(req, "id", "snapshot ID")
	if errResult == nil {
		t.Fatal("expected error result for missing argument")
	}
	if msg := resultText(t, errResult); msg != "id parameter is required" {
		t.Errorf("got %q", msg)
	}
}

func TestRequireUUID_Invalid(t *testing.T) {
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{"id": "not-a-uuid"}}}
	_, errResult := requireUUID(req, "id", "snapshot ID")
	if errResult == nil {
		t.Fatal("expected error result for invalid UUID")
	}
	if msg := resultText(t, errResult); msg == "" || msg[:8] != "invalid " {
		t.Errorf("got %q", msg)
	}
}

func TestDecodeJSON_RoundTrip(t *testing.T) {
	type target struct {
		Name  string `json:"name"`
		Count int    `json:"count"`
	}
	raw := map[string]any{"name": "x", "count": float64(3)}
	var out target
	if err := decodeJSON(raw, &out); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out.Name != "x" || out.Count != 3 {
		t.Errorf("got %+v", out)
	}
}

func TestScalarToString(t *testing.T) {
	cases := []struct {
		name     string
		in       any
		wantStr  string
		wantNull bool
		wantOK   bool
	}{
		{"nil", nil, "", true, true},
		{"string", "hello", "hello", false, true},
		{"bool", true, "true", false, true},
		{"int-like float", float64(30), "30", false, true},
		{"fractional float", float64(1.5), "1.5", false, true},
		{"unsupported", []any{1, 2}, "", false, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			s, isNull, ok := scalarToString(tc.in)
			if s != tc.wantStr || isNull != tc.wantNull || ok != tc.wantOK {
				t.Errorf("got (%q, %v, %v), want (%q, %v, %v)", s, isNull, ok, tc.wantStr, tc.wantNull, tc.wantOK)
			}
		})
	}
}
