/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package java

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
	PluginName      string
	PackageName     string
	ClassName       string
	TestClassName   string // Test class name that doesn't start with "Test" to avoid JUnit collection issues
	ActionName      string
	ActionClassName string
	ActionFileName  string
	GroupID         string
	Description     string
	ActionType      string
	DeltaFiVersion  string // DeltaFi SDK version (from TUI version)
}

// ReadRawFile reads a file from the template directory without template processing
func ReadRawFile(filePath string) ([]byte, error) {
	fullPath := filepath.Join("template", filePath)
	return templateFiles.ReadFile(fullPath)
}

// RenderTemplate renders a template file with the given data
func RenderTemplate(templatePath string, data *TemplateData) ([]byte, error) {
	// templatePath is relative to the template/ directory
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
			// Remove "template/" prefix
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
