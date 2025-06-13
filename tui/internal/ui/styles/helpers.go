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
	"fmt"
	"strings"
)

func formatMoniker(moniker string, msg string) string {
	return "[ " + moniker + " ] " + msg
}

func OK(msg string) string {
	return formatMoniker(
		SuccessStyle.Bold(true).Render(" OK "),
		BaseStyle.Render(msg),
	)
}

func FAIL(msg string) string {
	return formatMoniker(
		ErrorStyle.Bold(true).Render("FAIL"),
		BaseStyle.Render(msg),
	)
}

func ComposeOK(msg string, status string) string {
	return fmt.Sprintf(" %s %-38s %s", SuccessStyle.Bold(true).Render("✔"), msg, SuccessStyle.Bold(false).Render(status))
}

func ComposeFAIL(msg string, status string) string {
	return fmt.Sprintf(" %s %-38s %s", ErrorStyle.Bold(true).Render("✘"), msg, ErrorStyle.Render(status))
}

func RenderErrorString(msg string) string {
	return fmt.Sprintf("         %s %s", ErrorStyle.Bold(true).Render("Error:"), msg)
}

func RenderError(error error) string {
	return fmt.Sprintf("         %s %s", ErrorStyle.Bold(true).Render("Error:"), error.Error())
}

func RenderErrorWithContext(error error, context string) string {
	return RenderErrorStringWithContext(error.Error(), context)
}

func RenderErrorStringWithContext(error string, context string) string {
	return fmt.Sprintf("         %s %s %s", ErrorStyle.Bold(true).Render("Error:"), context, error)
}

func RenderErrors(errors []error) string {
	if errors == nil || len(errors) == 0 {
		return ""
	}

	errorStrings := make([]string, len(errors))
	for i, err := range errors {
		errorStrings[i] = err.Error()
	}

	return fmt.Sprintf("         %s %s", ErrorStyle.Bold(true).Render("Error:"), strings.Join(errorStrings, "\n                "))
}

func RenderErrorStringsWithContext(errors []string, context string) string {
	if errors == nil || len(errors) == 0 {
		return ""
	}

	return fmt.Sprintf("         %s %s\n                %s", ErrorStyle.Bold(true).Render("Error:"), context, strings.Join(errors, "\n                "))
}
