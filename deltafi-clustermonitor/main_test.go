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

package main

import (
	"testing"
)

func TestEnvOrDefault_Set(t *testing.T) {
	t.Setenv("TEST_ENV_VAR", "custom")
	got := envOrDefault("TEST_ENV_VAR", "default")
	if got != "custom" {
		t.Errorf("expected %q, got %q", "custom", got)
	}
}

func TestEnvOrDefault_Unset(t *testing.T) {
	got := envOrDefault("TEST_ENV_UNSET_12345", "fallback")
	if got != "fallback" {
		t.Errorf("expected %q, got %q", "fallback", got)
	}
}

func TestEnvOrDefault_Empty(t *testing.T) {
	// Empty string should fall through to the default
	t.Setenv("TEST_ENV_EMPTY", "")
	got := envOrDefault("TEST_ENV_EMPTY", "fallback")
	if got != "fallback" {
		t.Errorf("expected %q, got %q", "fallback", got)
	}
}
