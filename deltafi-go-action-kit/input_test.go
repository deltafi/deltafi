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
// ABOUTME: Tests for input types and parameter parsing.
// ABOUTME: Verifies FirstContent, ParseParameters, and ParseParams generic function.

package actionkit

import "testing"

func TestTransformInput_HasContent(t *testing.T) {
	empty := TransformInput{}
	if empty.HasContent() {
		t.Error("HasContent() = true for empty input")
	}

	withContent := TransformInput{
		Content: []ActionContent{{Name: "file.txt"}},
	}
	if !withContent.HasContent() {
		t.Error("HasContent() = false when content exists")
	}
}

func TestTransformInput_ContentAt(t *testing.T) {
	input := TransformInput{
		Content: []ActionContent{
			{Name: "first.txt"},
			{Name: "second.txt"},
		},
	}

	c, err := input.ContentAt(0)
	if err != nil {
		t.Fatalf("ContentAt(0): %v", err)
	}
	if c.Name != "first.txt" {
		t.Errorf("ContentAt(0).Name = %q", c.Name)
	}

	c, err = input.ContentAt(1)
	if err != nil {
		t.Fatalf("ContentAt(1): %v", err)
	}
	if c.Name != "second.txt" {
		t.Errorf("ContentAt(1).Name = %q", c.Name)
	}
}

func TestTransformInput_ContentAt_OutOfBounds(t *testing.T) {
	input := TransformInput{
		Content: []ActionContent{{Name: "only.txt"}},
	}

	_, err := input.ContentAt(1)
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

	_, err = input.ContentAt(-1)
	if err == nil {
		t.Fatal("ContentAt(-1) should error")
	}
}

func TestTransformInput_ContentAt_Empty(t *testing.T) {
	input := TransformInput{}
	_, err := input.ContentAt(0)
	if err == nil {
		t.Fatal("ContentAt(0) should error for empty content")
	}
}

func TestTransformInput_ContentNamed(t *testing.T) {
	input := TransformInput{
		Content: []ActionContent{
			{Name: "alpha.txt"},
			{Name: "beta.txt"},
			{Name: "gamma.txt"},
		},
	}

	c := input.ContentNamed("beta.txt")
	if c == nil {
		t.Fatal("ContentNamed(beta.txt) returned nil")
	}
	if c.Name != "beta.txt" {
		t.Errorf("ContentNamed(beta.txt).Name = %q", c.Name)
	}
}

func TestTransformInput_ContentNamed_NotFound(t *testing.T) {
	input := TransformInput{
		Content: []ActionContent{{Name: "only.txt"}},
	}
	if c := input.ContentNamed("missing.txt"); c != nil {
		t.Errorf("ContentNamed(missing.txt) = %v, want nil", c)
	}
}

func TestTransformInput_GetMetadata(t *testing.T) {
	input := TransformInput{
		Metadata: map[string]string{"key1": "val1", "key2": "val2"},
	}

	val, err := input.GetMetadata("key1")
	if err != nil {
		t.Fatalf("GetMetadata(key1): %v", err)
	}
	if val != "val1" {
		t.Errorf("GetMetadata(key1) = %q", val)
	}
}

func TestTransformInput_GetMetadata_Missing(t *testing.T) {
	input := TransformInput{
		Metadata: map[string]string{"key1": "val1"},
	}

	_, err := input.GetMetadata("missing")
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

func TestTransformInput_GetMetadataOrDefault(t *testing.T) {
	input := TransformInput{
		Metadata: map[string]string{"key1": "val1"},
	}

	if v := input.GetMetadataOrDefault("key1", "default"); v != "val1" {
		t.Errorf("GetMetadataOrDefault(key1) = %q, want val1", v)
	}
	if v := input.GetMetadataOrDefault("missing", "default"); v != "default" {
		t.Errorf("GetMetadataOrDefault(missing) = %q, want default", v)
	}
}

func TestEgressInput_HasContent(t *testing.T) {
	empty := EgressInput{}
	if empty.HasContent() {
		t.Error("HasContent() = true for empty input")
	}

	withContent := EgressInput{
		Content: []ActionContent{{Name: "file.txt"}},
	}
	if !withContent.HasContent() {
		t.Error("HasContent() = false when content exists")
	}
}

func TestEgressInput_GetMetadata(t *testing.T) {
	input := EgressInput{
		Metadata: map[string]string{"key1": "val1"},
	}

	val, err := input.GetMetadata("key1")
	if err != nil {
		t.Fatalf("GetMetadata(key1): %v", err)
	}
	if val != "val1" {
		t.Errorf("GetMetadata(key1) = %q", val)
	}

	_, err = input.GetMetadata("missing")
	if err == nil {
		t.Fatal("GetMetadata(missing) should error")
	}
}

func TestEgressInput_GetMetadataOrDefault(t *testing.T) {
	input := EgressInput{
		Metadata: map[string]string{"key1": "val1"},
	}

	if v := input.GetMetadataOrDefault("key1", "default"); v != "val1" {
		t.Errorf("GetMetadataOrDefault(key1) = %q, want val1", v)
	}
	if v := input.GetMetadataOrDefault("missing", "default"); v != "default" {
		t.Errorf("GetMetadataOrDefault(missing) = %q, want default", v)
	}
}

func TestTransformInput_FirstContent(t *testing.T) {
	input := TransformInput{
		Content: []ActionContent{
			{Name: "first.txt"},
			{Name: "second.txt"},
		},
	}

	fc := input.FirstContent()
	if fc == nil {
		t.Fatal("FirstContent() returned nil")
	}
	if fc.Name != "first.txt" {
		t.Errorf("FirstContent().Name = %q, want %q", fc.Name, "first.txt")
	}
}

func TestTransformInput_FirstContent_Empty(t *testing.T) {
	input := TransformInput{}
	if fc := input.FirstContent(); fc != nil {
		t.Errorf("FirstContent() = %v, want nil", fc)
	}
}

func TestEgressInput_FirstContent(t *testing.T) {
	input := EgressInput{
		Content: []ActionContent{
			{Name: "egress.txt"},
		},
	}

	fc := input.FirstContent()
	if fc == nil {
		t.Fatal("FirstContent() returned nil")
	}
	if fc.Name != "egress.txt" {
		t.Errorf("FirstContent().Name = %q, want %q", fc.Name, "egress.txt")
	}
}

func TestEgressInput_FirstContent_Empty(t *testing.T) {
	input := EgressInput{}
	if fc := input.FirstContent(); fc != nil {
		t.Errorf("FirstContent() = %v, want nil", fc)
	}
}

func TestEgressInput_ContentAt(t *testing.T) {
	input := EgressInput{
		Content: []ActionContent{
			{Name: "first.txt"},
			{Name: "second.txt"},
		},
	}

	c, err := input.ContentAt(0)
	if err != nil {
		t.Fatalf("ContentAt(0): %v", err)
	}
	if c.Name != "first.txt" {
		t.Errorf("ContentAt(0).Name = %q", c.Name)
	}

	c, err = input.ContentAt(1)
	if err != nil {
		t.Fatalf("ContentAt(1): %v", err)
	}
	if c.Name != "second.txt" {
		t.Errorf("ContentAt(1).Name = %q", c.Name)
	}
}

func TestEgressInput_ContentAt_OutOfBounds(t *testing.T) {
	input := EgressInput{
		Content: []ActionContent{{Name: "only.txt"}},
	}

	_, err := input.ContentAt(1)
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

	_, err = input.ContentAt(-1)
	if err == nil {
		t.Fatal("ContentAt(-1) should error")
	}
}

func TestEgressInput_ContentNamed(t *testing.T) {
	input := EgressInput{
		Content: []ActionContent{
			{Name: "alpha.txt"},
			{Name: "beta.txt"},
		},
	}

	c := input.ContentNamed("beta.txt")
	if c == nil {
		t.Fatal("ContentNamed(beta.txt) returned nil")
	}
	if c.Name != "beta.txt" {
		t.Errorf("ContentNamed(beta.txt).Name = %q", c.Name)
	}
}

func TestEgressInput_ContentNamed_NotFound(t *testing.T) {
	input := EgressInput{
		Content: []ActionContent{{Name: "only.txt"}},
	}
	if c := input.ContentNamed("missing.txt"); c != nil {
		t.Errorf("ContentNamed(missing.txt) = %v, want nil", c)
	}
}

type sampleParams struct {
	Name    string `json:"name"`
	Count   int    `json:"count"`
	Enabled bool   `json:"enabled"`
}

func TestTransformInput_ParseParameters(t *testing.T) {
	input := TransformInput{
		Params: map[string]any{
			"name":    "hello",
			"count":   float64(42), // JSON numbers are float64
			"enabled": true,
		},
	}

	var params sampleParams
	if err := input.ParseParameters(&params); err != nil {
		t.Fatalf("ParseParameters: %v", err)
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

func TestEgressInput_ParseParameters(t *testing.T) {
	input := EgressInput{
		Params: map[string]any{
			"name": "egress-param",
		},
	}

	var params sampleParams
	if err := input.ParseParameters(&params); err != nil {
		t.Fatalf("ParseParameters: %v", err)
	}
	if params.Name != "egress-param" {
		t.Errorf("Name = %q, want %q", params.Name, "egress-param")
	}
}

func TestIngressInput_ParseParameters(t *testing.T) {
	input := IngressInput{
		Params: map[string]any{
			"count": float64(7),
		},
	}

	var params sampleParams
	if err := input.ParseParameters(&params); err != nil {
		t.Fatalf("ParseParameters: %v", err)
	}
	if params.Count != 7 {
		t.Errorf("Count = %d, want 7", params.Count)
	}
}

func TestParseParameters_NilParams(t *testing.T) {
	input := TransformInput{Params: nil}
	var params sampleParams
	if err := input.ParseParameters(&params); err != nil {
		t.Fatalf("ParseParameters with nil: %v", err)
	}
	// Should leave dest at zero values
	if params.Name != "" {
		t.Errorf("Name = %q, want empty", params.Name)
	}
}

func TestParseParameters_PreservesDefaults(t *testing.T) {
	// When params only has partial keys, json.Unmarshal leaves missing fields untouched
	input := TransformInput{
		Params: map[string]any{
			"name": "partial",
		},
	}

	var params sampleParams
	params.Count = 99 // pre-set default
	if err := input.ParseParameters(&params); err != nil {
		t.Fatalf("ParseParameters: %v", err)
	}
	if params.Name != "partial" {
		t.Errorf("Name = %q, want %q", params.Name, "partial")
	}
	// json.Unmarshal leaves fields not present in JSON untouched
	if params.Count != 99 {
		t.Errorf("Count = %d, want 99 (Unmarshal preserves unset fields)", params.Count)
	}
}

func TestParseParams_Generic(t *testing.T) {
	raw := map[string]any{
		"name":    "generic",
		"count":   float64(10),
		"enabled": true,
	}

	params, err := ParseParams[sampleParams](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Name != "generic" {
		t.Errorf("Name = %q, want %q", params.Name, "generic")
	}
	if params.Count != 10 {
		t.Errorf("Count = %d, want 10", params.Count)
	}
	if !params.Enabled {
		t.Error("Enabled = false, want true")
	}
}

func TestParseParams_NilMap(t *testing.T) {
	params, err := ParseParams[sampleParams](nil)
	if err != nil {
		t.Fatalf("ParseParams nil: %v", err)
	}
	if params.Name != "" {
		t.Errorf("Name = %q, want empty", params.Name)
	}
}

func TestParseParams_StringCoercion(t *testing.T) {
	// DeltaFi core may send all params as strings after variable substitution
	raw := map[string]any{
		"name":    "hello",
		"count":   "42",
		"enabled": "true",
	}

	params, err := ParseParams[sampleParams](raw)
	if err != nil {
		t.Fatalf("ParseParams with string values: %v", err)
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

func TestParseParams_StringCoercionFalse(t *testing.T) {
	raw := map[string]any{
		"enabled": "false",
	}

	params, err := ParseParams[sampleParams](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Enabled {
		t.Error("Enabled = true, want false")
	}
}

func TestParseParams_StringCoercionFloat(t *testing.T) {
	type floatParams struct {
		Rate float64 `json:"rate"`
	}
	raw := map[string]any{
		"rate": "3.14",
	}

	params, err := ParseParams[floatParams](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Rate != 3.14 {
		t.Errorf("Rate = %f, want 3.14", params.Rate)
	}
}

func TestParseParams_MixedNativeAndStringValues(t *testing.T) {
	// Some values native, some strings — both should work
	raw := map[string]any{
		"name":    "mixed",
		"count":   float64(10),
		"enabled": "true",
	}

	params, err := ParseParams[sampleParams](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
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

func TestParseParams_NumericStringIntoStringField(t *testing.T) {
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

	params, err := ParseParams[prefixParams](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.DidErrorPrefix != "44" {
		t.Errorf("DidErrorPrefix = %q, want %q", params.DidErrorPrefix, "44")
	}
	if params.Count != 10 {
		t.Errorf("Count = %d, want 10", params.Count)
	}
}

func TestParseParams_NumericValueIntoStringField(t *testing.T) {
	// When core sends a float64 for a string-typed field, it should be converted
	type mixedParams struct {
		Label string `json:"label"`
	}

	raw := map[string]any{
		"label": float64(42),
	}

	params, err := ParseParams[mixedParams](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Label != "42" {
		t.Errorf("Label = %q, want %q", params.Label, "42")
	}
}

func TestParseParams_NestedStructCoercion(t *testing.T) {
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

	params, err := ParseParams[outer](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Inner.Threshold != 100 {
		t.Errorf("Inner.Threshold = %d, want 100", params.Inner.Threshold)
	}
	if params.Inner.Tag != "55" {
		t.Errorf("Inner.Tag = %q, want %q", params.Inner.Tag, "55")
	}
}

func TestParseParams_PointerStructCoercion(t *testing.T) {
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

	params, err := ParseParams[outer](raw)
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Inner == nil {
		t.Fatal("Inner is nil")
	}
	if params.Inner.Value != "99" {
		t.Errorf("Inner.Value = %q, want %q", params.Inner.Value, "99")
	}
}
