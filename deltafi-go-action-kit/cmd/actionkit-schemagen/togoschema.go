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
// ABOUTME: AST-based Go-struct-to-JSON-Schema generator.
// ABOUTME: Produces schemas equivalent to the kit's reflect-based GenerateSchema for the supported subset.

package main

import (
	"encoding/json"
	"fmt"
	"go/ast"
	"go/parser"
	"go/token"
	"maps"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"
)

// SchemaFromParamsFile parses a Go source file containing a *Params struct
// and returns the JSON Schema map for the primary *Params struct. Named types
// referenced by the params struct may live in sibling .go files in the same
// directory — the tool parses all non-test .go files in that directory and
// makes their struct declarations resolvable.
func SchemaFromParamsFile(srcPath string) (map[string]any, error) {
	fset := token.NewFileSet()
	file, err := parser.ParseFile(fset, srcPath, nil, parser.ParseComments)
	if err != nil {
		return nil, fmt.Errorf("parse: %w", err)
	}

	// Types declared in the primary file (used to choose the primary Params).
	fileStructs := collectStructTypes(file)
	primary := findPrimaryParamsStruct(fileStructs)
	if primary == "" {
		return nil, fmt.Errorf("no *Params struct found in %s", srcPath)
	}

	// Merge in struct types declared in sibling .go files so cross-file refs
	// (shared param-shape types like `ContentSelection`) resolve.
	siblings, err := collectPackageStructTypes(srcPath)
	if err != nil {
		return nil, fmt.Errorf("scan package: %w", err)
	}
	// Primary file wins on name conflict.
	maps.Copy(siblings, fileStructs)

	g := &schemaBuilder{structs: siblings}
	schema, err := g.rawStructSchema(primary, siblings[primary])
	if err != nil {
		return nil, err
	}
	if err := g.applyFieldTags(schema, siblings[primary]); err != nil {
		return nil, err
	}
	return schema, nil
}

// collectPackageStructTypes parses every non-test *.go file in the same
// directory as srcPath and returns a merged map of struct type declarations.
// Test files are excluded so package types defined for tests don't leak into
// generated schemas.
func collectPackageStructTypes(srcPath string) (map[string]*ast.StructType, error) {
	dir := filepath.Dir(srcPath)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	out := make(map[string]*ast.StructType)
	fset := token.NewFileSet()
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") {
			continue
		}
		f, err := parser.ParseFile(fset, filepath.Join(dir, name), nil, 0)
		if err != nil {
			return nil, fmt.Errorf("parse %s: %w", name, err)
		}
		maps.Copy(out, collectStructTypes(f))
	}
	return out, nil
}

// collectStructTypes returns every struct type declared at file scope.
func collectStructTypes(file *ast.File) map[string]*ast.StructType {
	out := make(map[string]*ast.StructType)
	for _, decl := range file.Decls {
		gen, ok := decl.(*ast.GenDecl)
		if !ok || gen.Tok != token.TYPE {
			continue
		}
		for _, spec := range gen.Specs {
			ts, ok := spec.(*ast.TypeSpec)
			if !ok {
				continue
			}
			st, ok := ts.Type.(*ast.StructType)
			if !ok {
				continue
			}
			out[ts.Name.Name] = st
		}
	}
	return out
}

// findPrimaryParamsStruct picks the struct type that represents the file's
// primary params. Convention: the only type name ending in "Params".
func findPrimaryParamsStruct(structs map[string]*ast.StructType) string {
	var hits []string
	for name := range structs {
		if strings.HasSuffix(name, "Params") {
			hits = append(hits, name)
		}
	}
	if len(hits) == 1 {
		return hits[0]
	}
	var best string
	for _, n := range hits {
		if len(n) > len(best) {
			best = n
		}
	}
	return best
}

type schemaBuilder struct {
	structs map[string]*ast.StructType
}

// rawStructSchema builds the basic schema for a struct: type, properties (with
// each property's type expression resolved), additionalProperties=false, and
// title. Tag-derived metadata (description, default, etc.) is NOT applied here
// — applyFieldTags handles that for direct/pointer-direct fields, mirroring
// the reflect-based path's behavior of not recursing into slice/map elements.
func (g *schemaBuilder) rawStructSchema(typeName string, st *ast.StructType) (map[string]any, error) {
	properties := make(map[string]any)
	for _, field := range st.Fields.List {
		if len(field.Names) == 0 {
			// Anonymous embedded fields aren't supported for params.
			continue
		}
		tag := parseStructTag(field.Tag)
		jsonName, _ := splitJSONTag(tag.Get("json"))
		if jsonName == "-" {
			continue
		}
		propSchema, err := g.typeSchema(field.Type)
		if err != nil {
			return nil, fmt.Errorf("%s.%s: %w", typeName, field.Names[0].Name, err)
		}
		for _, name := range field.Names {
			propName := jsonName
			if propName == "" {
				propName = lowerFirst(name.Name)
			}
			properties[propName] = cloneSchema(propSchema)
		}
	}
	return map[string]any{
		"type":                 "object",
		"properties":           properties,
		"additionalProperties": false,
		"title":                typeName,
	}, nil
}

// applyFieldTags reads dedicated struct tags (description, default, enum,
// required, minimum, maximum) and writes them onto the matching property
// schemas. Recurses into direct struct or pointer-to-struct fields to apply
// tags on nested types, matching the reflect-based path.
func (g *schemaBuilder) applyFieldTags(schema map[string]any, st *ast.StructType) error {
	properties, _ := schema["properties"].(map[string]any)
	if properties == nil {
		return nil
	}
	var required []string
	for _, field := range st.Fields.List {
		if len(field.Names) == 0 {
			continue
		}
		tag := parseStructTag(field.Tag)
		jsonName, _ := splitJSONTag(tag.Get("json"))
		if jsonName == "-" {
			continue
		}
		fieldType := field.Type
		if star, ok := fieldType.(*ast.StarExpr); ok {
			fieldType = star.X
		}
		ident, identOk := fieldType.(*ast.Ident)
		nestedSt, nestedOk := (*ast.StructType)(nil), false
		if identOk {
			nestedSt, nestedOk = g.structs[ident.Name]
		}
		for _, name := range field.Names {
			propName := jsonName
			if propName == "" {
				propName = lowerFirst(name.Name)
			}
			prop, _ := properties[propName].(map[string]any)
			if prop == nil {
				continue
			}
			applyDedicatedTags(prop, tag)
			if nestedOk {
				if err := g.applyFieldTags(prop, nestedSt); err != nil {
					return err
				}
			}
			if tag.Get("required") == "true" {
				required = append(required, propName)
			}
		}
	}
	if len(required) > 0 {
		req := make([]any, len(required))
		for i, r := range required {
			req[i] = r
		}
		schema["required"] = req
	}
	return nil
}

// typeSchema turns a Go AST type expression into its JSON Schema representation.
// Struct types are returned as their raw schema (no field-level tags applied);
// the caller adds tag metadata at the field level for direct/pointer-direct
// fields.
func (g *schemaBuilder) typeSchema(expr ast.Expr) (map[string]any, error) {
	switch t := expr.(type) {
	case *ast.Ident:
		return g.identSchema(t.Name)
	case *ast.StarExpr:
		return g.typeSchema(t.X)
	case *ast.ArrayType:
		items, err := g.typeSchema(t.Elt)
		if err != nil {
			return nil, err
		}
		return map[string]any{"type": "array", "items": items}, nil
	case *ast.MapType:
		if ident, ok := t.Key.(*ast.Ident); !ok || ident.Name != "string" {
			return nil, fmt.Errorf("map keys must be string, got %s", exprString(t.Key))
		}
		val, err := g.typeSchema(t.Value)
		if err != nil {
			return nil, err
		}
		return map[string]any{"type": "object", "additionalProperties": val}, nil
	case *ast.SelectorExpr:
		pkgIdent, ok := t.X.(*ast.Ident)
		if !ok {
			return nil, fmt.Errorf("unsupported qualified type expression")
		}
		return qualifiedTypeSchema(pkgIdent.Name, t.Sel.Name)
	case *ast.InterfaceType:
		if len(t.Methods.List) == 0 {
			return map[string]any{}, nil
		}
		return nil, fmt.Errorf("non-empty interface types are not supported")
	}
	return nil, fmt.Errorf("unsupported type expression: %s", exprString(expr))
}

// identSchema handles unqualified identifiers — Go primitives or local struct
// types defined in the same file.
func (g *schemaBuilder) identSchema(name string) (map[string]any, error) {
	switch name {
	case "bool":
		return map[string]any{"type": "boolean"}, nil
	case "string":
		return map[string]any{"type": "string"}, nil
	case "int", "int8", "int16", "int32", "int64",
		"uint", "uint8", "uint16", "uint32", "uint64",
		"byte", "rune":
		return map[string]any{"type": "integer"}, nil
	case "float32", "float64":
		return map[string]any{"type": "number"}, nil
	case "any":
		return map[string]any{}, nil
	}
	if st, ok := g.structs[name]; ok {
		return g.rawStructSchema(name, st)
	}
	return nil, fmt.Errorf("unknown type %q (must be a primitive, a struct defined in the same file, or a kit special type)", name)
}

// qualifiedTypeSchema handles the kit's special parameter types.
func qualifiedTypeSchema(pkg, name string) (map[string]any, error) {
	if pkg != "actionkit" {
		return nil, fmt.Errorf("unsupported qualified type %s.%s", pkg, name)
	}
	switch name {
	case "DataSize":
		return map[string]any{
			"type":           "string",
			"pattern":        "^[0-9]+(B|KB|MB|GB|TB)$",
			"x-deltafi-type": "DataSize",
		}, nil
	case "EnvVar":
		return map[string]any{
			"type":           "string",
			"pattern":        "^[A-Z][A-Z0-9_]*$",
			"x-deltafi-type": "EnvVar",
		}, nil
	}
	return nil, fmt.Errorf("unsupported actionkit type: %s", name)
}

// applyDedicatedTags reads description/default/enum/minimum/maximum from a
// struct tag and writes them into the property schema. Mirrors the reflect-path
// logic in the kit's schema.go.
func applyDedicatedTags(prop map[string]any, tag reflect.StructTag) {
	if desc := tag.Get("description"); desc != "" {
		prop["description"] = desc
	}
	if def := tag.Get("default"); def != "" {
		prop["default"] = coerceDefault(def, prop)
	}
	if enumStr := tag.Get("enum"); enumStr != "" {
		vals := strings.Split(enumStr, ",")
		enumAny := make([]any, len(vals))
		for i, v := range vals {
			enumAny[i] = strings.TrimSpace(v)
		}
		prop["enum"] = enumAny
	}
	if minStr := tag.Get("minimum"); minStr != "" {
		if n, err := strconv.ParseFloat(minStr, 64); err == nil {
			prop["minimum"] = n
		}
	}
	if maxStr := tag.Get("maximum"); maxStr != "" {
		if n, err := strconv.ParseFloat(maxStr, 64); err == nil {
			prop["maximum"] = n
		}
	}
}

// coerceDefault converts a string default value to the proper JSON type
// indicated by the property's type field. Matches schema.go's coerceDefault.
func coerceDefault(val string, schema map[string]any) any {
	switch schema["type"] {
	case "boolean":
		if val == "true" {
			return true
		}
		if val == "false" {
			return false
		}
	case "integer":
		if n, err := strconv.ParseInt(val, 10, 64); err == nil {
			return n
		}
	case "number":
		if f, err := strconv.ParseFloat(val, 64); err == nil {
			return f
		}
	case "array", "object":
		var parsed any
		if err := json.Unmarshal([]byte(val), &parsed); err == nil {
			return parsed
		}
	}
	return val
}

// --- helpers ---

func parseStructTag(lit *ast.BasicLit) reflect.StructTag {
	if lit == nil {
		return ""
	}
	s := lit.Value
	if len(s) >= 2 && (s[0] == '`' || s[0] == '"') {
		s = s[1 : len(s)-1]
	}
	return reflect.StructTag(s)
}

func splitJSONTag(s string) (name string, omitEmpty bool) {
	if s == "" {
		return "", false
	}
	parts := strings.Split(s, ",")
	for _, p := range parts[1:] {
		if p == "omitempty" {
			omitEmpty = true
		}
	}
	return parts[0], omitEmpty
}

func lowerFirst(s string) string {
	if s == "" {
		return s
	}
	return strings.ToLower(s[:1]) + s[1:]
}

func cloneSchema(s map[string]any) map[string]any {
	data, _ := json.Marshal(s)
	var out map[string]any
	_ = json.Unmarshal(data, &out)
	return out
}

func exprString(expr ast.Expr) string {
	switch t := expr.(type) {
	case *ast.Ident:
		return t.Name
	case *ast.SelectorExpr:
		return exprString(t.X) + "." + t.Sel.Name
	case *ast.StarExpr:
		return "*" + exprString(t.X)
	case *ast.ArrayType:
		return "[]" + exprString(t.Elt)
	case *ast.MapType:
		return "map[" + exprString(t.Key) + "]" + exprString(t.Value)
	}
	return fmt.Sprintf("%T", expr)
}
