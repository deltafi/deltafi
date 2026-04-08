// ABOUTME: Template rendering engine for Go plugin code generation.
// ABOUTME: Embeds template files and renders them with plugin-specific data.

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
package golang

import (
	"bytes"
	"embed"
	"fmt"
	"io/fs"
	"path/filepath"
	"strings"
	"text/template"
)

//go:embed all:template
var templateFiles embed.FS

// TemplateData holds the data for template rendering
type TemplateData struct {
	PluginName        string // kebab-case plugin name, e.g. "my-plugin"
	ModulePath        string // Go module path, e.g. "github.com/org/my-plugin"
	ClassName         string // PascalCase plugin name, e.g. "MyPlugin"
	ActionName        string // kebab-case action base name, e.g. "my-action"
	ActionClassName   string // PascalCase action name with Action suffix, e.g. "MyActionAction"
	ActionFileName    string // snake_case action file name, e.g. "my_action"
	SupplierClassName string // PascalCase supplier name, e.g. "MySupplier"
	SupplierFileName  string // snake_case supplier file name, e.g. "my_supplier"
	GroupID           string // DeltaFi group ID, e.g. "org.deltafi"
	Domain            string // DeltaFi domain, e.g. "org.deltafi.my-plugin"
	Description       string // Plugin description
	ActionType        string // Action type, e.g. "TransformAction"
	DeltaFiVersion    string // DeltaFi SDK version
}

// RenderTemplate renders a template file with the given data
func RenderTemplate(templatePath string, data *TemplateData) ([]byte, error) {
	fullPath := filepath.Join("template", templatePath)
	tmplContent, err := templateFiles.ReadFile(fullPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read template %s: %w", templatePath, err)
	}

	funcMap := template.FuncMap{
		"replace": strings.ReplaceAll,
	}
	tmpl, err := template.New(templatePath).Funcs(funcMap).Parse(string(tmplContent))
	if err != nil {
		return nil, fmt.Errorf("failed to parse template %s: %w", templatePath, err)
	}

	var buf bytes.Buffer
	if err := tmpl.Execute(&buf, data); err != nil {
		return nil, fmt.Errorf("failed to execute template %s: %w", templatePath, err)
	}

	return buf.Bytes(), nil
}

// ListTemplates lists all available template files
func ListTemplates() ([]string, error) {
	var templates []string
	err := fs.WalkDir(templateFiles, "template", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() {
			relPath, err := filepath.Rel("template", path)
			if err != nil {
				return err
			}
			templates = append(templates, relPath)
		}
		return nil
	})
	return templates, err
}
