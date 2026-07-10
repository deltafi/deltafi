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
// ABOUTME: Tests for JSON Schema generation from Go struct types.
// ABOUTME: Verifies schema output for various param structs including nested types and edge cases.

package actionkit

import (
	"bytes"
	"io"
	"log"
	"strings"
	"testing"
)

func TestGenerateSchema_Nil(t *testing.T) {
	schema := GenerateSchema(nil)
	if len(schema) != 0 {
		t.Errorf("expected empty map for nil, got %v", schema)
	}
}

type simpleSchemaParams struct {
	Name    string `json:"name"`
	Count   int    `json:"count"`
	Enabled bool   `json:"enabled"`
}

func TestGenerateSchema_SimpleStruct(t *testing.T) {
	schema := GenerateSchema(simpleSchemaParams{})

	if schema["type"] != "object" {
		t.Errorf("type = %v, want object", schema["type"])
	}

	props, ok := schema["properties"].(map[string]any)
	if !ok {
		t.Fatalf("properties is not a map: %T", schema["properties"])
	}

	// Should have all three fields
	for _, field := range []string{"name", "count", "enabled"} {
		if _, ok := props[field]; !ok {
			t.Errorf("missing property %q", field)
		}
	}

	// Verify types
	nameProps := props["name"].(map[string]any)
	if nameProps["type"] != "string" {
		t.Errorf("name type = %v, want string", nameProps["type"])
	}

	countProps := props["count"].(map[string]any)
	if countProps["type"] != "integer" {
		t.Errorf("count type = %v, want integer", countProps["type"])
	}

	enabledProps := props["enabled"].(map[string]any)
	if enabledProps["type"] != "boolean" {
		t.Errorf("enabled type = %v, want boolean", enabledProps["type"])
	}
}

func TestGenerateSchema_RemovesSchemaAndID(t *testing.T) {
	schema := GenerateSchema(simpleSchemaParams{})

	if _, ok := schema["$schema"]; ok {
		t.Error("$schema should be removed")
	}
	if _, ok := schema["$id"]; ok {
		t.Error("$id should be removed")
	}
}

type nestedSchemaParams struct {
	Config struct {
		Host string `json:"host"`
		Port int    `json:"port"`
	} `json:"config"`
}

func TestGenerateSchema_NestedStruct(t *testing.T) {
	schema := GenerateSchema(nestedSchemaParams{})

	props, ok := schema["properties"].(map[string]any)
	if !ok {
		t.Fatalf("properties is not a map: %T", schema["properties"])
	}

	configProp, ok := props["config"]
	if !ok {
		t.Fatal("missing config property")
	}

	configMap := configProp.(map[string]any)
	if configMap["type"] != "object" {
		t.Errorf("config type = %v, want object", configMap["type"])
	}

	innerProps, ok := configMap["properties"].(map[string]any)
	if !ok {
		t.Fatalf("config properties is not a map: %T", configMap["properties"])
	}
	if _, ok := innerProps["host"]; !ok {
		t.Error("missing config.host property")
	}
	if _, ok := innerProps["port"]; !ok {
		t.Error("missing config.port property")
	}

	// Nested object should have additionalProperties: false
	if configMap["additionalProperties"] != false {
		t.Errorf("config additionalProperties = %v, want false", configMap["additionalProperties"])
	}
}

type sliceSchemaParams struct {
	Tags   []string `json:"tags"`
	Values []int    `json:"values"`
}

func TestGenerateSchema_SliceFields(t *testing.T) {
	schema := GenerateSchema(sliceSchemaParams{})

	props, ok := schema["properties"].(map[string]any)
	if !ok {
		t.Fatalf("properties is not a map: %T", schema["properties"])
	}

	tagsProp := props["tags"].(map[string]any)
	if tagsProp["type"] != "array" {
		t.Errorf("tags type = %v, want array", tagsProp["type"])
	}
}

type emptySchemaParams struct{}

func TestGenerateSchema_EmptyStruct(t *testing.T) {
	schema := GenerateSchema(emptySchemaParams{})

	if schema["type"] != "object" {
		t.Errorf("type = %v, want object", schema["type"])
	}
}

func TestGenerateSchema_PointerToStruct(t *testing.T) {
	schema := GenerateSchema(&simpleSchemaParams{})

	if schema["type"] != "object" {
		t.Errorf("type = %v, want object", schema["type"])
	}

	props, ok := schema["properties"].(map[string]any)
	if !ok {
		t.Fatalf("properties is not a map: %T", schema["properties"])
	}
	if _, ok := props["name"]; !ok {
		t.Error("missing name property")
	}
}

// --- Phase 6: descriptions, defaults, enums, required, nested named types ---

type richSchemaParams struct {
	Name   string `json:"name" description:"The name of the thing" default:"unnamed"`
	Count  int    `json:"count" description:"How many items" default:"10"`
	Active bool   `json:"active" description:"Whether this is active" default:"true"`
	Mode   string `json:"mode" description:"Processing mode" enum:"fast,slow,auto"`
	Label  string `json:"label" description:"A required label" required:"true"`
}

func TestGenerateSchema_Descriptions(t *testing.T) {
	schema := GenerateSchema(richSchemaParams{})
	props := schema["properties"].(map[string]any)

	tests := map[string]string{
		"name":   "The name of the thing",
		"count":  "How many items",
		"active": "Whether this is active",
		"mode":   "Processing mode",
		"label":  "A required label",
	}

	for field, wantDesc := range tests {
		propMap := props[field].(map[string]any)
		got := propMap["description"]
		if got != wantDesc {
			t.Errorf("%s description = %v, want %q", field, got, wantDesc)
		}
	}
}

func TestGenerateSchema_DefaultValues(t *testing.T) {
	schema := GenerateSchema(richSchemaParams{})
	props := schema["properties"].(map[string]any)

	// String default stays as string
	nameProps := props["name"].(map[string]any)
	if nameProps["default"] != "unnamed" {
		t.Errorf("name default = %v (%T), want \"unnamed\"", nameProps["default"], nameProps["default"])
	}

	// Integer default coerced from string
	countProps := props["count"].(map[string]any)
	switch v := countProps["default"].(type) {
	case int64:
		if v != 10 {
			t.Errorf("count default = %v, want 10", v)
		}
	case float64:
		if v != 10 {
			t.Errorf("count default = %v, want 10", v)
		}
	default:
		t.Errorf("count default = %v (%T), want 10", countProps["default"], countProps["default"])
	}

	// Boolean default coerced from string to bool
	activeProps := props["active"].(map[string]any)
	if activeProps["default"] != true {
		t.Errorf("active default = %v (%T), want true", activeProps["default"], activeProps["default"])
	}
}

func TestGenerateSchema_DefaultFalse(t *testing.T) {
	type params struct {
		Disabled bool `json:"disabled" default:"false"`
	}
	schema := GenerateSchema(params{})
	props := schema["properties"].(map[string]any)
	disabledProps := props["disabled"].(map[string]any)
	if disabledProps["default"] != false {
		t.Errorf("disabled default = %v (%T), want false", disabledProps["default"], disabledProps["default"])
	}
}

func TestGenerateSchema_EnumValues(t *testing.T) {
	schema := GenerateSchema(richSchemaParams{})
	props := schema["properties"].(map[string]any)

	modeProps := props["mode"].(map[string]any)
	enumVals, ok := modeProps["enum"].([]any)
	if !ok {
		t.Fatalf("mode enum is not a slice: %T", modeProps["enum"])
	}

	want := []string{"fast", "slow", "auto"}
	if len(enumVals) != len(want) {
		t.Fatalf("mode enum length = %d, want %d", len(enumVals), len(want))
	}
	for i, w := range want {
		if enumVals[i] != w {
			t.Errorf("mode enum[%d] = %v, want %q", i, enumVals[i], w)
		}
	}
}

func TestGenerateSchema_RequiredFields(t *testing.T) {
	schema := GenerateSchema(richSchemaParams{})

	required, ok := schema["required"].([]any)
	if !ok {
		t.Fatalf("required is not a slice: %T", schema["required"])
	}

	found := false
	for _, r := range required {
		if r == "label" {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("label not found in required: %v", required)
	}
}

func TestGenerateSchema_AdditionalPropertiesFalse(t *testing.T) {
	schema := GenerateSchema(simpleSchemaParams{})
	if schema["additionalProperties"] != false {
		t.Errorf("top-level additionalProperties = %v, want false", schema["additionalProperties"])
	}
}

// Named nested type to trigger $ref resolution
type schemaServerConfig struct {
	Host string `json:"host"`
	Port int    `json:"port"`
}

type namedNestedSchemaParams struct {
	Server schemaServerConfig `json:"server"`
	Name   string             `json:"name"`
}

func TestGenerateSchema_NamedNestedStruct(t *testing.T) {
	schema := GenerateSchema(namedNestedSchemaParams{})
	props := schema["properties"].(map[string]any)

	serverProp := props["server"].(map[string]any)

	// $ref should be resolved (inlined)
	if _, hasRef := serverProp["$ref"]; hasRef {
		t.Error("server should not have $ref after resolution")
	}
	if serverProp["type"] != "object" {
		t.Errorf("server type = %v, want object", serverProp["type"])
	}

	innerProps := serverProp["properties"].(map[string]any)
	if _, ok := innerProps["host"]; !ok {
		t.Error("missing server.host property")
	}
	if _, ok := innerProps["port"]; !ok {
		t.Error("missing server.port property")
	}

	// Nested object should have additionalProperties: false
	if serverProp["additionalProperties"] != false {
		t.Errorf("server additionalProperties = %v, want false", serverProp["additionalProperties"])
	}

	// No $defs should remain
	if _, ok := schema["$defs"]; ok {
		t.Error("$defs should be removed from top-level schema")
	}
}

// Same named type used in multiple fields to test $ref resolution doesn't share state
type schemaSharedType struct {
	Value string `json:"value"`
}

type multiRefSchemaParams struct {
	First  schemaSharedType `json:"first"`
	Second schemaSharedType `json:"second"`
}

func TestGenerateSchema_MultipleRefsToSameType(t *testing.T) {
	schema := GenerateSchema(multiRefSchemaParams{})
	props := schema["properties"].(map[string]any)

	for _, field := range []string{"first", "second"} {
		fieldProp := props[field].(map[string]any)
		if _, hasRef := fieldProp["$ref"]; hasRef {
			t.Errorf("%s should not have $ref", field)
		}
		if fieldProp["type"] != "object" {
			t.Errorf("%s type = %v, want object", field, fieldProp["type"])
		}
		innerProps := fieldProp["properties"].(map[string]any)
		if _, ok := innerProps["value"]; !ok {
			t.Errorf("missing %s.value property", field)
		}
	}
}

type mapSchemaParams struct {
	Labels map[string]string `json:"labels"`
	Counts map[string]int    `json:"counts"`
}

func TestGenerateSchema_MapTypes(t *testing.T) {
	schema := GenerateSchema(mapSchemaParams{})
	props := schema["properties"].(map[string]any)

	// map[string]string
	labelsProp := props["labels"].(map[string]any)
	if labelsProp["type"] != "object" {
		t.Errorf("labels type = %v, want object", labelsProp["type"])
	}
	labelsAP := labelsProp["additionalProperties"].(map[string]any)
	if labelsAP["type"] != "string" {
		t.Errorf("labels additionalProperties type = %v, want string", labelsAP["type"])
	}

	// map[string]int
	countsProp := props["counts"].(map[string]any)
	if countsProp["type"] != "object" {
		t.Errorf("counts type = %v, want object", countsProp["type"])
	}
	countsAP := countsProp["additionalProperties"].(map[string]any)
	if countsAP["type"] != "integer" {
		t.Errorf("counts additionalProperties type = %v, want integer", countsAP["type"])
	}
}

// --- Dedicated struct tags ---

type dedicatedTagParams struct {
	Name    string `json:"name" description:"The name to use" default:"world"`
	Count   int    `json:"count" description:"Number of items" default:"5" minimum:"0" maximum:"100"`
	Mode    string `json:"mode" description:"Processing mode" enum:"fast,slow,auto"`
	Label   string `json:"label" description:"A required label" required:"true"`
	Verbose bool   `json:"verbose" description:"Enable verbose output" default:"false"`
}

func TestGenerateSchema_DedicatedTags_Description(t *testing.T) {
	schema := GenerateSchema(dedicatedTagParams{})
	props := schema["properties"].(map[string]any)

	tests := map[string]string{
		"name":    "The name to use",
		"count":   "Number of items",
		"mode":    "Processing mode",
		"label":   "A required label",
		"verbose": "Enable verbose output",
	}

	for field, wantDesc := range tests {
		propMap := props[field].(map[string]any)
		if propMap["description"] != wantDesc {
			t.Errorf("%s description = %v, want %q", field, propMap["description"], wantDesc)
		}
	}
}

func TestGenerateSchema_DedicatedTags_Defaults(t *testing.T) {
	schema := GenerateSchema(dedicatedTagParams{})
	props := schema["properties"].(map[string]any)

	nameProps := props["name"].(map[string]any)
	if nameProps["default"] != "world" {
		t.Errorf("name default = %v, want \"world\"", nameProps["default"])
	}

	countProps := props["count"].(map[string]any)
	// coerceDefault for integer returns int64; JSON round-trip would produce float64.
	// Both are valid — check the numeric value.
	switch v := countProps["default"].(type) {
	case int64:
		if v != 5 {
			t.Errorf("count default = %v, want 5", v)
		}
	case float64:
		if v != 5 {
			t.Errorf("count default = %v, want 5", v)
		}
	default:
		t.Errorf("count default = %v (%T), want 5", countProps["default"], countProps["default"])
	}

	verboseProps := props["verbose"].(map[string]any)
	if verboseProps["default"] != false {
		t.Errorf("verbose default = %v, want false", verboseProps["default"])
	}
}

func TestGenerateSchema_DedicatedTags_Enum(t *testing.T) {
	schema := GenerateSchema(dedicatedTagParams{})
	props := schema["properties"].(map[string]any)

	modeProps := props["mode"].(map[string]any)
	enumVals := modeProps["enum"].([]any)
	want := []string{"fast", "slow", "auto"}
	if len(enumVals) != len(want) {
		t.Fatalf("enum length = %d, want %d", len(enumVals), len(want))
	}
	for i, w := range want {
		if enumVals[i] != w {
			t.Errorf("enum[%d] = %v, want %q", i, enumVals[i], w)
		}
	}
}

func TestGenerateSchema_DedicatedTags_Required(t *testing.T) {
	schema := GenerateSchema(dedicatedTagParams{})
	required := schema["required"].([]any)
	found := false
	for _, r := range required {
		if r == "label" {
			found = true
		}
	}
	if !found {
		t.Errorf("label not in required: %v", required)
	}
}

func TestGenerateSchema_DedicatedTags_MinMax(t *testing.T) {
	schema := GenerateSchema(dedicatedTagParams{})
	props := schema["properties"].(map[string]any)

	countProps := props["count"].(map[string]any)
	if countProps["minimum"] != float64(0) {
		t.Errorf("count minimum = %v, want 0", countProps["minimum"])
	}
	if countProps["maximum"] != float64(100) {
		t.Errorf("count maximum = %v, want 100", countProps["maximum"])
	}
}

func TestCoerceDefault_NonString(t *testing.T) {
	// Non-string defaults should pass through unchanged
	result := coerceDefault(42, map[string]any{"type": "integer"})
	if result != 42 {
		t.Errorf("coerceDefault(42) = %v, want 42", result)
	}
}

func TestCoerceDefault_UnknownType(t *testing.T) {
	// Unknown type should return the string unchanged
	result := coerceDefault("hello", map[string]any{"type": "custom"})
	if result != "hello" {
		t.Errorf("coerceDefault(\"hello\", custom) = %v, want \"hello\"", result)
	}
}

func TestCoerceDefault_NumberType(t *testing.T) {
	result := coerceDefault("3.14", map[string]any{"type": "number"})
	if result != float64(3.14) {
		t.Errorf("coerceDefault(\"3.14\", number) = %v (%T), want 3.14", result, result)
	}
}

func TestCoerceDefault_ArrayType(t *testing.T) {
	result := coerceDefault(`["a","b"]`, map[string]any{"type": "array"})
	arr, ok := result.([]any)
	if !ok {
		t.Fatalf("coerceDefault array result is %T, want []any", result)
	}
	if len(arr) != 2 || arr[0] != "a" || arr[1] != "b" {
		t.Errorf("coerceDefault array = %v, want [a b]", arr)
	}
}

func TestCoerceDefault_InvalidParse(t *testing.T) {
	// Invalid bool string should return as-is
	result := coerceDefault("yes", map[string]any{"type": "boolean"})
	if result != "yes" {
		t.Errorf("coerceDefault(\"yes\", boolean) = %v, want \"yes\"", result)
	}

	// Invalid int string should return as-is
	result = coerceDefault("abc", map[string]any{"type": "integer"})
	if result != "abc" {
		t.Errorf("coerceDefault(\"abc\", integer) = %v, want \"abc\"", result)
	}
}

func TestJSONToMap_LogsAndFailsOnUnmarshalableValue(t *testing.T) {
	var buf bytes.Buffer
	old := log.Writer()
	log.SetOutput(&buf)
	t.Cleanup(func() { log.SetOutput(old) })

	// A channel cannot be JSON-marshaled, forcing the error path.
	m, ok := jsonToMap(map[string]any{"ch": make(chan int)})
	if ok {
		t.Fatal("jsonToMap should report failure on an unmarshalable value")
	}
	if m != nil {
		t.Errorf("expected nil map on failure, got %v", m)
	}
	if !strings.Contains(buf.String(), "schema generation") {
		t.Fatalf("expected a warning to be logged, got %q", buf.String())
	}
}

func TestDeepCopyMap_ReturnsOriginalOnFailure(t *testing.T) {
	old := log.Writer()
	log.SetOutput(io.Discard)
	t.Cleanup(func() { log.SetOutput(old) })

	bad := map[string]any{"ch": make(chan int)}
	got := deepCopyMap(bad)
	if got == nil {
		t.Fatal("deepCopyMap should return the original map when copying fails")
	}
}
