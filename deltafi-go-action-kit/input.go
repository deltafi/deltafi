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
// ABOUTME: Parameter parsing and type coercion for action parameters.
// ABOUTME: Handles conversion between JSON wire format and Go struct types.

package actionkit

import (
	"encoding/json"
	"reflect"
	"strconv"
	"strings"
	"sync"
)

// parseRawParams marshals the map to JSON then unmarshals into the destination struct.
// DeltaFi core may send all action params as strings after variable substitution,
// so values are coerced to match the destination struct's field types before unmarshaling.
func parseRawParams(raw map[string]any, dest any) error {
	if raw == nil {
		return nil
	}
	t := reflect.TypeOf(dest)
	for t.Kind() == reflect.Pointer {
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
		case []any:
			if ft == reflect.Slice {
				elemKind := sliceElemKind(structType, k)
				coerced := make([]any, len(val))
				for i, item := range val {
					switch elem := item.(type) {
					case string:
						coerced[i] = coerceStringForKind(elem, elemKind)
					case float64:
						if elemKind == reflect.String {
							coerced[i] = strconv.FormatFloat(elem, 'f', -1, 64)
						} else {
							coerced[i] = elem
						}
					default:
						coerced[i] = elem
					}
				}
				out[k] = coerced
			} else {
				out[k] = v
			}
		case map[string]any:
			if ft == reflect.Struct || ft == reflect.Pointer {
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

// jsonFieldMapCache memoizes jsonFieldMap results by struct type so the
// reflect walk happens once per type instead of once per parameter parse.
// Populated lazily on first lookup; entries live for the process lifetime.
var jsonFieldMapCache sync.Map // reflect.Type -> map[string]reflect.Kind

// jsonFieldMap returns a map from JSON field name to reflect.Kind for a struct type.
// Results are cached by reflect.Type for the process lifetime.
func jsonFieldMap(t reflect.Type) map[string]reflect.Kind {
	if t == nil || t.Kind() != reflect.Struct {
		return nil
	}
	if cached, ok := jsonFieldMapCache.Load(t); ok {
		return cached.(map[string]reflect.Kind)
	}
	m := make(map[string]reflect.Kind, t.NumField())
	for f := range t.Fields() {
		name := f.Tag.Get("json")
		if idx := strings.IndexByte(name, ','); idx >= 0 {
			name = name[:idx]
		}
		if name == "" || name == "-" {
			name = f.Name
		}
		kind := f.Type.Kind()
		if kind == reflect.Pointer {
			kind = f.Type.Elem().Kind()
		}
		m[name] = kind
	}
	// Use LoadOrStore so concurrent first-callers share the same map.
	actual, _ := jsonFieldMapCache.LoadOrStore(t, m)
	return actual.(map[string]reflect.Kind)
}

// structFieldType returns the struct type for a named JSON field, dereferencing pointers.
func structFieldType(parent reflect.Type, jsonName string) reflect.Type {
	if parent == nil || parent.Kind() != reflect.Struct {
		return nil
	}
	for f := range parent.Fields() {
		name := f.Tag.Get("json")
		if idx := strings.IndexByte(name, ','); idx >= 0 {
			name = name[:idx]
		}
		if name == "" || name == "-" {
			name = f.Name
		}
		if name == jsonName {
			t := f.Type
			for t.Kind() == reflect.Pointer {
				t = t.Elem()
			}
			return t
		}
	}
	return nil
}

// sliceElemKind returns the reflect.Kind of the element type for a slice field.
func sliceElemKind(parent reflect.Type, jsonName string) reflect.Kind {
	if parent == nil || parent.Kind() != reflect.Struct {
		return reflect.Invalid
	}
	for f := range parent.Fields() {
		name := f.Tag.Get("json")
		if idx := strings.IndexByte(name, ','); idx >= 0 {
			name = name[:idx]
		}
		if name == "" || name == "-" {
			name = f.Name
		}
		if name == jsonName && f.Type.Kind() == reflect.Slice {
			kind := f.Type.Elem().Kind()
			if kind == reflect.Pointer {
				kind = f.Type.Elem().Elem().Kind()
			}
			return kind
		}
	}
	return reflect.Invalid
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
