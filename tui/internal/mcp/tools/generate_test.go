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
// ABOUTME: Tests for generate MCP tool language dispatch and plugin language detection
// ABOUTME: Covers Java/Python/Go generator selection, namespace defaulting, and marker-file detection

package tools

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestGeneratorForLanguage(t *testing.T) {
	tests := []struct {
		language string
		wantErr  bool
	}{
		{"java", false},
		{"python", false},
		{"go", false},
		{"ruby", true},
		{"", true},
	}

	for _, tt := range tests {
		t.Run(tt.language, func(t *testing.T) {
			gen, err := generatorForLanguage(tt.language)
			if tt.wantErr {
				if err == nil {
					t.Fatalf("expected error for language %q, got nil", tt.language)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error for language %q: %v", tt.language, err)
			}
			if gen == nil {
				t.Fatalf("expected non-nil generator for language %q", tt.language)
			}
		})
	}
}

func TestResolvePluginGenerator_JavaDefaultNamespace(t *testing.T) {
	_, ns, err := resolvePluginGenerator("java", "my-plugin", "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.HasPrefix(ns, "org.deltafi.") {
		t.Errorf("expected namespace prefixed with org.deltafi., got %q", ns)
	}
}

func TestResolvePluginGenerator_PythonDefaultNamespace(t *testing.T) {
	_, ns, err := resolvePluginGenerator("python", "my-plugin", "", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.HasPrefix(ns, "org.deltafi.") {
		t.Errorf("expected namespace prefixed with org.deltafi., got %q", ns)
	}
}

func TestResolvePluginGenerator_ExplicitNamespacePreserved(t *testing.T) {
	_, ns, err := resolvePluginGenerator("java", "my-plugin", "com.example.custom", "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if ns != "com.example.custom" {
		t.Errorf("expected namespace com.example.custom, got %q", ns)
	}
}

func TestResolvePluginGenerator_GoRequiresModule(t *testing.T) {
	_, _, err := resolvePluginGenerator("go", "my-plugin", "", "")
	if err == nil {
		t.Fatal("expected error when go plugin is missing module path")
	}
	if !strings.Contains(err.Error(), "module parameter is required") {
		t.Errorf("expected error to mention module requirement, got: %v", err)
	}
	if !strings.Contains(err.Error(), "my-plugin") {
		t.Errorf("expected error to include plugin name in example, got: %v", err)
	}
}

func TestResolvePluginGenerator_GoWithModuleSucceeds(t *testing.T) {
	gen, ns, err := resolvePluginGenerator("go", "my-plugin", "", "github.com/org/my-plugin")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if gen == nil {
		t.Fatal("expected non-nil generator")
	}
	if ns != "org.deltafi" {
		t.Errorf("expected default go namespace org.deltafi, got %q", ns)
	}
}

func TestResolvePluginGenerator_GoExplicitNamespace(t *testing.T) {
	_, ns, err := resolvePluginGenerator("go", "my-plugin", "com.example", "github.com/org/my-plugin")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if ns != "com.example" {
		t.Errorf("expected explicit namespace com.example, got %q", ns)
	}
}

func TestResolvePluginGenerator_UnsupportedLanguage(t *testing.T) {
	_, _, err := resolvePluginGenerator("ruby", "my-plugin", "", "")
	if err == nil {
		t.Fatal("expected error for unsupported language")
	}
	if !strings.Contains(err.Error(), "unsupported language") {
		t.Errorf("expected error to mention unsupported language, got: %v", err)
	}
}

func TestDetectPluginLanguage(t *testing.T) {
	tests := []struct {
		name     string
		markers  []string
		want     string
		wantErr  bool
		errMatch string
	}{
		{name: "java", markers: []string{"build.gradle"}, want: "java"},
		{name: "python", markers: []string{"pyproject.toml"}, want: "python"},
		{name: "go", markers: []string{"go.mod"}, want: "go"},
		{name: "no markers", markers: nil, wantErr: true, errMatch: "does not contain"},
		{name: "java + go", markers: []string{"build.gradle", "go.mod"}, wantErr: true, errMatch: "multiple languages"},
		{name: "python + go", markers: []string{"pyproject.toml", "go.mod"}, wantErr: true, errMatch: "multiple languages"},
		{name: "java + python", markers: []string{"build.gradle", "pyproject.toml"}, wantErr: true, errMatch: "multiple languages"},
		{name: "all three", markers: []string{"build.gradle", "pyproject.toml", "go.mod"}, wantErr: true, errMatch: "multiple languages"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir := t.TempDir()
			for _, m := range tt.markers {
				path := filepath.Join(dir, m)
				if err := os.WriteFile(path, []byte(""), 0644); err != nil {
					t.Fatalf("failed to write marker %s: %v", m, err)
				}
			}

			got, err := detectPluginLanguage(dir)
			if tt.wantErr {
				if err == nil {
					t.Fatalf("expected error, got nil (result=%q)", got)
				}
				if tt.errMatch != "" && !strings.Contains(err.Error(), tt.errMatch) {
					t.Errorf("expected error to contain %q, got: %v", tt.errMatch, err)
				}
				return
			}
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if got != tt.want {
				t.Errorf("expected %q, got %q", tt.want, got)
			}
		})
	}
}

func TestDetectPluginLanguage_MixedMarkersListed(t *testing.T) {
	dir := t.TempDir()
	for _, m := range []string{"build.gradle", "go.mod"} {
		if err := os.WriteFile(filepath.Join(dir, m), []byte(""), 0644); err != nil {
			t.Fatalf("failed to write marker %s: %v", m, err)
		}
	}

	_, err := detectPluginLanguage(dir)
	if err == nil {
		t.Fatal("expected error when multiple markers present")
	}

	for _, lang := range []string{"java", "go"} {
		if !strings.Contains(err.Error(), lang) {
			t.Errorf("expected error to list %q, got: %v", lang, err)
		}
	}
}
