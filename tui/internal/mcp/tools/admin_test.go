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
// ABOUTME: Request-level tests for admin MCP handlers
// ABOUTME: Verifies property updates and snapshot lookups dispatch correctly

package tools

import (
	"context"
	"testing"

	"github.com/google/uuid"

	"github.com/deltafi/tui/graphql"
)

func TestUpdateSystemPropertiesRequest(t *testing.T) {
	var got []graphql.KeyValueInput
	original := updateProperties
	updateProperties = func(updates []graphql.KeyValueInput) (*graphql.UpdatePropertiesResponse, error) {
		got = updates
		return &graphql.UpdatePropertiesResponse{UpdateProperties: true}, nil
	}
	t.Cleanup(func() { updateProperties = original })

	result, err := updateSystemProperties(context.Background(), callToolRequest(map[string]any{
		"updates": map[string]any{"deltafi.a": "1", "deltafi.b": nil},
	}))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", resultText(t, result), err)
	}

	byKey := map[string]*string{}
	for _, kv := range got {
		byKey[kv.Key] = kv.Value
	}
	if v, ok := byKey["deltafi.a"]; !ok || v == nil || *v != "1" {
		t.Errorf("deltafi.a: got %v", v)
	}
	if v, ok := byKey["deltafi.b"]; !ok || v != nil {
		t.Errorf("deltafi.b should be nil to clear, got %v", v)
	}
}

func TestGetSnapshotRequest(t *testing.T) {
	id := uuid.New()
	var gotID uuid.UUID
	original := getSystemSnapshot
	getSystemSnapshot = func(snapshotId uuid.UUID) (*graphql.GetSystemSnapshotResponse, error) {
		gotID = snapshotId
		return &graphql.GetSystemSnapshotResponse{GetSystemSnapshot: &graphql.GetSystemSnapshotGetSystemSnapshot{}}, nil
	}
	t.Cleanup(func() { getSystemSnapshot = original })

	result, err := getSnapshot(context.Background(), callToolRequest(map[string]any{"id": id.String()}))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", resultText(t, result), err)
	}
	if gotID != id {
		t.Fatalf("got id %v, want %v", gotID, id)
	}
}
