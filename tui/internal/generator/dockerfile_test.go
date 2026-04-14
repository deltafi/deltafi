// ABOUTME: Tests for Dockerfile ENV variable preservation during plugin updates.
// ABOUTME: Covers added vars, changed values, removed template vars, and no-change scenarios.

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
	"strings"
	"testing"
)

func TestPreserveDockerfileEnvVars_NoChanges(t *testing.T) {
	dockerfile := []byte("FROM alpine\nENV FOO=bar\nRUN echo hi\n")
	result := PreserveDockerfileEnvVars(dockerfile, dockerfile)
	if string(result) != string(dockerfile) {
		t.Errorf("expected identical content when old == new\ngot:  %q\nwant: %q", string(result), string(dockerfile))
	}
}

func TestPreserveDockerfileEnvVars_PreservesChangedValue(t *testing.T) {
	old := []byte("FROM alpine\nENV ACTION_THREADS=4\nRUN echo hi\n")
	new := []byte("FROM alpine\nENV ACTION_THREADS=1\nRUN echo hi\n")

	result := PreserveDockerfileEnvVars(old, new)
	if !strings.Contains(string(result), "ENV ACTION_THREADS=4") {
		t.Errorf("expected user-changed value to be preserved, got:\n%s", string(result))
	}
	if strings.Contains(string(result), "ENV ACTION_THREADS=1") {
		t.Error("expected template default value to be replaced")
	}
}

func TestPreserveDockerfileEnvVars_PreservesAddedVars(t *testing.T) {
	old := []byte("FROM alpine\nENV FOO=bar\nENV CUSTOM_VAR=hello\nRUN echo hi\n")
	new := []byte("FROM alpine\nENV FOO=bar\nRUN echo hi\n")

	result := PreserveDockerfileEnvVars(old, new)
	if !strings.Contains(string(result), "ENV CUSTOM_VAR=hello") {
		t.Errorf("expected user-added ENV var to be preserved, got:\n%s", string(result))
	}
}

func TestPreserveDockerfileEnvVars_AddedVarsInsertedAfterLastEnv(t *testing.T) {
	old := []byte("FROM alpine\nENV FOO=bar\nENV ADDED=yes\nRUN echo hi\n")
	new := []byte("FROM alpine\nENV FOO=bar\nRUN echo hi\n")

	result := string(PreserveDockerfileEnvVars([]byte(old), []byte(new)))
	lines := strings.Split(result, "\n")

	// ADDED should appear after FOO and before RUN
	fooIdx, addedIdx, runIdx := -1, -1, -1
	for i, line := range lines {
		switch {
		case strings.HasPrefix(line, "ENV FOO"):
			fooIdx = i
		case strings.HasPrefix(line, "ENV ADDED"):
			addedIdx = i
		case strings.HasPrefix(line, "RUN"):
			runIdx = i
		}
	}
	if addedIdx <= fooIdx {
		t.Errorf("ADDED (line %d) should be after FOO (line %d)", addedIdx, fooIdx)
	}
	if addedIdx >= runIdx {
		t.Errorf("ADDED (line %d) should be before RUN (line %d)", addedIdx, runIdx)
	}
}

func TestPreserveDockerfileEnvVars_NewTemplateVarsAdded(t *testing.T) {
	old := []byte("FROM alpine\nENV FOO=bar\nRUN echo hi\n")
	new := []byte("FROM alpine\nENV FOO=bar\nENV NEW_TEMPLATE_VAR=default\nRUN echo hi\n")

	result := PreserveDockerfileEnvVars(old, new)
	if !strings.Contains(string(result), "ENV NEW_TEMPLATE_VAR=default") {
		t.Errorf("expected new template ENV var to appear, got:\n%s", string(result))
	}
}

func TestPreserveDockerfileEnvVars_CombinedScenario(t *testing.T) {
	// User changed ACTION_THREADS from 1 to 4 and added MY_VAR
	old := []byte(strings.Join([]string{
		"FROM alpine",
		"ENV ACTION_THREADS=4",
		"ENV TABLE_LOOKUP_CACHE_TTL=10s",
		"ENV MY_VAR=custom",
		"RUN echo hi",
		"",
	}, "\n"))

	// Template updates: changes default, adds a new var
	new := []byte(strings.Join([]string{
		"FROM alpine",
		"ENV ACTION_THREADS=1",
		"ENV TABLE_LOOKUP_CACHE_TTL=30s",
		"ENV ARTIFACT_CACHE_TTL=10s",
		"RUN echo hi",
		"",
	}, "\n"))

	result := string(PreserveDockerfileEnvVars(old, new))

	// User's changed value preserved
	if !strings.Contains(result, "ENV ACTION_THREADS=4") {
		t.Error("expected ACTION_THREADS=4 (user value) to be preserved")
	}
	// User's changed value for TABLE_LOOKUP preserved
	if !strings.Contains(result, "ENV TABLE_LOOKUP_CACHE_TTL=10s") {
		t.Error("expected TABLE_LOOKUP_CACHE_TTL=10s (user value) to be preserved")
	}
	// New template var present
	if !strings.Contains(result, "ENV ARTIFACT_CACHE_TTL=10s") {
		t.Error("expected new template var ARTIFACT_CACHE_TTL to be present")
	}
	// User-added var preserved
	if !strings.Contains(result, "ENV MY_VAR=custom") {
		t.Error("expected user-added MY_VAR to be preserved")
	}
}

func TestPreserveDockerfileEnvVars_SpaceSyntax(t *testing.T) {
	old := []byte("FROM alpine\nENV FOO bar\nRUN echo hi\n")
	new := []byte("FROM alpine\nENV FOO baz\nRUN echo hi\n")

	result := PreserveDockerfileEnvVars(old, new)
	if !strings.Contains(string(result), "ENV FOO bar") {
		t.Errorf("expected space-syntax ENV value to be preserved, got:\n%s", string(result))
	}
}

func TestPreserveDockerfileEnvVars_NoEnvLines(t *testing.T) {
	old := []byte("FROM alpine\nRUN echo hi\n")
	new := []byte("FROM alpine\nRUN echo bye\n")

	result := PreserveDockerfileEnvVars(old, new)
	if string(result) != string(new) {
		t.Errorf("expected new content unchanged when no ENV lines exist\ngot:  %q\nwant: %q", string(result), string(new))
	}
}

func TestPreserveDockerfileEnvVars_MultipleAddedVarsPreserveOrder(t *testing.T) {
	old := []byte("FROM alpine\nENV FOO=1\nENV ALPHA=a\nENV BETA=b\nRUN echo hi\n")
	new := []byte("FROM alpine\nENV FOO=1\nRUN echo hi\n")

	result := string(PreserveDockerfileEnvVars([]byte(old), []byte(new)))
	alphaIdx := strings.Index(result, "ENV ALPHA=a")
	betaIdx := strings.Index(result, "ENV BETA=b")
	if alphaIdx < 0 || betaIdx < 0 {
		t.Fatalf("expected both ALPHA and BETA to be preserved, got:\n%s", result)
	}
	if alphaIdx > betaIdx {
		t.Error("expected user-added ENV vars to preserve their original order")
	}
}

func TestIsCustomDockerfile_True(t *testing.T) {
	content := []byte("ARG CUSTOM_DOCKERFILE=true\nFROM alpine\nRUN echo hi\n")
	if !IsCustomDockerfile(content) {
		t.Error("expected IsCustomDockerfile to return true")
	}
}

func TestIsCustomDockerfile_False(t *testing.T) {
	content := []byte("ARG CUSTOM_DOCKERFILE=false\nFROM alpine\nRUN echo hi\n")
	if IsCustomDockerfile(content) {
		t.Error("expected IsCustomDockerfile to return false for CUSTOM_DOCKERFILE=false")
	}
}

func TestIsCustomDockerfile_Missing(t *testing.T) {
	content := []byte("FROM alpine\nRUN echo hi\n")
	if IsCustomDockerfile(content) {
		t.Error("expected IsCustomDockerfile to return false when ARG is absent")
	}
}
