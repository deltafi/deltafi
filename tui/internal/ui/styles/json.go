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
)

// JSON syntax highlighting styles
var (
	jsonKeyStyle     = NewStyleBuilder().WithForeground(LightBlue).Bold().Build()
	jsonStringStyle  = NewStyleBuilder().WithForeground(LightGreen).Build()
	jsonNumberStyle  = NewStyleBuilder().WithForeground(Peach).Build()
	jsonBooleanStyle = NewStyleBuilder().WithForeground(Yellow).Build()
	jsonNullStyle    = NewStyleBuilder().WithForeground(Gray).Italic().Build()
	jsonBracketStyle = NewStyleBuilder().WithForeground(Text).Build()
	jsonCommaStyle   = NewStyleBuilder().WithForeground(Text).Build()
)

// colorizeJSON adds syntax highlighting to JSON output
func ColorizeJSON(jsonStr string) string {
	// Use a token-based approach to preserve escape sequences
	return colorizeJSONTokens(jsonStr)
}

// colorizeJSONTokens applies syntax highlighting while preserving the original JSON structure
func colorizeJSONTokens(jsonStr string) string {
	var result strings.Builder
	var i int

	for i < len(jsonStr) {
		char := jsonStr[i]

		switch char {
		case '{', '}':
			result.WriteString(jsonBracketStyle.Render(string(char)))
		case '[', ']':
			result.WriteString(jsonBracketStyle.Render(string(char)))
		case ',':
			result.WriteString(jsonCommaStyle.Render(string(char)))
		case ':':
			result.WriteString(string(char))
		case '"':
			// Handle string literals (keys and values)
			start := i
			i++ // Skip opening quote

			// Find the closing quote, handling escaped quotes
			for i < len(jsonStr) {
				if jsonStr[i] == '"' && jsonStr[i-1] != '\\' {
					break
				}
				i++
			}

			if i < len(jsonStr) {
				// Extract the complete string including quotes
				quotedStr := jsonStr[start : i+1]

				// Determine if this is a key or value by looking at context
				if isJSONKey(jsonStr, start) {
					result.WriteString(jsonKeyStyle.Render(quotedStr))
				} else {
					result.WriteString(jsonStringStyle.Render(quotedStr))
				}
			} else {
				// Unterminated string, just render as-is
				result.WriteString(jsonStringStyle.Render(jsonStr[start:]))
			}
		case 't':
			// Check for "true"
			if i+3 < len(jsonStr) && jsonStr[i:i+4] == "true" {
				result.WriteString(jsonBooleanStyle.Render("true"))
				i += 3
			} else {
				result.WriteByte(char)
			}
		case 'f':
			// Check for "false"
			if i+4 < len(jsonStr) && jsonStr[i:i+5] == "false" {
				result.WriteString(jsonBooleanStyle.Render("false"))
				i += 4
			} else {
				result.WriteByte(char)
			}
		case 'n':
			// Check for "null"
			if i+3 < len(jsonStr) && jsonStr[i:i+4] == "null" {
				result.WriteString(jsonNullStyle.Render("null"))
				i += 3
			} else {
				result.WriteByte(char)
			}
		case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9':
			// Handle numbers
			start := i
			for i < len(jsonStr) {
				c := jsonStr[i]
				if !((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
					break
				}
				i++
			}
			numberStr := jsonStr[start:i]
			result.WriteString(jsonNumberStyle.Render(numberStr))
			i-- // Adjust for the loop increment
		case ' ', '\t', '\n', '\r':
			// Preserve whitespace
			result.WriteByte(char)
		default:
			result.WriteByte(char)
		}

		i++
	}

	return result.String()
}

// isJSONKey determines if a quoted string at the given position is a JSON key
func isJSONKey(jsonStr string, quotePos int) bool {
	// Look backwards to find the context
	for i := quotePos - 1; i >= 0; i-- {
		char := jsonStr[i]
		switch char {
		case ' ', '\t', '\n', '\r':
			continue
		case ':':
			// If we find a colon before the quote, this is a value, not a key
			return false
		case '{', '[', ',':
			// If we find these before the quote, this is likely a key
			return true
		default:
			// Any other character suggests this might be part of a value
			return false
		}
	}
	return false
}
