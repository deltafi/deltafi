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
// ABOUTME: Tests for the Row read-only lookup row type.
// ABOUTME: Covers Get/Has/Len, iteration, JSON equivalence, and zero-copy semantics.

package actionkit

import (
	"encoding/json"
	"maps"
	"testing"
)

func TestRow_GetHasLen(t *testing.T) {
	r := NewRow(map[string]string{"color": "red", "team": "McLaren"})

	v, ok := r.Get("color")
	if !ok || v != "red" {
		t.Errorf("Get(color) = %q, %v; want red, true", v, ok)
	}
	_, ok = r.Get("missing")
	if ok {
		t.Error("Get(missing) should return false")
	}
	if !r.Has("team") {
		t.Error("Has(team) should be true")
	}
	if r.Has("missing") {
		t.Error("Has(missing) should be false")
	}
	if r.Len() != 2 {
		t.Errorf("Len() = %d, want 2", r.Len())
	}
}

func TestRow_All(t *testing.T) {
	m := map[string]string{"a": "1", "b": "2", "c": "3"}
	r := NewRow(m)

	got := maps.Collect(r.All())
	if len(got) != 3 || got["a"] != "1" || got["b"] != "2" || got["c"] != "3" {
		t.Errorf("All() = %v, want %v", got, m)
	}
}

// TestRow_MarshalJSON verifies that json.Marshal on a []Row produces byte-for-byte
// identical output to json.Marshal on []map[string]string with the same data.
// This is the REQUIRED invariant for lookup-table upload to work correctly.
func TestRow_MarshalJSON(t *testing.T) {
	m := map[string]string{"driver": "Norris", "team": "McLaren"}
	row := NewRow(m)

	rowJSON, err := json.Marshal([]Row{row})
	if err != nil {
		t.Fatalf("Marshal []Row: %v", err)
	}
	mapJSON, err := json.Marshal([]map[string]string{m})
	if err != nil {
		t.Fatalf("Marshal []map: %v", err)
	}
	if string(rowJSON) != string(mapJSON) {
		t.Errorf("Row JSON = %s\n want   %s", rowJSON, mapJSON)
	}
}

// TestRow_ZeroCopy proves that NewRow stores the map by reference (no copy).
// Mutations to the original map are visible through Get, which would not be
// true if NewRow made a defensive copy.
func TestRow_ZeroCopy(t *testing.T) {
	m := map[string]string{"color": "red"}
	r := NewRow(m)

	m["color"] = "blue"
	v, _ := r.Get("color")
	if v != "blue" {
		t.Errorf("Get(color) = %q; want blue (proves zero-copy: NewRow stores map by reference)", v)
	}
}
