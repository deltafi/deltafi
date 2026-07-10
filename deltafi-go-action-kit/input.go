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
		fk := typeKind(ft)
		switch val := v.(type) {
		case string:
			out[k] = coerceStringForKind(val, fk)
		case float64:
			if fk == reflect.String {
				out[k] = strconv.FormatFloat(val, 'f', -1, 64)
			} else {
				out[k] = v
			}
		case []any:
			if fk == reflect.Slice {
				elemKind := typeKind(sliceElemType(ft))
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
			if fk == reflect.Struct || fk == reflect.Pointer {
				out[k] = coerceParams(val, derefType(ft))
			} else if fk == reflect.Map {
				out[k] = coerceMapForType(val, ft)
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
var jsonFieldMapCache sync.Map // reflect.Type -> map[string]reflect.Type

// jsonFieldMap returns a map from JSON field name to reflect.Type for a struct type.
// Results are cached by reflect.Type for the process lifetime.
func jsonFieldMap(t reflect.Type) map[string]reflect.Type {
	if t == nil || t.Kind() != reflect.Struct {
		return nil
	}
	if cached, ok := jsonFieldMapCache.Load(t); ok {
		return cached.(map[string]reflect.Type)
	}
	fields := reflect.VisibleFields(t)
	m := make(map[string]reflect.Type, len(fields))
	for _, f := range fields {
		if !f.IsExported() || f.Anonymous {
			continue
		}
		name := f.Tag.Get("json")
		if idx := strings.IndexByte(name, ','); idx >= 0 {
			name = name[:idx]
		}
		if name == "" || name == "-" {
			name = f.Name
		}
		m[name] = f.Type
	}
	// Use LoadOrStore so concurrent first-callers share the same map.
	actual, _ := jsonFieldMapCache.LoadOrStore(t, m)
	return actual.(map[string]reflect.Type)
}

func derefType(t reflect.Type) reflect.Type {
	for t != nil && t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	return t
}

func typeKind(t reflect.Type) reflect.Kind {
	t = derefType(t)
	if t == nil {
		return reflect.Invalid
	}
	return t.Kind()
}

func sliceElemType(t reflect.Type) reflect.Type {
	t = derefType(t)
	if t == nil || t.Kind() != reflect.Slice {
		return nil
	}
	return t.Elem()
}

func mapElemType(t reflect.Type) reflect.Type {
	t = derefType(t)
	if t == nil || t.Kind() != reflect.Map {
		return nil
	}
	return t.Elem()
}

func coerceMapForType(m map[string]any, mapType reflect.Type) map[string]any {
	elemType := mapElemType(mapType)
	elemKind := typeKind(elemType)
	out := make(map[string]any, len(m))
	for k, v := range m {
		switch val := v.(type) {
		case string:
			out[k] = coerceStringForKind(val, elemKind)
		case float64:
			if elemKind == reflect.String {
				out[k] = strconv.FormatFloat(val, 'f', -1, 64)
			} else {
				out[k] = v
			}
		case map[string]any:
			if elemKind == reflect.Struct || elemKind == reflect.Pointer {
				out[k] = coerceParams(val, derefType(elemType))
			} else if elemKind == reflect.Map {
				out[k] = coerceMapForType(val, elemType)
			} else {
				out[k] = coerceParams(val, nil)
			}
		default:
			out[k] = v
		}
	}
	return out
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
		return s
	}
	return s
}
