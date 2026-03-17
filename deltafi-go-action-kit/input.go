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
// ABOUTME: Input types for action execution.
// ABOUTME: Defines TransformInput, EgressInput, IngressInput, and parameter parsing helpers.

package actionkit

import (
	"encoding/json"
	"reflect"
	"strconv"
	"strings"
)

// TransformInput holds the input data for a transform action.
type TransformInput struct {
	ActionContext *ActionContext
	Content       []ActionContent
	Metadata      map[string]string
	Params        map[string]any
}

// HasContent returns true if the input has at least one content item.
func (i TransformInput) HasContent() bool {
	return len(i.Content) > 0
}

// FirstContent returns a pointer to the first content item, or nil if empty.
func (i TransformInput) FirstContent() *ActionContent {
	if len(i.Content) == 0 {
		return nil
	}
	return &i.Content[0]
}

// ContentAt returns a pointer to the content item at the given index.
// Returns an ExpectedContentError if the index is out of range.
func (i TransformInput) ContentAt(index int) (*ActionContent, error) {
	if index < 0 || index >= len(i.Content) {
		return nil, &ExpectedContentError{Index: index, Count: len(i.Content)}
	}
	return &i.Content[index], nil
}

// ContentNamed returns a pointer to the first content item with the given name.
// Returns nil if no content with that name exists.
func (i TransformInput) ContentNamed(name string) *ActionContent {
	for idx := range i.Content {
		if i.Content[idx].Name == name {
			return &i.Content[idx]
		}
	}
	return nil
}

// GetMetadata returns the metadata value for the given key.
// Returns a MissingMetadataError if the key is not present.
func (i TransformInput) GetMetadata(key string) (string, error) {
	val, ok := i.Metadata[key]
	if !ok {
		return "", &MissingMetadataError{Key: key}
	}
	return val, nil
}

// GetMetadataOrDefault returns the metadata value for the given key,
// or the default value if the key is not present.
func (i TransformInput) GetMetadataOrDefault(key, defaultValue string) string {
	if val, ok := i.Metadata[key]; ok {
		return val
	}
	return defaultValue
}

// ParseParameters unmarshals the raw params into the given struct.
func (i TransformInput) ParseParameters(dest any) error {
	return parseRawParams(i.Params, dest)
}

// EgressInput holds the input data for an egress action.
type EgressInput struct {
	ActionContext *ActionContext
	Content       []ActionContent
	Metadata      map[string]string
	Params        map[string]any
}

// HasContent returns true if the input has at least one content item.
func (i EgressInput) HasContent() bool {
	return len(i.Content) > 0
}

// FirstContent returns a pointer to the first content item, or nil if empty.
func (i EgressInput) FirstContent() *ActionContent {
	if len(i.Content) == 0 {
		return nil
	}
	return &i.Content[0]
}

// ContentAt returns a pointer to the content item at the given index.
// Returns an ExpectedContentError if the index is out of range.
func (i EgressInput) ContentAt(index int) (*ActionContent, error) {
	if index < 0 || index >= len(i.Content) {
		return nil, &ExpectedContentError{Index: index, Count: len(i.Content)}
	}
	return &i.Content[index], nil
}

// ContentNamed returns a pointer to the first content item with the given name.
// Returns nil if no content with that name exists.
func (i EgressInput) ContentNamed(name string) *ActionContent {
	for idx := range i.Content {
		if i.Content[idx].Name == name {
			return &i.Content[idx]
		}
	}
	return nil
}

// GetMetadata returns the metadata value for the given key.
// Returns a MissingMetadataError if the key is not present.
func (i EgressInput) GetMetadata(key string) (string, error) {
	val, ok := i.Metadata[key]
	if !ok {
		return "", &MissingMetadataError{Key: key}
	}
	return val, nil
}

// GetMetadataOrDefault returns the metadata value for the given key,
// or the default value if the key is not present.
func (i EgressInput) GetMetadataOrDefault(key, defaultValue string) string {
	if val, ok := i.Metadata[key]; ok {
		return val
	}
	return defaultValue
}

// ParseParameters unmarshals the raw params into the given struct.
func (i EgressInput) ParseParameters(dest any) error {
	return parseRawParams(i.Params, dest)
}

// IngressInput holds the input data for a timed ingress action.
type IngressInput struct {
	ActionContext *ActionContext
	Params        map[string]any
}

// ParseParameters unmarshals the raw params into the given struct.
func (i IngressInput) ParseParameters(dest any) error {
	return parseRawParams(i.Params, dest)
}

// ParseParams unmarshals raw params into a typed struct using generics.
func ParseParams[T any](raw map[string]any) (T, error) {
	var result T
	if err := parseRawParams(raw, &result); err != nil {
		return result, err
	}
	return result, nil
}

// parseRawParams marshals the map to JSON then unmarshals into the destination struct.
// DeltaFi core may send all action params as strings after variable substitution,
// so values are coerced to match the destination struct's field types before unmarshaling.
func parseRawParams(raw map[string]any, dest any) error {
	if raw == nil {
		return nil
	}
	t := reflect.TypeOf(dest)
	for t.Kind() == reflect.Ptr {
		t = t.Elem()
	}
	data, err := json.Marshal(coerceParams(raw, t))
	if err != nil {
		return err
	}
	return json.Unmarshal(data, dest)
}

// coerceParams converts map values to match the target struct's field types.
// String values are coerced to numbers or booleans when the target field requires it.
// Numeric values are converted to strings when the target field is a string.
func coerceParams(m map[string]any, structType reflect.Type) map[string]any {
	fields := jsonFieldMap(structType)
	out := make(map[string]any, len(m))
	for k, v := range m {
		ft := fields[k]
		switch val := v.(type) {
		case string:
			out[k] = coerceStringForKind(val, ft)
		case float64:
			if ft == reflect.String {
				out[k] = strconv.FormatFloat(val, 'f', -1, 64)
			} else {
				out[k] = v
			}
		case map[string]any:
			if ft == reflect.Struct || ft == reflect.Ptr {
				nested := structFieldType(structType, k)
				out[k] = coerceParams(val, nested)
			} else {
				out[k] = coerceParams(val, nil)
			}
		default:
			out[k] = v
		}
	}
	return out
}

// jsonFieldMap returns a map from JSON field name to reflect.Kind for a struct type.
func jsonFieldMap(t reflect.Type) map[string]reflect.Kind {
	if t == nil || t.Kind() != reflect.Struct {
		return nil
	}
	m := make(map[string]reflect.Kind, t.NumField())
	for i := 0; i < t.NumField(); i++ {
		f := t.Field(i)
		name := f.Tag.Get("json")
		if idx := strings.IndexByte(name, ','); idx >= 0 {
			name = name[:idx]
		}
		if name == "" || name == "-" {
			name = f.Name
		}
		kind := f.Type.Kind()
		if kind == reflect.Ptr {
			kind = f.Type.Elem().Kind()
		}
		m[name] = kind
	}
	return m
}

// structFieldType returns the struct type for a named JSON field, dereferencing pointers.
func structFieldType(parent reflect.Type, jsonName string) reflect.Type {
	if parent == nil || parent.Kind() != reflect.Struct {
		return nil
	}
	for i := 0; i < parent.NumField(); i++ {
		f := parent.Field(i)
		name := f.Tag.Get("json")
		if idx := strings.IndexByte(name, ','); idx >= 0 {
			name = name[:idx]
		}
		if name == "" || name == "-" {
			name = f.Name
		}
		if name == jsonName {
			t := f.Type
			for t.Kind() == reflect.Ptr {
				t = t.Elem()
			}
			return t
		}
	}
	return nil
}

// coerceStringForKind converts a string value to the appropriate Go type
// based on the target field's kind.
func coerceStringForKind(s string, targetKind reflect.Kind) any {
	switch targetKind {
	case reflect.Bool:
		if s == "true" {
			return true
		}
		if s == "false" {
			return false
		}
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		if i, err := strconv.ParseInt(s, 10, 64); err == nil {
			return i
		}
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		if u, err := strconv.ParseUint(s, 10, 64); err == nil {
			return u
		}
	case reflect.Float32, reflect.Float64:
		if f, err := strconv.ParseFloat(s, 64); err == nil {
			return f
		}
	case reflect.String:
		return s
	case reflect.Invalid:
		// Target type unknown — apply best-effort coercion
		return coerceStringBestEffort(s)
	}
	return s
}

// coerceStringBestEffort converts a string value when the target type is unknown.
func coerceStringBestEffort(s string) any {
	if s == "true" {
		return true
	}
	if s == "false" {
		return false
	}
	if i, err := strconv.ParseInt(s, 10, 64); err == nil {
		return i
	}
	if f, err := strconv.ParseFloat(s, 64); err == nil {
		return f
	}
	return s
}
