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

// ABOUTME: Tests for shell detection and shell type classification.
// ABOUTME: Covers IsKnownShell and ensureConfigLine functionality.
package app

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestShellType_Constants(t *testing.T) {
	// Verify shell type string values
	tests := []struct {
		shell    ShellType
		expected string
	}{
		{Bash, "bash"},
		{Fish, "fish"},
		{Zsh, "zsh"},
		{Powershell, "powershell"},
		{UnknownShell, "unknown"},
	}

	for _, tt := range tests {
		t.Run(string(tt.shell), func(t *testing.T) {
			if string(tt.shell) != tt.expected {
				t.Errorf("ShellType = %q, want %q", string(tt.shell), tt.expected)
			}
		})
	}
}

func TestIsKnownShell(t *testing.T) {
	tests := []struct {
		shell    ShellType
		expected bool
	}{
		{Bash, true},
		{Fish, true},
		{Zsh, true},
		{Powershell, true},
		{UnknownShell, false},
		{ShellType("custom"), false},
		{ShellType(""), false},
	}

	for _, tt := range tests {
		t.Run(string(tt.shell), func(t *testing.T) {
			result := IsKnownShell(tt.shell)
			if result != tt.expected {
				t.Errorf("IsKnownShell(%q) = %v, want %v", tt.shell, result, tt.expected)
			}
		})
	}
}

func TestDetectShell_FromEnvironment(t *testing.T) {
	// Save original SHELL value
	originalShell := os.Getenv("SHELL")
	defer os.Setenv("SHELL", originalShell)

	tests := []struct {
		name     string
		shellEnv string
		expected ShellType
	}{
		{"bash path", "/bin/bash", Bash},
		{"bash usr path", "/usr/bin/bash", Bash},
		{"zsh path", "/bin/zsh", Zsh},
		{"zsh usr path", "/usr/bin/zsh", Zsh},
		{"fish path", "/usr/bin/fish", Fish},
		{"fish local path", "/usr/local/bin/fish", Fish},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			os.Setenv("SHELL", tt.shellEnv)
			result := DetectShell()
			if result != tt.expected {
				t.Errorf("DetectShell() with SHELL=%q = %q, want %q", tt.shellEnv, result, tt.expected)
			}
		})
	}
}

func TestDetectShell_Powershell(t *testing.T) {
	// Save original environment
	originalShell := os.Getenv("SHELL")
	originalPSModulePath := os.Getenv("PSModulePath")
	defer func() {
		os.Setenv("SHELL", originalShell)
		if originalPSModulePath == "" {
			os.Unsetenv("PSModulePath")
		} else {
			os.Setenv("PSModulePath", originalPSModulePath)
		}
	}()

	// Clear SHELL and set PSModulePath
	os.Unsetenv("SHELL")
	os.Setenv("PSModulePath", "/some/path")

	result := DetectShell()
	if result != Powershell {
		t.Errorf("DetectShell() with PSModulePath set = %q, want %q", result, Powershell)
	}
}

func TestEnsureConfigLine(t *testing.T) {
	t.Run("creates new file", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "testrc")
		configLine := "source /path/to/config"

		err := ensureConfigLine(testFile, configLine)
		if err != nil {
			t.Fatalf("ensureConfigLine() error: %v", err)
		}

		content, err := os.ReadFile(testFile)
		if err != nil {
			t.Fatalf("failed to read file: %v", err)
		}

		if !strings.Contains(string(content), configLine) {
			t.Errorf("file should contain config line, got: %q", string(content))
		}
	})

	t.Run("appends to existing file", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "testrc")
		existingContent := "# existing content\n"
		configLine := "source /path/to/config"

		// Create file with existing content
		err := os.WriteFile(testFile, []byte(existingContent), 0644)
		if err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}

		err = ensureConfigLine(testFile, configLine)
		if err != nil {
			t.Fatalf("ensureConfigLine() error: %v", err)
		}

		content, err := os.ReadFile(testFile)
		if err != nil {
			t.Fatalf("failed to read file: %v", err)
		}

		if !strings.Contains(string(content), existingContent) {
			t.Error("file should still contain existing content")
		}
		if !strings.Contains(string(content), configLine) {
			t.Error("file should contain new config line")
		}
	})

	t.Run("does not duplicate existing line", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "testrc")
		configLine := "source /path/to/config"

		// Create file with the config line already present
		err := os.WriteFile(testFile, []byte(configLine+"\n"), 0644)
		if err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}

		err = ensureConfigLine(testFile, configLine)
		if err != nil {
			t.Fatalf("ensureConfigLine() error: %v", err)
		}

		content, err := os.ReadFile(testFile)
		if err != nil {
			t.Fatalf("failed to read file: %v", err)
		}

		// Count occurrences
		count := strings.Count(string(content), configLine)
		if count != 1 {
			t.Errorf("config line should appear exactly once, found %d times", count)
		}
	})

	t.Run("strips stale DeltaFi marker line", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "testrc")
		staleLine := "[[ -f /old/path/rc.bash ]] && source /old/path/rc.bash " + deltafiShellMarker
		newLine := "[[ -f /new/path/rc.bash ]] && source /new/path/rc.bash " + deltafiShellMarker
		existing := "# user content\n" + staleLine + "\n# more user content\n"

		if err := os.WriteFile(testFile, []byte(existing), 0644); err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}

		if err := ensureConfigLine(testFile, newLine); err != nil {
			t.Fatalf("ensureConfigLine() error: %v", err)
		}

		content, err := os.ReadFile(testFile)
		if err != nil {
			t.Fatalf("failed to read file: %v", err)
		}
		got := string(content)

		if strings.Contains(got, staleLine) {
			t.Errorf("stale DeltaFi line should have been removed, got: %q", got)
		}
		if !strings.Contains(got, newLine) {
			t.Errorf("new DeltaFi line should be present, got: %q", got)
		}
		if !strings.Contains(got, "# user content") || !strings.Contains(got, "# more user content") {
			t.Errorf("unrelated user content should be preserved, got: %q", got)
		}
		if c := strings.Count(got, deltafiShellMarker); c != 1 {
			t.Errorf("expected exactly one DeltaFi marker line, found %d", c)
		}
	})

	t.Run("adds newline if file doesn't end with one", func(t *testing.T) {
		tmpDir := t.TempDir()
		testFile := filepath.Join(tmpDir, "testrc")
		existingContent := "# no trailing newline"
		configLine := "source /path/to/config"

		// Create file without trailing newline
		err := os.WriteFile(testFile, []byte(existingContent), 0644)
		if err != nil {
			t.Fatalf("failed to create test file: %v", err)
		}

		err = ensureConfigLine(testFile, configLine)
		if err != nil {
			t.Fatalf("ensureConfigLine() error: %v", err)
		}

		content, err := os.ReadFile(testFile)
		if err != nil {
			t.Fatalf("failed to read file: %v", err)
		}

		// The existing content and new line should be on separate lines
		lines := strings.Split(string(content), "\n")
		if len(lines) < 2 {
			t.Error("expected at least 2 lines")
		}
	})
}

func TestConfigureShell_UnsupportedShell(t *testing.T) {
	err := ConfigureShell(UnknownShell, "/path/to/tui")
	if err == nil {
		t.Error("expected error for unknown shell, got nil")
	}
	if !strings.Contains(err.Error(), "not supported") {
		t.Errorf("error should mention 'not supported', got: %v", err)
	}
}

func TestConfigureShell_CustomShell(t *testing.T) {
	err := ConfigureShell(ShellType("custom"), "/path/to/tui")
	if err == nil {
		t.Error("expected error for custom shell, got nil")
	}
}

func BenchmarkIsKnownShell(b *testing.B) {
	shells := []ShellType{Bash, Fish, Zsh, Powershell, UnknownShell}

	for i := 0; i < b.N; i++ {
		IsKnownShell(shells[i%len(shells)])
	}
}

func BenchmarkDetectShell(b *testing.B) {
	for i := 0; i < b.N; i++ {
		DetectShell()
	}
}
