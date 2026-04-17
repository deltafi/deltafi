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
// ABOUTME: JSON Schema generation from Go struct types.
// ABOUTME: Uses reflection to produce schemas for action parameter discovery in the DeltaFi UI.

package actionkit

import (
	"encoding/json"
	"maps"
	"reflect"
	"slices"
	"strconv"
	"strings"

	"github.com/invopop/jsonschema"
)

// GenerateSchema produces a JSON Schema map from a Go struct type.
// Returns an empty map if paramType is nil.
//
// Struct fields use dedicated tags for schema metadata:
//
//	Name   string `json:"name" description:"The name to use" default:"unnamed"`
//	Count  int    `json:"count" description:"How many items" default:"10"`
//	Mode   string `json:"mode" description:"Processing mode" enum:"fast,slow,auto"`
//	Label  string `json:"label" description:"A required label" required:"true"`
//	Score  int    `json:"score" minimum:"0" maximum:"100"`
func GenerateSchema(paramType any) map[string]any {
	if paramType == nil {
		return map[string]any{}
	}

	reflector := &jsonschema.Reflector{
		RequiredFromJSONSchemaTags: true,
	}
	schema := reflector.Reflect(paramType)

	// Round-trip through JSON to get a plain map
	data, err := json.Marshal(schema)
	if err != nil {
		return map[string]any{}
	}

	var raw map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return map[string]any{}
	}

	// Extract definitions for resolving $ref pointers
	defs, _ := raw["$defs"].(map[string]any)

	// Resolve the top-level $ref (library produces {"$ref":"#/$defs/T","$defs":{...}})
	result := resolveTopLevelRef(raw, defs)

	// Recursively resolve nested $refs, add additionalProperties, coerce defaults
	postProcessSchema(result, defs)

	// Apply dedicated struct tags (description, default, enum, required, minimum, maximum)
	applyDedicatedTags(result, paramType)

	// Remove fields that DeltaFi core doesn't expect
	delete(result, "$schema")
	delete(result, "$id")
	delete(result, "$defs")

	return result
}

// resolveTopLevelRef inlines the $ref target from $defs into the top-level map.
func resolveTopLevelRef(schema map[string]any, defs map[string]any) map[string]any {
	ref, ok := schema["$ref"].(string)
	if !ok {
		return schema
	}
	if resolved := lookupDef(ref, defs); resolved != nil {
		return deepCopyMap(resolved)
	}
	return schema
}

// lookupDef resolves a JSON Schema $ref string against a $defs map.
func lookupDef(ref string, defs map[string]any) map[string]any {
	const prefix = "#/$defs/"
	if len(ref) <= len(prefix) || ref[:len(prefix)] != prefix {
		return nil
	}
	typeName := ref[len(prefix):]
	defMap, ok := defs[typeName].(map[string]any)
	if !ok {
		return nil
	}
	return defMap
}

// postProcessSchema recursively walks a schema map to:
//   - resolve $ref pointers by inlining definitions
//   - set additionalProperties: false on struct-typed objects
//   - coerce default values to their proper JSON types
func postProcessSchema(schema map[string]any, defs map[string]any) {
	// Resolve $ref by inlining the referenced definition
	if ref, ok := schema["$ref"].(string); ok {
		if resolved := lookupDef(ref, defs); resolved != nil {
			resolved = deepCopyMap(resolved)
			delete(schema, "$ref")
			maps.Copy(schema, resolved)
		}
	}

	// Set additionalProperties: false on object types (structs).
	// Map types already have additionalProperties set to their value schema.
	if schema["type"] == "object" {
		if _, exists := schema["additionalProperties"]; !exists {
			schema["additionalProperties"] = false
		}
	}

	// Coerce string default values to their proper JSON types
	if def, exists := schema["default"]; exists {
		schema["default"] = coerceDefault(def, schema)
	}

	// Recurse into properties
	if props, ok := schema["properties"].(map[string]any); ok {
		for _, v := range props {
			if propMap, ok := v.(map[string]any); ok {
				postProcessSchema(propMap, defs)
			}
		}
	}

	// Recurse into items (arrays)
	if items, ok := schema["items"].(map[string]any); ok {
		postProcessSchema(items, defs)
	}

	// Recurse into additionalProperties if it's a schema (maps)
	if ap, ok := schema["additionalProperties"].(map[string]any); ok {
		postProcessSchema(ap, defs)
	}
}

// coerceDefault converts a string default value to the proper JSON type
// based on the schema's type field. The jsonschema library always produces
// string defaults from struct tags; this restores the correct types.
func coerceDefault(val any, schema map[string]any) any {
	str, ok := val.(string)
	if !ok {
		return val
	}

	typ, _ := schema["type"].(string)
	switch typ {
	case "boolean":
		if str == "true" {
			return true
		}
		if str == "false" {
			return false
		}
	case "integer":
		if n, err := strconv.ParseInt(str, 10, 64); err == nil {
			return n
		}
	case "number":
		if f, err := strconv.ParseFloat(str, 64); err == nil {
			return f
		}
	case "array", "object":
		var parsed any
		if err := json.Unmarshal([]byte(str), &parsed); err == nil {
			return parsed
		}
	}
	return val
}

// deepCopyMap creates an independent copy of a map[string]any tree via JSON round-trip.
func deepCopyMap(m map[string]any) map[string]any {
	data, err := json.Marshal(m)
	if err != nil {
		return m
	}
	var result map[string]any
	if err := json.Unmarshal(data, &result); err != nil {
		return m
	}
	return result
}

// applyDedicatedTags reads description, default, enum, required, minimum, and
// maximum struct tags and applies them to the schema. These dedicated tags are
// simpler than the jsonschema tag and take precedence when both are present.
func applyDedicatedTags(schema map[string]any, paramType any) {
	t := reflect.TypeOf(paramType)
	for t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	if t.Kind() != reflect.Struct {
		return
	}

	props, ok := schema["properties"].(map[string]any)
	if !ok {
		return
	}

	// Collect existing required fields
	var requiredFields []string
	if existing, ok := schema["required"].([]any); ok {
		for _, r := range existing {
			if s, ok := r.(string); ok {
				requiredFields = append(requiredFields, s)
			}
		}
	}

	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		jsonName := field.Tag.Get("json")
		if idx := strings.IndexByte(jsonName, ','); idx >= 0 {
			jsonName = jsonName[:idx]
		}
		if jsonName == "" || jsonName == "-" {
			continue
		}

		propMap, ok := props[jsonName].(map[string]any)
		if !ok {
			continue
		}

		if desc := field.Tag.Get("description"); desc != "" {
			propMap["description"] = desc
		}
		if def := field.Tag.Get("default"); def != "" {
			propMap["default"] = coerceDefault(def, propMap)
		}
		if enumStr := field.Tag.Get("enum"); enumStr != "" {
			vals := strings.Split(enumStr, ",")
			enumAny := make([]any, len(vals))
			for j, v := range vals {
				enumAny[j] = strings.TrimSpace(v)
			}
			propMap["enum"] = enumAny
		}
		if field.Tag.Get("required") == "true" {
			if !slices.Contains(requiredFields, jsonName) {
				requiredFields = append(requiredFields, jsonName)
			}
		}
		if minStr := field.Tag.Get("minimum"); minStr != "" {
			if n, err := strconv.ParseFloat(minStr, 64); err == nil {
				propMap["minimum"] = n
			}
		}
		if maxStr := field.Tag.Get("maximum"); maxStr != "" {
			if n, err := strconv.ParseFloat(maxStr, 64); err == nil {
				propMap["maximum"] = n
			}
		}

		// Recurse for nested structs
		ft := field.Type
		if ft.Kind() == reflect.Pointer {
			ft = ft.Elem()
		}
		if ft.Kind() == reflect.Struct {
			applyDedicatedTags(propMap, reflect.New(ft).Interface())
		}
	}

	if len(requiredFields) > 0 {
		reqAny := make([]any, len(requiredFields))
		for i, r := range requiredFields {
			reqAny[i] = r
		}
		schema["required"] = reqAny
	}
}
