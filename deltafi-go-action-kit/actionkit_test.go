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
// ABOUTME: Tests for core types: PluginCoordinates, ActionContext, Variable, FlowPlan, envOrDefault.
// ABOUTME: Verifies JSON serialization, FlowPlan round-trip, Deltafile operations, and environment variable handling.

package actionkit

import (
	"encoding/json"
	"os"
	"strings"
	"testing"
)

func TestPluginCoordinatesJSON(t *testing.T) {
	coords := PluginCoordinates{
		GroupID:    "org.deltafi",
		ArtifactID: "my-plugin",
		Version:    "1.0.0",
	}

	data, err := json.Marshal(coords)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]string
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal to map: %v", err)
	}

	// Verify JSON field names match Java wire format
	if parsed["groupId"] != "org.deltafi" {
		t.Errorf("groupId = %q, want %q", parsed["groupId"], "org.deltafi")
	}
	if parsed["artifactId"] != "my-plugin" {
		t.Errorf("artifactId = %q, want %q", parsed["artifactId"], "my-plugin")
	}
	if parsed["version"] != "1.0.0" {
		t.Errorf("version = %q, want %q", parsed["version"], "1.0.0")
	}

	// Round-trip
	var roundTripped PluginCoordinates
	if err := json.Unmarshal(data, &roundTripped); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if roundTripped != coords {
		t.Errorf("round-trip mismatch: got %+v, want %+v", roundTripped, coords)
	}
}

func TestVariableJSON(t *testing.T) {
	v := Variable{
		Name:         "myVar",
		Description:  "A test variable",
		DataType:     "STRING",
		Required:     true,
		DefaultValue: "hello",
	}

	data, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal to map: %v", err)
	}

	if parsed["name"] != "myVar" {
		t.Errorf("name = %q, want %q", parsed["name"], "myVar")
	}
	if parsed["dataType"] != "STRING" {
		t.Errorf("dataType = %q, want %q", parsed["dataType"], "STRING")
	}
	if parsed["required"] != true {
		t.Errorf("required = %v, want true", parsed["required"])
	}
	if parsed["defaultValue"] != "hello" {
		t.Errorf("defaultValue = %q, want %q", parsed["defaultValue"], "hello")
	}
}

func TestVariableJSON_OmitsEmptyOptionals(t *testing.T) {
	v := Variable{
		Name:     "simple",
		DataType: "BOOLEAN",
	}

	data, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if _, ok := parsed["defaultValue"]; ok {
		t.Error("defaultValue should be omitted when nil")
	}
	if _, ok := parsed["value"]; ok {
		t.Error("value should be omitted when nil")
	}
	if _, ok := parsed["masked"]; ok {
		t.Error("masked should be omitted when false")
	}
}

func TestFlowPlanUnmarshalJSON(t *testing.T) {
	raw := `{"name":"my-flow","type":"TRANSFORM","description":"test","subscribe":[{"topic":"t1"}]}`

	var fp FlowPlan
	if err := json.Unmarshal([]byte(raw), &fp); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if fp.Name != "my-flow" {
		t.Errorf("Name = %q, want %q", fp.Name, "my-flow")
	}
	if fp.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want %q", fp.Type, "TRANSFORM")
	}
	if string(fp.Raw) != raw {
		t.Errorf("Raw not preserved: got %q", string(fp.Raw))
	}
}

func TestFlowPlanMarshalJSON(t *testing.T) {
	raw := `{"name":"my-flow","type":"DATA_SINK","egressAction":{"name":"EA","type":"EA"}}`
	var fp FlowPlan
	if err := json.Unmarshal([]byte(raw), &fp); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	data, err := json.Marshal(fp)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	if string(data) != raw {
		t.Errorf("marshal output = %q, want %q", string(data), raw)
	}
}

func TestFlowPlanUnmarshalJSON_InvalidJSON(t *testing.T) {
	var fp FlowPlan
	err := json.Unmarshal([]byte(`not json`), &fp)
	if err == nil {
		t.Error("expected error for invalid JSON")
	}
}

func TestActionContext_TrackSegment(t *testing.T) {
	actx := &ActionContext{DID: "test-did"}

	if len(actx.savedSegments) != 0 {
		t.Fatalf("expected empty savedSegments, got %d", len(actx.savedSegments))
	}

	seg1 := Segment{UUID: "seg-1", DID: "test-did", Size: 100}
	seg2 := Segment{UUID: "seg-2", DID: "test-did", Size: 200}
	actx.trackSegment(seg1)
	actx.trackSegment(seg2)

	if len(actx.savedSegments) != 2 {
		t.Fatalf("expected 2 savedSegments, got %d", len(actx.savedSegments))
	}
	if actx.savedSegments[0].UUID != "seg-1" {
		t.Errorf("first segment UUID = %q, want %q", actx.savedSegments[0].UUID, "seg-1")
	}
	if actx.savedSegments[1].UUID != "seg-2" {
		t.Errorf("second segment UUID = %q, want %q", actx.savedSegments[1].UUID, "seg-2")
	}
}

func TestActionContext_ChildContext(t *testing.T) {
	parent := &ActionContext{
		DID:           "parent-did",
		DeltaFileName: "test.txt",
		DataSource:    "my-source",
		FlowName:      "my-flow",
		FlowID:        "flow-123",
		ActionName:    "TestAction",
		ActionVersion: "1.0",
		Hostname:      "host1",
		SystemName:    "deltafi",
		Memo:          "memo-data",
	}

	child := parent.ChildContext()

	// Child DID must be new and non-empty
	if child.DID == "" {
		t.Error("child DID should not be empty")
	}
	if child.DID == parent.DID {
		t.Error("child DID should differ from parent DID")
	}

	// All other fields inherited
	if child.DeltaFileName != parent.DeltaFileName {
		t.Errorf("DeltaFileName = %q, want %q", child.DeltaFileName, parent.DeltaFileName)
	}
	if child.FlowName != parent.FlowName {
		t.Errorf("FlowName = %q, want %q", child.FlowName, parent.FlowName)
	}
	if child.FlowID != parent.FlowID {
		t.Errorf("FlowID = %q, want %q", child.FlowID, parent.FlowID)
	}
	if child.ActionName != parent.ActionName {
		t.Errorf("ActionName = %q, want %q", child.ActionName, parent.ActionName)
	}
	if child.Memo != parent.Memo {
		t.Errorf("Memo = %q, want %q", child.Memo, parent.Memo)
	}
	if child.ContentService != parent.ContentService {
		t.Error("ContentService should be shared with parent")
	}

	// Two children should have different DIDs
	child2 := parent.ChildContext()
	if child.DID == child2.DID {
		t.Error("two children should have different DIDs")
	}
}

func TestJoinConfiguration_JSON(t *testing.T) {
	jc := joinConfiguration{
		MaxAge:      3.0,
		MinNum:      2,
		MaxNum:      10,
		MetadataKey: "groupKey",
	}

	data, err := json.Marshal(jc)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed joinConfiguration
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if parsed.MaxAge != 3.0 {
		t.Errorf("MaxAge = %f", parsed.MaxAge)
	}
	if parsed.MinNum != 2 {
		t.Errorf("MinNum = %d", parsed.MinNum)
	}
	if parsed.MaxNum != 10 {
		t.Errorf("MaxNum = %d", parsed.MaxNum)
	}
	if parsed.MetadataKey != "groupKey" {
		t.Errorf("MetadataKey = %q", parsed.MetadataKey)
	}
}

func TestActionContext_JoinFields(t *testing.T) {
	actx := &ActionContext{
		DID:        "join-did",
		ActionName: "JoinAction",
		Join: &joinConfiguration{
			MaxAge: 5.0,
			MinNum: 3,
			MaxNum: 20,
		},
		JoinedDIDs: []string{"parent-1", "parent-2", "parent-3"},
	}

	if actx.Join == nil {
		t.Fatal("Join should not be nil")
	}
	if actx.Join.MaxAge != 5.0 {
		t.Errorf("Join.MaxAge = %f", actx.Join.MaxAge)
	}
	if len(actx.JoinedDIDs) != 3 {
		t.Fatalf("JoinedDIDs count = %d, want 3", len(actx.JoinedDIDs))
	}
	if actx.JoinedDIDs[1] != "parent-2" {
		t.Errorf("JoinedDIDs[1] = %q", actx.JoinedDIDs[1])
	}
}

func TestSafeStringMap_StoreAndForEach(t *testing.T) {
	var sm SafeStringMap
	sm.Store(map[string]string{"a": "1", "b": "2"})

	got := make(map[string]string)
	sm.ForEach(func(k, v string) {
		got[k] = v
	})

	if len(got) != 2 || got["a"] != "1" || got["b"] != "2" {
		t.Errorf("ForEach returned %v, want {a:1 b:2}", got)
	}
}

func TestSafeStringMap_StoreReplaces(t *testing.T) {
	var sm SafeStringMap
	sm.Store(map[string]string{"old": "data"})
	sm.Store(map[string]string{"new": "data"})

	got := make(map[string]string)
	sm.ForEach(func(k, v string) {
		got[k] = v
	})

	if len(got) != 1 || got["new"] != "data" {
		t.Errorf("ForEach after replace returned %v, want {new:data}", got)
	}
}

func TestSafeStringMap_StoreNil(t *testing.T) {
	var sm SafeStringMap
	sm.Store(map[string]string{"a": "1"})
	sm.Store(nil)

	count := 0
	sm.ForEach(func(k, v string) {
		count++
	})

	if count != 0 {
		t.Errorf("ForEach after Store(nil) visited %d entries, want 0", count)
	}
}

func TestSafeStringMap_ZeroValue(t *testing.T) {
	var sm SafeStringMap

	count := 0
	sm.ForEach(func(k, v string) {
		count++
	})

	if count != 0 {
		t.Errorf("ForEach on zero value visited %d entries, want 0", count)
	}
}

func TestEnvOrDefault(t *testing.T) {
	// Unset var returns fallback
	os.Unsetenv("ACTIONKIT_TEST_VAR")
	if got := envOrDefault("ACTIONKIT_TEST_VAR", "fallback"); got != "fallback" {
		t.Errorf("got %q, want %q", got, "fallback")
	}

	// Set var returns value
	os.Setenv("ACTIONKIT_TEST_VAR", "custom")
	defer os.Unsetenv("ACTIONKIT_TEST_VAR")
	if got := envOrDefault("ACTIONKIT_TEST_VAR", "fallback"); got != "custom" {
		t.Errorf("got %q, want %q", got, "custom")
	}

	// Empty string returns fallback
	os.Setenv("ACTIONKIT_TEST_VAR", "")
	if got := envOrDefault("ACTIONKIT_TEST_VAR", "fallback"); got != "fallback" {
		t.Errorf("got %q for empty env, want %q", got, "fallback")
	}
}

func TestDeltafile_SaveAndLoad(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()

	saved, err := df.SaveStringContent("hello world", "out.txt", "text/plain")
	if err != nil {
		t.Fatalf("SaveStringContent: %v", err)
	}
	if saved.Name != "out.txt" || saved.MediaType != "text/plain" {
		t.Errorf("saved = %+v", saved)
	}

	got, err := saved.LoadString()
	if err != nil {
		t.Fatalf("LoadString: %v", err)
	}
	if got != "hello world" {
		t.Errorf("LoadString = %q, want %q", got, "hello world")
	}

	// Save tracks the segment for orphan cleanup.
	if len(tc.ActionContext.savedSegments) != 1 {
		t.Errorf("savedSegments = %d, want 1", len(tc.ActionContext.savedSegments))
	}
}

func TestDeltafile_SaveBytesAndLoadBytes(t *testing.T) {
	tc := NewTestContext("did-2")
	df := tc.NewDeltafile()
	payload := []byte{0x00, 0x01, 0x02, 0x03}

	saved, err := df.SaveBytesContent(payload, "out.bin", "application/octet-stream")
	if err != nil {
		t.Fatalf("SaveBytesContent: %v", err)
	}

	got, err := saved.LoadBytes()
	if err != nil {
		t.Fatalf("LoadBytes: %v", err)
	}
	if !bytesEqual(got, payload) {
		t.Errorf("LoadBytes = %v, want %v", got, payload)
	}
}

func TestDeltafile_Errorf(t *testing.T) {
	tc := NewTestContext("did-4")
	df := tc.NewDeltafile()

	result := df.Errorf("Failed to save: %v", "disk full")
	if result == nil {
		t.Fatal("Errorf returned nil")
	}
	if !result.hasError {
		t.Error("expected error state")
	}
	if !strings.Contains(result.errCause, "Failed to save") || !strings.Contains(result.errCause, "disk full") {
		t.Errorf("cause = %q", result.errCause)
	}
}

func TestDeltafile_Filterf(t *testing.T) {
	tc := NewTestContext("did-5")
	df := tc.NewDeltafile()
	result := df.Filterf("Skipping prefix %q", "44")
	if result == nil {
		t.Fatal("Filterf returned nil")
	}
	if !result.hasFilter {
		t.Error("expected filter state")
	}
	if result.filterMessage != `Skipping prefix "44"` {
		t.Errorf("message = %q", result.filterMessage)
	}
}

func bytesEqual(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
