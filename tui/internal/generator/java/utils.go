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
	"fmt"
	"strings"
	"unicode"
)

// PluginNameToPackageName converts a kebab-case plugin name to a Java package name
// Example: "my-plugin" -> "my.plugin"
func PluginNameToPackageName(pluginName string) string {
	return strings.ReplaceAll(pluginName, "-", ".")
}

// PluginNameToClassName converts a kebab-case plugin name to a PascalCase class name
// Example: "my-plugin" -> "MyPlugin"
func PluginNameToClassName(pluginName string) string {
	parts := strings.Split(pluginName, "-")
	var result strings.Builder
	for _, part := range parts {
		if len(part) > 0 {
			result.WriteString(strings.ToUpper(part[:1]) + part[1:])
		}
	}
	return result.String()
}

// ActionNameToClassName converts a kebab-case action name to a PascalCase class name
// Example: "my-new-action" -> "MyNewAction"
func ActionNameToClassName(actionName string) string {
	return PluginNameToClassName(actionName)
}

// ActionNameToFileName converts an action name to a Java file name (PascalCase)
// Handles kebab-case, PascalCase, and mixed case
// Examples: "my-new-action" -> "MyNewAction", "Super" -> "Super", "SuperDuper" -> "SuperDuper"
func ActionNameToFileName(actionName string) string {
	if actionName == "" {
		return ""
	}

	// First normalize hyphens - convert to PascalCase
	parts := strings.Split(actionName, "-")
	var result strings.Builder
	for _, part := range parts {
		if len(part) > 0 {
			// Handle mixed case (e.g., "SuperDuper")
			if unicode.IsUpper(rune(part[0])) {
				// Already PascalCase, keep as-is
				result.WriteString(part)
			} else {
				// Convert to PascalCase
				result.WriteString(strings.ToUpper(part[:1]) + part[1:])
			}
		}
	}

	// If the original had no hyphens and starts with uppercase, preserve it
	if !strings.Contains(actionName, "-") && unicode.IsUpper(rune(actionName[0])) {
		return actionName
	}

	return result.String()
}

// validateName validates that a name is valid (starts with letter, contains only letters, digits, and hyphens)
func validateName(name, nameType string) error {
	if len(name) == 0 {
		return &ValidationError{Message: fmt.Sprintf("%s name is required", nameType)}
	}

	if !unicode.IsLetter(rune(name[0])) {
		return &ValidationError{Message: fmt.Sprintf("%s name must start with a letter", nameType)}
	}

	for _, r := range name {
		if !unicode.IsLetter(r) && !unicode.IsDigit(r) && r != '-' {
			return &ValidationError{Message: fmt.Sprintf("%s name must contain only letters, digits, and '-'", nameType)}
		}
	}

	return nil
}

// ValidatePluginName validates that a plugin name is valid (starts with letter, contains only letters, digits, and hyphens)
func ValidatePluginName(name string) error {
	return validateName(name, "plugin")
}

// ValidateActionName validates that an action name is valid (starts with letter, contains only letters, digits, and hyphens)
func ValidateActionName(name string) error {
	return validateName(name, "action")
}

// ValidationError represents a validation error
type ValidationError struct {
	Message string
}

func (e *ValidationError) Error() string {
	return e.Message
}
