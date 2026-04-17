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
// ABOUTME: Tests for Deltafile content access, metadata, and parameter parsing.
// ABOUTME: Verifies ContentAt, FirstContent, GetMetadata, and parseRawParams.

package actionkit

import "testing"

func TestDeltafile_HasContent(t *testing.T) {
	tc := NewTestContext("did")
	empty := tc.NewDeltafile()
	if empty.HasContent() {
		t.Error("HasContent() = true for empty deltafile")
	}

	withContent := tc.NewDeltafile(ActionContent{Name: "file.txt"})
	if !withContent.HasContent() {
		t.Error("HasContent() = false when content exists")
	}
}

func TestDeltafile_ContentAt(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile(
		ActionContent{Name: "first.txt"},
		ActionContent{Name: "second.txt"},
	)

	c, err := df.ContentAt(0)
	if err != nil {
		t.Fatalf("ContentAt(0): %v", err)
	}
	if c.Name != "first.txt" {
		t.Errorf("ContentAt(0).Name = %q", c.Name)
	}

	c, err = df.ContentAt(1)
	if err != nil {
		t.Fatalf("ContentAt(1): %v", err)
	}
	if c.Name != "second.txt" {
		t.Errorf("ContentAt(1).Name = %q", c.Name)
	}
}

func TestDeltafile_ContentAt_OutOfBounds(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile(ActionContent{Name: "only.txt"})

	_, err := df.ContentAt(1)
	if err == nil {
		t.Fatal("ContentAt(1) should error for single-item content")
	}
	ece, ok := err.(*ExpectedContentError)
	if !ok {
		t.Fatalf("expected *ExpectedContentError, got %T", err)
	}
	if ece.Index != 1 || ece.Count != 1 {
		t.Errorf("ExpectedContentError = {Index:%d, Count:%d}, want {1, 1}", ece.Index, ece.Count)
	}

	_, err = df.ContentAt(-1)
	if err == nil {
		t.Fatal("ContentAt(-1) should error")
	}
}

func TestDeltafile_ContentAt_Empty(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile()
	_, err := df.ContentAt(0)
	if err == nil {
		t.Fatal("ContentAt(0) should error for empty content")
	}
}

func TestDeltafile_ContentNamed(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile(
		ActionContent{Name: "alpha.txt"},
		ActionContent{Name: "beta.txt"},
		ActionContent{Name: "gamma.txt"},
	)

	c, err := df.ContentNamed("beta.txt")
	if err != nil {
		t.Fatalf("ContentNamed(beta.txt): %v", err)
	}
	if c.Name != "beta.txt" {
		t.Errorf("ContentNamed(beta.txt).Name = %q", c.Name)
	}
}

func TestDeltafile_ContentNamed_NotFound(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile(ActionContent{Name: "only.txt"})
	_, err := df.ContentNamed("missing.txt")
	if err == nil {
		t.Error("ContentNamed(missing.txt) should error")
	}
}

func TestDeltafile_GetMetadata(t *testing.T) {
	tc := NewTestContext("did")
	// Set metadata on the input side by creating with metadata
	df2 := newDeltafile(tc.ActionContext, nil, map[string]string{"key1": "val1", "key2": "val2"})

	val, err := df2.GetMetadata("key1")
	if err != nil {
		t.Fatalf("GetMetadata(key1): %v", err)
	}
	if val != "val1" {
		t.Errorf("GetMetadata(key1) = %q", val)
	}
}

func TestDeltafile_GetMetadata_Missing(t *testing.T) {
	tc := NewTestContext("did")
	df := newDeltafile(tc.ActionContext, nil, map[string]string{"key1": "val1"})

	_, err := df.GetMetadata("missing")
	if err == nil {
		t.Fatal("GetMetadata(missing) should error")
	}
	mme, ok := err.(*MissingMetadataError)
	if !ok {
		t.Fatalf("expected *MissingMetadataError, got %T", err)
	}
	if mme.Key != "missing" {
		t.Errorf("MissingMetadataError.Key = %q", mme.Key)
	}
}

func TestDeltafile_GetMetadataOrDefault(t *testing.T) {
	tc := NewTestContext("did")
	df := newDeltafile(tc.ActionContext, nil, map[string]string{"key1": "val1"})

	if v := df.GetMetadataOrDefault("key1", "default"); v != "val1" {
		t.Errorf("GetMetadataOrDefault(key1) = %q, want val1", v)
	}
	if v := df.GetMetadataOrDefault("missing", "default"); v != "default" {
		t.Errorf("GetMetadataOrDefault(missing) = %q, want default", v)
	}
}

func TestDeltafile_FirstContent(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile(
		ActionContent{Name: "first.txt"},
		ActionContent{Name: "second.txt"},
	)

	fc, err := df.FirstContent()
	if err != nil {
		t.Fatalf("FirstContent(): %v", err)
	}
	if fc.Name != "first.txt" {
		t.Errorf("FirstContent().Name = %q, want %q", fc.Name, "first.txt")
	}
}

func TestDeltafile_FirstContent_Empty(t *testing.T) {
	tc := NewTestContext("did")
	df := tc.NewDeltafile()
	_, err := df.FirstContent()
	if err == nil {
		t.Error("FirstContent() should error for empty content")
	}
}

type sampleParams struct {
	Name    string `json:"name"`
	Count   int    `json:"count"`
	Enabled bool   `json:"enabled"`
}

func TestParseRawParams(t *testing.T) {
	raw := map[string]any{
		"name":    "hello",
		"count":   float64(42), // JSON numbers are float64
		"enabled": true,
	}

	var params sampleParams
	if err := parseRawParams(raw, &params); err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Name != "hello" {
		t.Errorf("Name = %q, want %q", params.Name, "hello")
	}
	if params.Count != 42 {
		t.Errorf("Count = %d, want 42", params.Count)
	}
	if !params.Enabled {
		t.Error("Enabled = false, want true")
	}
}

func TestParseRawParams_NilMap(t *testing.T) {
	var params sampleParams
	err := parseRawParams(nil, &params)
	if err != nil {
		t.Fatalf("parseRawParams nil: %v", err)
	}
	if params.Name != "" {
		t.Errorf("Name = %q, want empty", params.Name)
	}
}

func TestParseRawParams_PreservesDefaults(t *testing.T) {
	// When params only has partial keys, json.Unmarshal leaves missing fields untouched
	raw := map[string]any{
		"name": "partial",
	}

	var params sampleParams
	params.Count = 99 // pre-set default
	if err := parseRawParams(raw, &params); err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Name != "partial" {
		t.Errorf("Name = %q, want %q", params.Name, "partial")
	}
	// json.Unmarshal leaves fields not present in JSON untouched
	if params.Count != 99 {
		t.Errorf("Count = %d, want 99 (Unmarshal preserves unset fields)", params.Count)
	}
}

func TestParseRawParams_StringCoercion(t *testing.T) {
	// DeltaFi core may send all params as strings after variable substitution
	raw := map[string]any{
		"name":    "hello",
		"count":   "42",
		"enabled": "true",
	}

	var params sampleParams
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams with string values: %v", err)
	}
	if params.Name != "hello" {
		t.Errorf("Name = %q, want %q", params.Name, "hello")
	}
	if params.Count != 42 {
		t.Errorf("Count = %d, want 42", params.Count)
	}
	if !params.Enabled {
		t.Error("Enabled = false, want true")
	}
}

func TestParseRawParams_StringCoercionFalse(t *testing.T) {
	raw := map[string]any{
		"enabled": "false",
	}

	var params sampleParams
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Enabled {
		t.Error("Enabled = true, want false")
	}
}

func TestParseRawParams_StringCoercionFloat(t *testing.T) {
	type floatParams struct {
		Rate float64 `json:"rate"`
	}
	raw := map[string]any{
		"rate": "3.14",
	}

	var params floatParams
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Rate != 3.14 {
		t.Errorf("Rate = %f, want 3.14", params.Rate)
	}
}

func TestParseRawParams_MixedNativeAndStringValues(t *testing.T) {
	// Some values native, some strings — both should work
	raw := map[string]any{
		"name":    "mixed",
		"count":   float64(10),
		"enabled": "true",
	}

	var params sampleParams
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Count != 10 {
		t.Errorf("Count = %d, want 10", params.Count)
	}
	if !params.Enabled {
		t.Error("Enabled = false, want true")
	}
}

func TestCoerceParams_NestedMap(t *testing.T) {
	raw := map[string]any{
		"outer": map[string]any{
			"innerNum":  "99",
			"innerBool": "true",
			"innerStr":  "hello",
		},
	}

	// Without type info, best-effort coercion is applied
	coerced := coerceParams(raw, nil)
	inner := coerced["outer"].(map[string]any)
	if inner["innerNum"] != int64(99) {
		t.Errorf("innerNum = %v (%T)", inner["innerNum"], inner["innerNum"])
	}
	if inner["innerBool"] != true {
		t.Errorf("innerBool = %v", inner["innerBool"])
	}
	if inner["innerStr"] != "hello" {
		t.Errorf("innerStr = %v", inner["innerStr"])
	}
}

func TestCoerceParams_NonStringValuesPassThrough(t *testing.T) {
	raw := map[string]any{
		"num":   float64(42),
		"bool":  true,
		"slice": []any{"a", "b"},
		"nil":   nil,
	}

	coerced := coerceParams(raw, nil)
	if coerced["num"] != float64(42) {
		t.Errorf("num = %v", coerced["num"])
	}
	if coerced["bool"] != true {
		t.Errorf("bool = %v", coerced["bool"])
	}
	if coerced["nil"] != nil {
		t.Errorf("nil = %v", coerced["nil"])
	}
}

func TestParseRawParams_NumericStringIntoStringField(t *testing.T) {
	// Reproduces the bug: string fields with numeric-looking defaults (e.g. "44")
	// must not be coerced to numbers
	type prefixParams struct {
		Name           string `json:"name"`
		Count          int    `json:"count"`
		DidErrorPrefix string `json:"didErrorPrefix"`
	}

	raw := map[string]any{
		"name":           "test",
		"count":          "10",
		"didErrorPrefix": "44",
	}

	var params prefixParams
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.DidErrorPrefix != "44" {
		t.Errorf("DidErrorPrefix = %q, want %q", params.DidErrorPrefix, "44")
	}
	if params.Count != 10 {
		t.Errorf("Count = %d, want 10", params.Count)
	}
}

func TestParseRawParams_NumericValueIntoStringField(t *testing.T) {
	// When core sends a float64 for a string-typed field, it should be converted
	type mixedParams struct {
		Label string `json:"label"`
	}

	raw := map[string]any{
		"label": float64(42),
	}

	var params mixedParams
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Label != "42" {
		t.Errorf("Label = %q, want %q", params.Label, "42")
	}
}

func TestParseRawParams_NestedStructCoercion(t *testing.T) {
	type inner struct {
		Threshold int    `json:"threshold"`
		Tag       string `json:"tag"`
	}
	type outer struct {
		Inner inner `json:"inner"`
	}

	raw := map[string]any{
		"inner": map[string]any{
			"threshold": "100",
			"tag":       "55",
		},
	}

	var params outer
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Inner.Threshold != 100 {
		t.Errorf("Inner.Threshold = %d, want 100", params.Inner.Threshold)
	}
	if params.Inner.Tag != "55" {
		t.Errorf("Inner.Tag = %q, want %q", params.Inner.Tag, "55")
	}
}

func TestParseRawParams_PointerStructCoercion(t *testing.T) {
	type inner struct {
		Value string `json:"value"`
	}
	type outer struct {
		Inner *inner `json:"inner"`
	}

	raw := map[string]any{
		"inner": map[string]any{
			"value": "99",
		},
	}

	var params outer
	err := parseRawParams(raw, &params)
	if err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if params.Inner == nil {
		t.Fatal("Inner is nil")
	}
	if params.Inner.Value != "99" {
		t.Errorf("Inner.Value = %q, want %q", params.Inner.Value, "99")
	}
}

func TestParseRawParams_SliceStringCoercion(t *testing.T) {
	type params struct {
		Counts []int    `json:"counts"`
		Flags  []bool   `json:"flags"`
		Names  []string `json:"names"`
	}

	raw := map[string]any{
		"counts": []any{"1", "2", "3"},
		"flags":  []any{"true", "false"},
		"names":  []any{"alice", "bob"},
	}

	var p params
	if err := parseRawParams(raw, &p); err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if len(p.Counts) != 3 || p.Counts[0] != 1 || p.Counts[1] != 2 || p.Counts[2] != 3 {
		t.Errorf("Counts = %v, want [1 2 3]", p.Counts)
	}
	if len(p.Flags) != 2 || p.Flags[0] != true || p.Flags[1] != false {
		t.Errorf("Flags = %v, want [true false]", p.Flags)
	}
	if len(p.Names) != 2 || p.Names[0] != "alice" || p.Names[1] != "bob" {
		t.Errorf("Names = %v, want [alice bob]", p.Names)
	}
}

func TestParseRawParams_SliceFloatToString(t *testing.T) {
	type params struct {
		Labels []string `json:"labels"`
	}

	raw := map[string]any{
		"labels": []any{1.5, 2.0},
	}

	var p params
	if err := parseRawParams(raw, &p); err != nil {
		t.Fatalf("parseRawParams: %v", err)
	}
	if len(p.Labels) != 2 || p.Labels[0] != "1.5" || p.Labels[1] != "2" {
		t.Errorf("Labels = %v, want [1.5 2]", p.Labels)
	}
}
