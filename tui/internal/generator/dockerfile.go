// ABOUTME: Dockerfile-specific logic for plugin updates: custom-Dockerfile opt-out and ENV preservation.
// ABOUTME: Keeps added ENV vars and value changes while updating everything else from the template.

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
package generator

import (
	"regexp"
	"strings"
)

// customDockerfileRe matches the CUSTOM_DOCKERFILE ARG set to true.
var customDockerfileRe = regexp.MustCompile(`(?m)^ARG\s+CUSTOM_DOCKERFILE\s*=\s*true\s*$`)

// IsCustomDockerfile reports whether the Dockerfile has opted out of
// automatic updates via ARG CUSTOM_DOCKERFILE=true.
func IsCustomDockerfile(content []byte) bool {
	return customDockerfileRe.Match(content)
}

// envLineRe matches Dockerfile ENV directives in both KEY=VALUE and KEY VALUE forms.
var envLineRe = regexp.MustCompile(`(?m)^(ENV\s+(\w+)[\s=].*)$`)

// envEntry pairs an ENV variable name with its full Dockerfile line.
type envEntry struct {
	name string
	line string
}

// parseEnvLines extracts all ENV directives from Dockerfile content, preserving order.
func parseEnvLines(content []byte) []envEntry {
	var entries []envEntry
	for _, match := range envLineRe.FindAllStringSubmatch(string(content), -1) {
		entries = append(entries, envEntry{name: match[2], line: match[1]})
	}
	return entries
}

// PreserveDockerfileEnvVars merges ENV directives from an existing Dockerfile
// into a newly rendered one. User-added ENV vars and user-modified values are
// preserved; everything else comes from the template.
func PreserveDockerfileEnvVars(oldContent, newContent []byte) []byte {
	oldEntries := parseEnvLines(oldContent)
	newEntries := parseEnvLines(newContent)

	// Build lookup maps
	oldByName := make(map[string]string, len(oldEntries))
	for _, e := range oldEntries {
		oldByName[e.name] = e.line
	}
	newNames := make(map[string]bool, len(newEntries))
	for _, e := range newEntries {
		newNames[e.name] = true
	}

	result := string(newContent)

	// Replace each template ENV line with the old version, preserving user values
	for _, e := range newEntries {
		if oldLine, ok := oldByName[e.name]; ok {
			result = strings.Replace(result, e.line, oldLine, 1)
		}
	}

	// Collect user-added ENV vars (present in old but absent from template), preserving order
	var additions []string
	for _, e := range oldEntries {
		if !newNames[e.name] {
			additions = append(additions, e.line)
		}
	}

	// Insert additions after the last ENV line in the result
	if len(additions) > 0 {
		lines := strings.Split(result, "\n")
		lastEnvIdx := -1
		for i, line := range lines {
			if envLineRe.MatchString(line) {
				lastEnvIdx = i
			}
		}
		if lastEnvIdx >= 0 {
			merged := make([]string, 0, len(lines)+len(additions))
			merged = append(merged, lines[:lastEnvIdx+1]...)
			merged = append(merged, additions...)
			merged = append(merged, lines[lastEnvIdx+1:]...)
			result = strings.Join(merged, "\n")
		}
	}

	return []byte(result)
}
