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
// ABOUTME: Tests for plugin generation MCP helpers (path resolution and language detection)
// ABOUTME: Avoids invoking the real generators so tests stay hermetic

package tools

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestDetectPluginLanguageMCP_Java(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "build.gradle"), []byte("// java"), 0o644); err != nil {
		t.Fatal(err)
	}
	got, err := detectPluginLanguageMCP(dir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "java" {
		t.Errorf("expected java, got %q", got)
	}
}

func TestDetectPluginLanguageMCP_Python(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "pyproject.toml"), []byte("[tool.poetry]"), 0o644); err != nil {
		t.Fatal(err)
	}
	got, err := detectPluginLanguageMCP(dir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "python" {
		t.Errorf("expected python, got %q", got)
	}
}

func TestDetectPluginLanguageMCP_Go(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), []byte("module example.com/p"), 0o644); err != nil {
		t.Fatal(err)
	}
	got, err := detectPluginLanguageMCP(dir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != "go" {
		t.Errorf("expected go, got %q", got)
	}
}

func TestDetectPluginLanguageMCP_MultipleAmbiguous(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "build.gradle"), []byte{}, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), []byte{}, 0o644); err != nil {
		t.Fatal(err)
	}
	_, err := detectPluginLanguageMCP(dir)
	if err == nil {
		t.Fatal("expected error for multiple build files")
	}
	if !strings.Contains(err.Error(), "multiple") {
		t.Errorf("expected error to mention 'multiple', got %v", err)
	}
}

func TestDetectPluginLanguageMCP_None(t *testing.T) {
	dir := t.TempDir()
	_, err := detectPluginLanguageMCP(dir)
	if err == nil {
		t.Fatal("expected error when no build file is present")
	}
}

func TestResolvePluginRef_AbsolutePath(t *testing.T) {
	dir := t.TempDir()
	pluginDir := filepath.Join(dir, "my-plugin")
	if err := os.MkdirAll(pluginDir, 0o755); err != nil {
		t.Fatal(err)
	}

	resolved, name, err := resolvePluginRef(pluginDir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if resolved != pluginDir {
		t.Errorf("dir: expected %q, got %q", pluginDir, resolved)
	}
	if name != "my-plugin" {
		t.Errorf("name: got %q", name)
	}
}

func TestResolvePluginRef_Empty(t *testing.T) {
	_, _, err := resolvePluginRef("")
	if err == nil {
		t.Fatal("expected error for empty reference")
	}
}

func TestResolvePluginRef_NotADirectory(t *testing.T) {
	dir := t.TempDir()
	file := filepath.Join(dir, "not-a-dir")
	if err := os.WriteFile(file, []byte{}, 0o644); err != nil {
		t.Fatal(err)
	}
	_, _, err := resolvePluginRef(file)
	if err == nil {
		t.Fatal("expected error for non-directory reference")
	}
	if !strings.Contains(err.Error(), "not a directory") {
		t.Errorf("expected 'not a directory' in error, got %v", err)
	}
}

func TestResolvePluginRef_MissingDirectory(t *testing.T) {
	missing := filepath.Join(t.TempDir(), "does-not-exist")
	_, _, err := resolvePluginRef(missing)
	if err == nil {
		t.Fatal("expected error for missing directory")
	}
}

func TestCapitalize(t *testing.T) {
	tests := map[string]string{
		"":       "",
		"java":   "Java",
		"python": "Python",
		"go":     "Go",
		"X":      "X",
	}
	for in, want := range tests {
		if got := capitalize(in); got != want {
			t.Errorf("capitalize(%q) = %q, want %q", in, got, want)
		}
	}
}
