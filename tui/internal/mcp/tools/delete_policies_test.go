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
// ABOUTME: Tests for timed delete policy MCP tool JSON-to-input decoding and validation
// ABOUTME: Covers required enabled/deleteMetadata booleans, afterCreate/afterComplete requirement, optional flow/minBytes, and id parsing

package tools

import (
	"strings"
	"testing"

	"github.com/google/uuid"
)

func TestDecodeTimedDeletePolicyInput_AfterCreate(t *testing.T) {
	policy, err := decodeTimedDeletePolicyInput(map[string]any{
		"name":           "purge-24h",
		"enabled":        true,
		"afterCreate":    "PT24H",
		"deleteMetadata": false,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if policy.Name != "purge-24h" {
		t.Errorf("name: got %q", policy.Name)
	}
	if !policy.Enabled {
		t.Errorf("enabled: got false")
	}
	if policy.AfterCreate == nil || *policy.AfterCreate != "PT24H" {
		t.Errorf("afterCreate: got %v", policy.AfterCreate)
	}
	if policy.AfterComplete != nil {
		t.Errorf("afterComplete should be nil, got %v", policy.AfterComplete)
	}
	if policy.DeleteMetadata {
		t.Errorf("deleteMetadata: got true")
	}
}

func TestDecodeTimedDeletePolicyInput_AfterComplete(t *testing.T) {
	id := uuid.New()
	minBytes := int64(1024)
	policy, err := decodeTimedDeletePolicyInput(map[string]any{
		"id":             id.String(),
		"name":           "purge-completed",
		"enabled":        false,
		"flow":           "my-flow",
		"afterComplete":  "P7D",
		"minBytes":       float64(minBytes),
		"deleteMetadata": true,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if policy.Id == nil || *policy.Id != id {
		t.Errorf("id: got %v", policy.Id)
	}
	if policy.Flow == nil || *policy.Flow != "my-flow" {
		t.Errorf("flow: got %v", policy.Flow)
	}
	if policy.AfterComplete == nil || *policy.AfterComplete != "P7D" {
		t.Errorf("afterComplete: got %v", policy.AfterComplete)
	}
	if policy.MinBytes == nil || *policy.MinBytes != minBytes {
		t.Errorf("minBytes: got %v", policy.MinBytes)
	}
	if !policy.DeleteMetadata {
		t.Errorf("deleteMetadata: got false")
	}
}

func TestDecodeTimedDeletePolicyInput_RejectsNonObject(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput("not-an-object")
	if err == nil {
		t.Fatal("expected error for non-object input")
	}
}

func TestDecodeTimedDeletePolicyInput_RequiresName(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput(map[string]any{
		"enabled":        true,
		"afterCreate":    "PT1H",
		"deleteMetadata": false,
	})
	if err == nil || !strings.Contains(err.Error(), "name") {
		t.Fatalf("expected name-required error, got %v", err)
	}
}

func TestDecodeTimedDeletePolicyInput_RequiresDuration(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput(map[string]any{
		"name":           "no-duration",
		"enabled":        true,
		"deleteMetadata": false,
	})
	if err == nil || !strings.Contains(err.Error(), "afterCreate") {
		t.Fatalf("expected duration-required error, got %v", err)
	}
}

func TestDecodeTimedDeletePolicyInput_RequiresEnabled(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput(map[string]any{
		"name":           "no-enabled",
		"afterCreate":    "PT1H",
		"deleteMetadata": false,
	})
	if err == nil || !strings.Contains(err.Error(), "enabled") {
		t.Fatalf("expected enabled-required error, got %v", err)
	}
}

func TestDecodeTimedDeletePolicyInput_RequiresDeleteMetadata(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput(map[string]any{
		"name":        "no-delete-metadata",
		"enabled":     true,
		"afterCreate": "PT1H",
	})
	if err == nil || !strings.Contains(err.Error(), "deleteMetadata") {
		t.Fatalf("expected deleteMetadata-required error, got %v", err)
	}
}

func TestDecodeTimedDeletePolicyInput_RejectsNullRequiredBooleans(t *testing.T) {
	for _, field := range []string{"enabled", "deleteMetadata"} {
		t.Run(field, func(t *testing.T) {
			input := map[string]any{
				"name": "invalid", "enabled": true, "afterCreate": "PT1H", "deleteMetadata": false,
			}
			input[field] = nil
			_, err := decodeTimedDeletePolicyInput(input)
			if err == nil || !strings.Contains(err.Error(), field+" must be a boolean") {
				t.Fatalf("expected boolean validation error, got %v", err)
			}
		})
	}
}

func TestDecodeTimedDeletePolicyInput_RejectsUnknownField(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput(map[string]any{
		"name": "invalid", "enabled": true, "afterCreate": "PT1H", "deleteMetadata": false,
		"flwo": "typo",
	})
	if err == nil || !strings.Contains(err.Error(), "unknown field") {
		t.Fatalf("expected unknown-field error, got %v", err)
	}
}

func TestDecodeTimedDeletePolicyInput_InvalidUUID(t *testing.T) {
	_, err := decodeTimedDeletePolicyInput(map[string]any{
		"id":             "not-a-uuid",
		"name":           "purge",
		"enabled":        true,
		"afterCreate":    "PT1H",
		"deleteMetadata": false,
	})
	if err == nil {
		t.Fatal("expected error for invalid id")
	}
}

func TestDecodeTimedDeletePolicies_EmptyRequiresReplaceAll(t *testing.T) {
	if _, err := decodeTimedDeletePolicies([]any{}, false); err == nil {
		t.Fatal("expected empty list to require replace_all")
	}
	policies, err := decodeTimedDeletePolicies([]any{}, true)
	if err != nil {
		t.Fatalf("replace_all should allow clearing policies: %v", err)
	}
	if len(policies) != 0 {
		t.Fatalf("expected empty policy list, got %d", len(policies))
	}
}
