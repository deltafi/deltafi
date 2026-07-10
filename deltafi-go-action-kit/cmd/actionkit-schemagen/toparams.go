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
// ABOUTME: AST-free JSON-Schema-to-Go-struct generator.
// ABOUTME: Reads *_action_params.json, emits the matching *_action_params.go source.

package main

import (
	"encoding/json"
	"fmt"
	"go/format"
	"os"
	"sort"
	"strconv"
	"strings"
)

// ParamsFileFromSchema reads a *_action_params.json file and returns the
// formatted Go source for the matching *_action_params.go file.
func ParamsFileFromSchema(jsonPath string) ([]byte, error) {
	data, err := os.ReadFile(jsonPath)
	if err != nil {
		return nil, fmt.Errorf("read: %w", err)
	}
	var schema map[string]any
	if err := json.Unmarshal(data, &schema); err != nil {
		return nil, fmt.Errorf("parse: %w", err)
	}

	title, _ := schema["title"].(string)
	if title == "" {
		return nil, fmt.Errorf("schema is missing the \"title\" field (needed to derive the Go struct name)")
	}

	e := &goEmitter{
		pkg:     "actions",
		emitted: map[string]bool{},
		pending: []pendingStruct{},
		pendIdx: map[string]bool{},
	}
	if err := e.queueStruct(title, schema); err != nil {
		return nil, err
	}
	for len(e.pending) > 0 {
		next := e.pending[0]
		e.pending = e.pending[1:]
		if err := e.writeStruct(next.name, next.schema); err != nil {
			return nil, err
		}
	}

	src := e.assemble()
	formatted, err := format.Source(src)
	if err != nil {
		// Return unformatted source for debuggability — the error message
		// points at the bad output.
		return src, fmt.Errorf("format generated source: %w", err)
	}
	return formatted, nil
}

type pendingStruct struct {
	name   string
	schema map[string]any
}

type goEmitter struct {
	pkg     string
	body    strings.Builder
	emitted map[string]bool
	pending []pendingStruct
	pendIdx map[string]bool
}

// queueStruct registers a struct to emit once. Already-queued/emitted names are
// no-ops. Order of emission is enqueue order.
func (e *goEmitter) queueStruct(name string, schema map[string]any) error {
	if e.emitted[name] || e.pendIdx[name] {
		return nil
	}
	e.pendIdx[name] = true
	e.pending = append(e.pending, pendingStruct{name: name, schema: schema})
	return nil
}

func (e *goEmitter) writeStruct(name string, schema map[string]any) error {
	if e.emitted[name] {
		return nil
	}
	e.emitted[name] = true

	if t, _ := schema["type"].(string); t != "object" {
		return fmt.Errorf("%s: expected object schema, got %q", name, t)
	}

	properties, _ := schema["properties"].(map[string]any)
	requiredSet := requiredFieldSet(schema["required"])

	// Sort properties for deterministic output.
	propNames := make([]string, 0, len(properties))
	for k := range properties {
		propNames = append(propNames, k)
	}
	sort.Strings(propNames)

	if e.body.Len() > 0 {
		e.body.WriteString("\n")
	}
	fmt.Fprintf(&e.body, "// %s defines the action's configurable parameters.\n", name)
	fmt.Fprintf(&e.body, "type %s struct {\n", name)
	if len(propNames) == 0 {
		fmt.Fprintf(&e.body, "}\n")
		return nil
	}

	for _, propName := range propNames {
		prop, _ := properties[propName].(map[string]any)
		if prop == nil {
			continue
		}
		fieldName := pascalCase(propName)
		fieldType, err := e.goTypeForProperty(prop)
		if err != nil {
			return fmt.Errorf("%s.%s: %w", name, propName, err)
		}
		tag := buildStructTag(propName, prop, requiredSet[propName])
		fmt.Fprintf(&e.body, "\t%s %s `%s`\n", fieldName, fieldType, tag)
	}
	fmt.Fprintf(&e.body, "}\n")
	return nil
}

// goTypeForProperty turns a JSON Schema property fragment into the matching
// Go type expression. Nested object properties with a title trigger a
// queued struct emission; properties without a title for a struct shape
// are emitted as anonymous nested structs.
func (e *goEmitter) goTypeForProperty(prop map[string]any) (string, error) {
	if xdt, ok := prop["x-deltafi-type"].(string); ok {
		switch xdt {
		case "DataSize":
			return "actionkit.DataSize", nil
		case "EnvVar":
			return "actionkit.EnvVar", nil
		}
	}
	switch prop["type"] {
	case "boolean":
		return "bool", nil
	case "string":
		return "string", nil
	case "integer":
		return "int", nil
	case "number":
		return "float64", nil
	case "array":
		items, _ := prop["items"].(map[string]any)
		if items == nil {
			return "[]any", nil
		}
		inner, err := e.goTypeForProperty(items)
		if err != nil {
			return "", err
		}
		return "[]" + inner, nil
	case "object":
		// Map shape: object with additionalProperties as a schema.
		if ap, ok := prop["additionalProperties"].(map[string]any); ok {
			inner, err := e.goTypeForProperty(ap)
			if err != nil {
				return "", err
			}
			return "map[string]" + inner, nil
		}
		// Named nested struct.
		if title, ok := prop["title"].(string); ok && title != "" {
			if err := e.queueStruct(title, prop); err != nil {
				return "", err
			}
			return "*" + title, nil
		}
		// Anonymous nested object: emit inline.
		return e.inlineObjectType(prop)
	case nil:
		// Untyped — treat as any.
		return "any", nil
	}
	return "", fmt.Errorf("unsupported schema type: %v", prop["type"])
}

func (e *goEmitter) inlineObjectType(prop map[string]any) (string, error) {
	properties, _ := prop["properties"].(map[string]any)
	if len(properties) == 0 {
		return "map[string]any", nil
	}
	requiredSet := requiredFieldSet(prop["required"])
	propNames := make([]string, 0, len(properties))
	for k := range properties {
		propNames = append(propNames, k)
	}
	sort.Strings(propNames)

	var b strings.Builder
	b.WriteString("struct {\n")
	for _, propName := range propNames {
		sub, _ := properties[propName].(map[string]any)
		if sub == nil {
			continue
		}
		fieldName := pascalCase(propName)
		fieldType, err := e.goTypeForProperty(sub)
		if err != nil {
			return "", err
		}
		tag := buildStructTag(propName, sub, requiredSet[propName])
		fmt.Fprintf(&b, "%s %s `%s`\n", fieldName, fieldType, tag)
	}
	b.WriteString("}")
	return b.String(), nil
}

// buildStructTag produces the kit-conventional struct tag for a property.
// Order: json, description, default, enum, required, minimum, maximum.
func buildStructTag(propName string, prop map[string]any, isRequired bool) string {
	var parts []string
	parts = append(parts, fmt.Sprintf(`json:%q`, propName))
	if s, ok := prop["description"].(string); ok && s != "" {
		parts = append(parts, fmt.Sprintf(`description:%q`, s))
	}
	if v, ok := prop["default"]; ok {
		parts = append(parts, fmt.Sprintf(`default:%q`, defaultToString(v)))
	}
	if v, ok := prop["enum"].([]any); ok && len(v) > 0 {
		strs := make([]string, len(v))
		for i, x := range v {
			strs[i] = fmt.Sprint(x)
		}
		parts = append(parts, fmt.Sprintf(`enum:%q`, strings.Join(strs, ",")))
	}
	if isRequired {
		parts = append(parts, `required:"true"`)
	}
	if v, ok := numericProp(prop, "minimum"); ok {
		parts = append(parts, fmt.Sprintf(`minimum:%q`, strconv.FormatFloat(v, 'f', -1, 64)))
	}
	if v, ok := numericProp(prop, "maximum"); ok {
		parts = append(parts, fmt.Sprintf(`maximum:%q`, strconv.FormatFloat(v, 'f', -1, 64)))
	}
	return strings.Join(parts, " ")
}

func defaultToString(v any) string {
	switch x := v.(type) {
	case string:
		return x
	case bool:
		return strconv.FormatBool(x)
	case float64:
		// JSON unmarshals all numbers as float64.
		if x == float64(int64(x)) {
			return strconv.FormatInt(int64(x), 10)
		}
		return strconv.FormatFloat(x, 'f', -1, 64)
	case nil:
		return ""
	}
	data, _ := json.Marshal(v)
	return string(data)
}

func numericProp(prop map[string]any, key string) (float64, bool) {
	switch v := prop[key].(type) {
	case float64:
		return v, true
	case int:
		return float64(v), true
	case int64:
		return float64(v), true
	}
	return 0, false
}

func requiredFieldSet(v any) map[string]bool {
	out := map[string]bool{}
	if arr, ok := v.([]any); ok {
		for _, s := range arr {
			if str, ok := s.(string); ok {
				out[str] = true
			}
		}
	}
	return out
}

func pascalCase(s string) string {
	if s == "" {
		return s
	}
	return strings.ToUpper(s[:1]) + s[1:]
}

// assemble builds the final Go file content from the queued pieces.
func (e *goEmitter) assemble() []byte {
	// Find the primary struct name (the first one emitted) for the ABOUTME hint.
	primary := ""
	for line := range strings.SplitSeq(e.body.String(), "\n") {
		if after, ok := strings.CutPrefix(line, "type "); ok {
			rest := after
			parts := strings.Fields(rest)
			if len(parts) > 0 {
				primary = parts[0]
				break
			}
		}
	}

	needsKitImport := strings.Contains(e.body.String(), "actionkit.")

	var src strings.Builder
	src.WriteString("// ABOUTME: Configurable parameters for ")
	if primary != "" {
		actionName := strings.TrimSuffix(primary, "Params")
		// Some params types use the bare action stem (e.g. HelloWorldGoTransformParams)
		// rather than the *Action-suffixed form; normalize so the comment always
		// references the registered action class name.
		if !strings.HasSuffix(actionName, "Action") {
			actionName += "Action"
		}
		src.WriteString(actionName)
		src.WriteString(".\n")
	} else {
		src.WriteString("the action.\n")
	}
	src.WriteString("// ABOUTME: Generated from the matching *_action_params.json by actionkit-schemagen.\n")
	src.WriteString("\n")
	fmt.Fprintf(&src, "package %s\n\n", e.pkg)
	if needsKitImport {
		src.WriteString("import actionkit \"gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2\"\n\n")
	}
	src.WriteString(e.body.String())
	return []byte(src.String())
}
