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
package styles

import (
	"strings"
	"testing"
)

func TestColorizeJSONPreservesEscapes(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected string
	}{
		{
			name:     "escaped quotes in string",
			input:    `{"message": "Hello \"world\"!"}`,
			expected: `\"world\"`,
		},
		{
			name:     "escaped backslashes",
			input:    `{"path": "C:\\Users\\name\\file.txt"}`,
			expected: `\\`,
		},
		{
			name:     "newlines and tabs",
			input:    `{"text": "Line 1\nLine 2\tTabbed"}`,
			expected: `\n`,
		},
		{
			name:     "unicode escapes",
			input:    `{"unicode": "Hello \u0041\u0042\u0043"}`,
			expected: `\u0041`,
		},
		{
			name:     "mixed escapes",
			input:    `{"complex": "Quote: \"\\n\\t\\u0041\""}`,
			expected: `\"`,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ColorizeJSON(tt.input)

			// Check if the result contains the expected escape sequences
			if !strings.Contains(result, tt.expected) {
				t.Errorf("Expected result to contain %q, got: %s", tt.expected, result)
			}

			// Also verify that the original JSON structure is preserved
			// Remove all color codes to get the plain text
			plainResult := removeColorCodes(result)

			// The plain result should contain the original escape sequences
			if !strings.Contains(plainResult, tt.expected) {
				t.Errorf("Expected plain result to contain %q, got: %s", tt.expected, plainResult)
			}
		})
	}
}

func TestColorizeJSONBasicSyntax(t *testing.T) {
	input := `{"name": "test", "number": 123, "boolean": true, "null": null, "array": [1, 2, 3]}`
	result := ColorizeJSON(input)

	// Basic checks that the function doesn't break
	if result == "" {
		t.Error("Result should not be empty")
	}

	// Remove color codes for easier testing
	plainResult := removeColorCodes(result)

	if !strings.Contains(plainResult, "test") {
		t.Error("Result should contain the original string value")
	}

	if !strings.Contains(plainResult, "123") {
		t.Error("Result should contain the original number value")
	}

	if !strings.Contains(plainResult, "true") {
		t.Error("Result should contain the original boolean value")
	}

	if !strings.Contains(plainResult, "null") {
		t.Error("Result should contain the original null value")
	}
}

// removeColorCodes removes ANSI color codes from a string for testing purposes
func removeColorCodes(s string) string {
	// This is a simple implementation - in a real scenario you might want a more robust solution
	var result strings.Builder
	var i int

	for i < len(s) {
		if s[i] == '\x1b' && i+1 < len(s) && s[i+1] == '[' {
			// Skip until we find 'm' (end of color code)
			for i < len(s) && s[i] != 'm' {
				i++
			}
		} else {
			result.WriteByte(s[i])
		}
		i++
	}

	return result.String()
}
