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

// ABOUTME: Tests for DeploymentMode enum type and its parsing/serialization.
// ABOUTME: Covers string conversion, display names, parsing, and marshaling.
package types

import (
	"testing"
)

func TestDeploymentMode_String(t *testing.T) {
	tests := []struct {
		mode     DeploymentMode
		expected string
	}{
		{Deployment, "Deployment"},
		{PluginDevelopment, "PluginDevelopment"},
		{CoreDevelopment, "CoreDevelopment"},
	}

	for _, tt := range tests {
		t.Run(tt.expected, func(t *testing.T) {
			result := tt.mode.String()
			if result != tt.expected {
				t.Errorf("String() = %q, want %q", result, tt.expected)
			}
		})
	}
}

func TestDeploymentMode_String_Invalid(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for invalid deployment mode, got none")
		}
	}()

	invalid := DeploymentMode(-1)
	_ = invalid.String()
}

func TestDeploymentMode_DisplayName(t *testing.T) {
	tests := []struct {
		mode     DeploymentMode
		expected string
	}{
		{Deployment, "Deployment"},
		{PluginDevelopment, "Plugin Development"},
		{CoreDevelopment, "Core Development"},
	}

	for _, tt := range tests {
		t.Run(tt.expected, func(t *testing.T) {
			result := tt.mode.DisplayName()
			if result != tt.expected {
				t.Errorf("DisplayName() = %q, want %q", result, tt.expected)
			}
		})
	}
}

func TestDeploymentMode_DisplayName_Invalid(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for invalid deployment mode, got none")
		}
	}()

	invalid := DeploymentMode(999)
	_ = invalid.DisplayName()
}

func TestDeploymentMode_Description(t *testing.T) {
	tests := []struct {
		mode DeploymentMode
		name string
	}{
		{Deployment, "Deployment"},
		{PluginDevelopment, "PluginDevelopment"},
		{CoreDevelopment, "CoreDevelopment"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := tt.mode.Description()
			if result == "" {
				t.Error("Description() returned empty string")
			}
		})
	}
}

func TestDeploymentMode_Description_Invalid(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("expected panic for invalid deployment mode, got none")
		}
	}()

	invalid := DeploymentMode(999)
	_ = invalid.Description()
}

func TestParseDeploymentMode(t *testing.T) {
	tests := []struct {
		input    string
		expected DeploymentMode
		wantErr  bool
	}{
		// Valid cases - exact match
		{"Deployment", Deployment, false},
		{"PluginDevelopment", PluginDevelopment, false},
		{"CoreDevelopment", CoreDevelopment, false},

		// Valid cases - case insensitive
		{"deployment", Deployment, false},
		{"DEPLOYMENT", Deployment, false},
		{"plugindevelopment", PluginDevelopment, false},
		{"PLUGINDEVELOPMENT", PluginDevelopment, false},
		{"coredevelopment", CoreDevelopment, false},
		{"COREDEVELOPMENT", CoreDevelopment, false},

		// Invalid cases
		{"invalid", unknownDeploymentMode, true},
		{"", unknownDeploymentMode, true},
		{"production", unknownDeploymentMode, true},
		{"development", unknownDeploymentMode, true},
		{"plugin", unknownDeploymentMode, true},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			result, err := ParseDeploymentMode(tt.input)

			if tt.wantErr {
				if err == nil {
					t.Errorf("ParseDeploymentMode(%q) expected error, got nil", tt.input)
				}
			} else {
				if err != nil {
					t.Errorf("ParseDeploymentMode(%q) unexpected error: %v", tt.input, err)
				}
				if result != tt.expected {
					t.Errorf("ParseDeploymentMode(%q) = %v, want %v", tt.input, result, tt.expected)
				}
			}
		})
	}
}

func TestDeploymentMode_MarshalText(t *testing.T) {
	tests := []struct {
		mode     DeploymentMode
		expected string
	}{
		{Deployment, "Deployment"},
		{PluginDevelopment, "PluginDevelopment"},
		{CoreDevelopment, "CoreDevelopment"},
	}

	for _, tt := range tests {
		t.Run(tt.expected, func(t *testing.T) {
			result, err := tt.mode.MarshalText()
			if err != nil {
				t.Errorf("MarshalText() unexpected error: %v", err)
			}
			if string(result) != tt.expected {
				t.Errorf("MarshalText() = %q, want %q", string(result), tt.expected)
			}
		})
	}
}

func TestDeploymentMode_UnmarshalText(t *testing.T) {
	tests := []struct {
		input    string
		expected DeploymentMode
		wantErr  bool
	}{
		{"Deployment", Deployment, false},
		{"PluginDevelopment", PluginDevelopment, false},
		{"CoreDevelopment", CoreDevelopment, false},
		{"deployment", Deployment, false},
		{"invalid", unknownDeploymentMode, true},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			var mode DeploymentMode
			err := mode.UnmarshalText([]byte(tt.input))

			if tt.wantErr {
				if err == nil {
					t.Errorf("UnmarshalText(%q) expected error, got nil", tt.input)
				}
			} else {
				if err != nil {
					t.Errorf("UnmarshalText(%q) unexpected error: %v", tt.input, err)
				}
				if mode != tt.expected {
					t.Errorf("UnmarshalText(%q) = %v, want %v", tt.input, mode, tt.expected)
				}
			}
		})
	}
}

func TestDeploymentMode_RoundTrip(t *testing.T) {
	modes := []DeploymentMode{Deployment, PluginDevelopment, CoreDevelopment}

	for _, original := range modes {
		t.Run(original.String(), func(t *testing.T) {
			// Marshal
			text, err := original.MarshalText()
			if err != nil {
				t.Fatalf("MarshalText() error: %v", err)
			}

			// Unmarshal
			var restored DeploymentMode
			err = restored.UnmarshalText(text)
			if err != nil {
				t.Fatalf("UnmarshalText() error: %v", err)
			}

			if restored != original {
				t.Errorf("round-trip failed: original=%v, restored=%v", original, restored)
			}
		})
	}
}

func TestDeploymentMode_Values(t *testing.T) {
	// Verify the enum values are what we expect
	if Deployment != 0 {
		t.Errorf("Deployment should be 0, got %d", Deployment)
	}
	if PluginDevelopment != 1 {
		t.Errorf("PluginDevelopment should be 1, got %d", PluginDevelopment)
	}
	if CoreDevelopment != 2 {
		t.Errorf("CoreDevelopment should be 2, got %d", CoreDevelopment)
	}
	if unknownDeploymentMode != 3 {
		t.Errorf("unknownDeploymentMode should be 3, got %d", unknownDeploymentMode)
	}
}

func BenchmarkParseDeploymentMode(b *testing.B) {
	inputs := []string{"Deployment", "PluginDevelopment", "CoreDevelopment", "deployment"}

	for i := 0; i < b.N; i++ {
		ParseDeploymentMode(inputs[i%len(inputs)])
	}
}

func BenchmarkDeploymentMode_String(b *testing.B) {
	modes := []DeploymentMode{Deployment, PluginDevelopment, CoreDevelopment}

	for i := 0; i < b.N; i++ {
		_ = modes[i%len(modes)].String()
	}
}
