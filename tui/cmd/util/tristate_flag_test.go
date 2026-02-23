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

// ABOUTME: Tests for the TristateFlag type (true/false/unset boolean).
// ABOUTME: Covers construction, state transitions, parsing, and flag interface.
package util

import (
	"testing"
)

func TestNewTriStateFlag(t *testing.T) {
	flag := NewTriStateFlag()

	if flag == nil {
		t.Fatal("NewTriStateFlag() returned nil")
	}
	if flag.Value != nil {
		t.Errorf("new TristateFlag should have nil Value, got %v", flag.Value)
	}
	if !flag.IsUnset() {
		t.Error("new TristateFlag should be unset")
	}
	if flag.IsSet() {
		t.Error("new TristateFlag should not be set")
	}
}

func TestNewTriStateFlagFromBool(t *testing.T) {
	tests := []struct {
		name     string
		input    bool
		wantTrue bool
	}{
		{"from true", true, true},
		{"from false", false, false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			flag := NewTriStateFlagFromBool(tt.input)

			if flag == nil {
				t.Fatal("NewTriStateFlagFromBool() returned nil")
			}
			if !flag.IsSet() {
				t.Error("flag should be set")
			}
			if flag.IsTrue() != tt.wantTrue {
				t.Errorf("IsTrue() = %v, want %v", flag.IsTrue(), tt.wantTrue)
			}
			if flag.IsFalse() == tt.wantTrue {
				t.Errorf("IsFalse() = %v, want %v", flag.IsFalse(), !tt.wantTrue)
			}
		})
	}
}

func TestNewTriStateFlagFromString(t *testing.T) {
	tests := []struct {
		name       string
		input      string
		wantSet    bool
		wantTrue   bool
		wantFalse  bool
		wantUnset  bool
		wantString string
	}{
		// True values
		{"true", "true", true, true, false, false, "true"},
		{"TRUE", "TRUE", true, true, false, false, "true"},
		{"True", "True", true, true, false, false, "true"},
		{"t", "t", true, true, false, false, "true"},
		{"T", "T", true, true, false, false, "true"},
		{"yes", "yes", true, true, false, false, "true"},
		{"YES", "YES", true, true, false, false, "true"},
		{"y", "y", true, true, false, false, "true"},
		{"Y", "Y", true, true, false, false, "true"},
		{"1", "1", true, true, false, false, "true"},

		// False values
		{"false", "false", true, false, true, false, "false"},
		{"FALSE", "FALSE", true, false, true, false, "false"},
		{"False", "False", true, false, true, false, "false"},
		{"f", "f", true, false, true, false, "false"},
		{"F", "F", true, false, true, false, "false"},
		{"no", "no", true, false, true, false, "false"},
		{"NO", "NO", true, false, true, false, "false"},
		{"n", "n", true, false, true, false, "false"},
		{"N", "N", true, false, true, false, "false"},
		{"0", "0", true, false, true, false, "false"},

		// Unset values
		{"empty", "", false, false, false, true, "unset"},
		{"unset", "unset", false, false, false, true, "unset"},
		{"UNSET", "UNSET", false, false, false, true, "unset"},
		{"all", "all", false, false, false, true, "unset"},
		{"ALL", "ALL", false, false, false, true, "unset"},
		{"any", "any", false, false, false, true, "unset"},
		{"ANY", "ANY", false, false, false, true, "unset"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			flag := NewTriStateFlagFromString(tt.input)

			if flag == nil {
				t.Fatal("NewTriStateFlagFromString() returned nil")
			}
			if flag.IsSet() != tt.wantSet {
				t.Errorf("IsSet() = %v, want %v", flag.IsSet(), tt.wantSet)
			}
			if flag.IsTrue() != tt.wantTrue {
				t.Errorf("IsTrue() = %v, want %v", flag.IsTrue(), tt.wantTrue)
			}
			if flag.IsFalse() != tt.wantFalse {
				t.Errorf("IsFalse() = %v, want %v", flag.IsFalse(), tt.wantFalse)
			}
			if flag.IsUnset() != tt.wantUnset {
				t.Errorf("IsUnset() = %v, want %v", flag.IsUnset(), tt.wantUnset)
			}
			if flag.String() != tt.wantString {
				t.Errorf("String() = %q, want %q", flag.String(), tt.wantString)
			}
		})
	}
}

func TestTristateFlag_Set(t *testing.T) {
	t.Run("valid values", func(t *testing.T) {
		validTrueValues := []string{"true", "t", "yes", "y", "1", "TRUE", "True", "YES", "Y"}
		validFalseValues := []string{"false", "f", "no", "n", "0", "FALSE", "False", "NO", "N"}
		validUnsetValues := []string{"", "unset", "all", "any", "UNSET", "ALL", "ANY"}

		for _, v := range validTrueValues {
			flag := NewTriStateFlag()
			err := flag.Set(v)
			if err != nil {
				t.Errorf("Set(%q) returned error: %v", v, err)
			}
			if !flag.IsTrue() {
				t.Errorf("Set(%q) should result in IsTrue()", v)
			}
		}

		for _, v := range validFalseValues {
			flag := NewTriStateFlag()
			err := flag.Set(v)
			if err != nil {
				t.Errorf("Set(%q) returned error: %v", v, err)
			}
			if !flag.IsFalse() {
				t.Errorf("Set(%q) should result in IsFalse()", v)
			}
		}

		for _, v := range validUnsetValues {
			flag := NewTriStateFlag()
			// First set to true
			flag.SetTrue()
			// Then set to unset value
			err := flag.Set(v)
			if err != nil {
				t.Errorf("Set(%q) returned error: %v", v, err)
			}
			if !flag.IsUnset() {
				t.Errorf("Set(%q) should result in IsUnset()", v)
			}
		}
	})

	t.Run("invalid values", func(t *testing.T) {
		invalidValues := []string{"invalid", "maybe", "perhaps", "2", "-1", "truee", "fals"}

		for _, v := range invalidValues {
			flag := NewTriStateFlag()
			err := flag.Set(v)
			if err == nil {
				t.Errorf("Set(%q) should return error", v)
			}
		}
	})

	t.Run("whitespace handling", func(t *testing.T) {
		flag := NewTriStateFlag()
		err := flag.Set("  true  ")
		if err != nil {
			t.Errorf("Set with whitespace returned error: %v", err)
		}
		if !flag.IsTrue() {
			t.Error("Set with whitespace should work")
		}
	})
}

func TestTristateFlag_SetTrue(t *testing.T) {
	flag := NewTriStateFlag()

	// Initially unset
	if !flag.IsUnset() {
		t.Error("new flag should be unset")
	}

	flag.SetTrue()

	if !flag.IsTrue() {
		t.Error("after SetTrue(), IsTrue() should be true")
	}
	if !flag.IsSet() {
		t.Error("after SetTrue(), IsSet() should be true")
	}
	if flag.IsFalse() {
		t.Error("after SetTrue(), IsFalse() should be false")
	}
	if flag.IsUnset() {
		t.Error("after SetTrue(), IsUnset() should be false")
	}
	if flag.String() != "true" {
		t.Errorf("after SetTrue(), String() should be 'true', got %q", flag.String())
	}
}

func TestTristateFlag_SetFalse(t *testing.T) {
	flag := NewTriStateFlag()

	// Initially unset
	if !flag.IsUnset() {
		t.Error("new flag should be unset")
	}

	flag.SetFalse()

	if flag.IsTrue() {
		t.Error("after SetFalse(), IsTrue() should be false")
	}
	if !flag.IsSet() {
		t.Error("after SetFalse(), IsSet() should be true")
	}
	if !flag.IsFalse() {
		t.Error("after SetFalse(), IsFalse() should be true")
	}
	if flag.IsUnset() {
		t.Error("after SetFalse(), IsUnset() should be false")
	}
	if flag.String() != "false" {
		t.Errorf("after SetFalse(), String() should be 'false', got %q", flag.String())
	}
}

func TestTristateFlag_Unset(t *testing.T) {
	flag := NewTriStateFlag()

	// Set to true first
	flag.SetTrue()
	if !flag.IsTrue() {
		t.Error("SetTrue() should work")
	}

	// Now unset
	flag.Unset()

	if flag.IsTrue() {
		t.Error("after Unset(), IsTrue() should be false")
	}
	if flag.IsSet() {
		t.Error("after Unset(), IsSet() should be false")
	}
	if flag.IsFalse() {
		t.Error("after Unset(), IsFalse() should be false")
	}
	if !flag.IsUnset() {
		t.Error("after Unset(), IsUnset() should be true")
	}
	if flag.String() != "unset" {
		t.Errorf("after Unset(), String() should be 'unset', got %q", flag.String())
	}
}

func TestTristateFlag_SetFromBool(t *testing.T) {
	tests := []struct {
		name      string
		input     bool
		wantTrue  bool
		wantFalse bool
	}{
		{"set from true", true, true, false},
		{"set from false", false, false, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			flag := NewTriStateFlag()
			flag.SetFromBool(tt.input)

			if !flag.IsSet() {
				t.Error("after SetFromBool(), IsSet() should be true")
			}
			if flag.IsTrue() != tt.wantTrue {
				t.Errorf("IsTrue() = %v, want %v", flag.IsTrue(), tt.wantTrue)
			}
			if flag.IsFalse() != tt.wantFalse {
				t.Errorf("IsFalse() = %v, want %v", flag.IsFalse(), tt.wantFalse)
			}
		})
	}
}

func TestTristateFlag_Type(t *testing.T) {
	flag := NewTriStateFlag()

	if flag.Type() != "bool" {
		t.Errorf("Type() = %q, want 'bool'", flag.Type())
	}
}

func TestTristateFlag_String(t *testing.T) {
	tests := []struct {
		name     string
		setup    func(*TristateFlag)
		expected string
	}{
		{
			name:     "unset",
			setup:    func(f *TristateFlag) {},
			expected: "unset",
		},
		{
			name: "true",
			setup: func(f *TristateFlag) {
				f.SetTrue()
			},
			expected: "true",
		},
		{
			name: "false",
			setup: func(f *TristateFlag) {
				f.SetFalse()
			},
			expected: "false",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			flag := NewTriStateFlag()
			tt.setup(flag)

			if flag.String() != tt.expected {
				t.Errorf("String() = %q, want %q", flag.String(), tt.expected)
			}
		})
	}
}

func TestTristateFlag_StateTransitions(t *testing.T) {
	flag := NewTriStateFlag()

	// unset -> true
	if !flag.IsUnset() {
		t.Error("initial state should be unset")
	}
	flag.SetTrue()
	if !flag.IsTrue() {
		t.Error("should be true after SetTrue()")
	}

	// true -> false
	flag.SetFalse()
	if !flag.IsFalse() {
		t.Error("should be false after SetFalse()")
	}

	// false -> unset
	flag.Unset()
	if !flag.IsUnset() {
		t.Error("should be unset after Unset()")
	}

	// unset -> false
	flag.SetFalse()
	if !flag.IsFalse() {
		t.Error("should be false after SetFalse() from unset")
	}

	// false -> true
	flag.SetTrue()
	if !flag.IsTrue() {
		t.Error("should be true after SetTrue() from false")
	}

	// true -> unset
	flag.Unset()
	if !flag.IsUnset() {
		t.Error("should be unset after Unset() from true")
	}
}

func TestTristateFlag_IsSetVsIsUnset(t *testing.T) {
	flag := NewTriStateFlag()

	// They should always be opposites
	if flag.IsSet() == flag.IsUnset() {
		t.Error("IsSet() and IsUnset() should never be equal")
	}

	flag.SetTrue()
	if flag.IsSet() == flag.IsUnset() {
		t.Error("IsSet() and IsUnset() should never be equal after SetTrue()")
	}

	flag.SetFalse()
	if flag.IsSet() == flag.IsUnset() {
		t.Error("IsSet() and IsUnset() should never be equal after SetFalse()")
	}

	flag.Unset()
	if flag.IsSet() == flag.IsUnset() {
		t.Error("IsSet() and IsUnset() should never be equal after Unset()")
	}
}

func BenchmarkTristateFlag_Set(b *testing.B) {
	flag := NewTriStateFlag()
	inputs := []string{"true", "false", "unset", "yes", "no", "1", "0"}

	for i := 0; i < b.N; i++ {
		flag.Set(inputs[i%len(inputs)])
	}
}

func BenchmarkTristateFlag_IsTrue(b *testing.B) {
	flag := NewTriStateFlagFromBool(true)

	for i := 0; i < b.N; i++ {
		_ = flag.IsTrue()
	}
}
