// ABOUTME: Shared version utilities for plugin code generators.
// ABOUTME: Handles SNAPSHOT version adjustment for dependency resolution.

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
	"fmt"
	"regexp"
	"strconv"
	"strings"
)

// AdjustVersionForSnapshot adjusts a SNAPSHOT version to use the previous patch version.
// For example: 2.34.5-SNAPSHOT -> 2.34.4, 2.34.0-SNAPSHOT -> 2.34.0
func AdjustVersionForSnapshot(version string) string {
	if version == "" {
		return "0.0.0"
	}

	if !strings.Contains(version, "-SNAPSHOT") {
		return version
	}

	versionPart := strings.Split(version, "-SNAPSHOT")[0]

	versionRe := regexp.MustCompile(`^(\d+)\.(\d+)\.(\d+)$`)
	matches := versionRe.FindStringSubmatch(versionPart)
	if len(matches) != 4 {
		return versionPart
	}

	major := matches[1]
	minor := matches[2]
	patch := matches[3]

	patchInt, err := strconv.Atoi(patch)
	if err != nil {
		return versionPart
	}
	if patchInt > 0 {
		patchInt--
	}

	return fmt.Sprintf("%s.%s.%d", major, minor, patchInt)
}
