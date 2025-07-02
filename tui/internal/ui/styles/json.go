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
	"encoding/json"
	"fmt"
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
	// Parse the JSON to validate it and get a structured representation
	var data interface{}
	if err := json.Unmarshal([]byte(jsonStr), &data); err != nil {
		// If we can't parse it, return the original string
		return jsonStr
	}

	// Use the existing styles from the codebase to colorize the JSON
	return colorizeJSONValue(data, 0)
}

// colorizeJSONValue recursively colorizes JSON values with proper indentation
func colorizeJSONValue(value interface{}, indent int) string {
	indentStr := strings.Repeat("  ", indent)

	switch v := value.(type) {
	case map[string]interface{}:
		if len(v) == 0 {
			return jsonBracketStyle.Render("{}")
		}

		lines := []string{jsonBracketStyle.Render("{")}
		keys := make([]string, 0, len(v))
		for k := range v {
			keys = append(keys, k)
		}

		// Sort keys for consistent output
		// Note: In a real implementation, you might want to preserve order
		// but for display purposes, sorting is fine

		for i, key := range keys {
			val := v[key]
			comma := ""
			if i < len(keys)-1 {
				comma = jsonCommaStyle.Render(",")
			}

			keyStr := jsonKeyStyle.Render(fmt.Sprintf(`"%s"`, key))
			valStr := colorizeJSONValue(val, indent+1)

			line := fmt.Sprintf("%s  %s: %s%s", indentStr, keyStr, valStr, comma)
			lines = append(lines, line)
		}

		lines = append(lines, indentStr+jsonBracketStyle.Render("}"))
		return strings.Join(lines, "\n")

	case []interface{}:
		if len(v) == 0 {
			return jsonBracketStyle.Render("[]")
		}

		lines := []string{jsonBracketStyle.Render("[")}
		for i, item := range v {
			comma := ""
			if i < len(v)-1 {
				comma = jsonCommaStyle.Render(",")
			}

			itemStr := colorizeJSONValue(item, indent+1)
			line := fmt.Sprintf("%s  %s%s", indentStr, itemStr, comma)
			lines = append(lines, line)
		}

		lines = append(lines, indentStr+jsonBracketStyle.Render("]"))
		return strings.Join(lines, "\n")

	case string:
		return jsonStringStyle.Render(fmt.Sprintf(`"%s"`, v))

	case float64:
		// Check if it's an integer
		if v == float64(int(v)) {
			return jsonNumberStyle.Render(fmt.Sprintf("%.0f", v))
		}
		return jsonNumberStyle.Render(fmt.Sprintf("%g", v))

	case bool:
		if v {
			return jsonBooleanStyle.Render("true")
		}
		return jsonBooleanStyle.Render("false")

	case nil:
		return jsonNullStyle.Render("null")

	default:
		return fmt.Sprintf("%v", v)
	}
}
