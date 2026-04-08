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
// ABOUTME: Tests for worker deserialization, input building, and orphan detection.
// ABOUTME: Verifies queue event parsing, input construction, and segment collection logic.

package actionkit

import (
	"encoding/json"
	"testing"
)

func TestDeserializeQueueEvent_FullEvent(t *testing.T) {
	raw := `{
		"deltaFileMessages": [{
			"metadata": {"key1": "val1"},
			"contentList": [{
				"name": "file.txt",
				"mediaType": "text/plain",
				"segments": [{"uuid": "seg-1", "offset": 0, "size": 100, "did": "did-1"}]
			}]
		}],
		"actionContext": {
			"did": "did-1",
			"deltaFileName": "test.txt",
			"dataSource": "rest",
			"flowName": "my-flow",
			"flowId": "flow-123",
			"actionName": "TestAction",
			"actionVersion": "1.0",
			"hostname": "host1",
			"systemName": "deltafi",
			"memo": "state-0"
		},
		"actionParams": {"convertToUpper": true, "count": 5},
		"queueName": "TestAction",
		"returnAddress": "core-1"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	// ActionContext
	if event.ActionContext.DID != "did-1" {
		t.Errorf("DID = %q", event.ActionContext.DID)
	}
	if event.ActionContext.DeltaFileName != "test.txt" {
		t.Errorf("DeltaFileName = %q", event.ActionContext.DeltaFileName)
	}
	if event.ActionContext.DataSource != "rest" {
		t.Errorf("DataSource = %q", event.ActionContext.DataSource)
	}
	if event.ActionContext.FlowName != "my-flow" {
		t.Errorf("FlowName = %q", event.ActionContext.FlowName)
	}
	if event.ActionContext.FlowID != "flow-123" {
		t.Errorf("FlowID = %q", event.ActionContext.FlowID)
	}
	if event.ActionContext.ActionName != "TestAction" {
		t.Errorf("ActionName = %q", event.ActionContext.ActionName)
	}
	if event.ActionContext.Memo != "state-0" {
		t.Errorf("Memo = %q", event.ActionContext.Memo)
	}

	// DeltaFileMessages
	if len(event.DeltaFileMessages) != 1 {
		t.Fatalf("DeltaFileMessages count = %d, want 1", len(event.DeltaFileMessages))
	}
	msg := event.DeltaFileMessages[0]
	if msg.Metadata["key1"] != "val1" {
		t.Errorf("metadata key1 = %q", msg.Metadata["key1"])
	}
	if len(msg.ContentList) != 1 {
		t.Fatalf("ContentList count = %d, want 1", len(msg.ContentList))
	}
	if msg.ContentList[0].Name != "file.txt" {
		t.Errorf("content name = %q", msg.ContentList[0].Name)
	}
	if msg.ContentList[0].Segments[0].UUID != "seg-1" {
		t.Errorf("segment uuid = %q", msg.ContentList[0].Segments[0].UUID)
	}

	// Params
	if event.ActionParams["convertToUpper"] != true {
		t.Errorf("param convertToUpper = %v", event.ActionParams["convertToUpper"])
	}
	if event.ActionParams["count"] != float64(5) {
		t.Errorf("param count = %v", event.ActionParams["count"])
	}

	// Queue info
	if event.QueueName != "TestAction" {
		t.Errorf("QueueName = %q", event.QueueName)
	}
	if event.ReturnAddress != "core-1" {
		t.Errorf("ReturnAddress = %q", event.ReturnAddress)
	}
}

func TestDeserializeQueueEvent_EmptyMessages(t *testing.T) {
	raw := `{
		"deltaFileMessages": [],
		"actionContext": {"did": "d1", "actionName": "A"},
		"actionParams": {},
		"queueName": "A"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}
	if len(event.DeltaFileMessages) != 0 {
		t.Errorf("expected empty messages, got %d", len(event.DeltaFileMessages))
	}
}

func TestDeserializeQueueEvent_InvalidJSON(t *testing.T) {
	_, err := deserializeQueueEvent([]byte("not valid json"))
	if err == nil {
		t.Error("expected error for invalid JSON")
	}
}

func TestBuildTransformInput(t *testing.T) {
	actx := &ActionContext{DID: "d1", ActionName: "TestAction"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{
			Metadata:    map[string]string{"mk": "mv"},
			ContentList: []ActionContent{{Name: "file.txt", MediaType: "text/plain"}},
		}},
		ActionParams: map[string]any{"p1": "v1"},
	}

	input := buildTransformInput(actx, event)

	if input.ActionContext != actx {
		t.Error("ActionContext not set")
	}
	if len(input.Content) != 1 || input.Content[0].Name != "file.txt" {
		t.Errorf("Content = %v", input.Content)
	}
	if input.Metadata["mk"] != "mv" {
		t.Errorf("Metadata mk = %q", input.Metadata["mk"])
	}
	if input.Params["p1"] != "v1" {
		t.Errorf("Params p1 = %v", input.Params["p1"])
	}
}

func TestBuildTransformInput_NoMessages(t *testing.T) {
	actx := &ActionContext{DID: "d1"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{},
		ActionParams:      map[string]any{},
	}

	input := buildTransformInput(actx, event)

	if input.Content != nil {
		t.Errorf("Content should be nil, got %v", input.Content)
	}
	if input.Metadata == nil {
		t.Error("Metadata should be initialized to empty map")
	}
}

func TestBuildEgressInput(t *testing.T) {
	actx := &ActionContext{DID: "d1"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{
			Metadata:    map[string]string{"ek": "ev"},
			ContentList: []ActionContent{{Name: "egress.txt"}},
		}},
		ActionParams: map[string]any{},
	}

	input := buildEgressInput(actx, event)

	if input.ActionContext != actx {
		t.Error("ActionContext not set")
	}
	if len(input.Content) != 1 || input.Content[0].Name != "egress.txt" {
		t.Errorf("Content = %v", input.Content)
	}
	if input.Metadata["ek"] != "ev" {
		t.Errorf("Metadata ek = %q", input.Metadata["ek"])
	}
}

func TestBuildIngressInput(t *testing.T) {
	actx := &ActionContext{DID: "d1"}
	event := &queueEvent{
		ActionParams: map[string]any{"ip": "iv"},
	}

	input := buildIngressInput(actx, event)

	if input.ActionContext != actx {
		t.Error("ActionContext not set")
	}
	if input.Params["ip"] != "iv" {
		t.Errorf("Params ip = %v", input.Params["ip"])
	}
}

func TestCollectResultSegments_TransformResult(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResult(actx)
	r.AddContent(ActionContent{
		Segments: []Segment{
			{UUID: "s1"},
			{UUID: "s2"},
		},
	})
	r.AddContent(ActionContent{
		Segments: []Segment{
			{UUID: "s3"},
		},
	})

	segs := collectResultSegments(r)
	if len(segs) != 3 {
		t.Fatalf("expected 3 segments, got %d", len(segs))
	}
	uuids := make(map[string]bool)
	for _, s := range segs {
		uuids[s.UUID] = true
	}
	for _, want := range []string{"s1", "s2", "s3"} {
		if !uuids[want] {
			t.Errorf("missing segment %q", want)
		}
	}
}

func TestCollectResultSegments_IngressResult(t *testing.T) {
	actx := testActionContext()
	r := NewIngressResult(actx)

	item := NewIngressResultItem("file.txt")
	item.AddContent(ActionContent{
		Segments: []Segment{{UUID: "is1"}, {UUID: "is2"}},
	})
	r.AddItem(*item)

	segs := collectResultSegments(r)
	if len(segs) != 2 {
		t.Fatalf("expected 2 segments, got %d", len(segs))
	}
}

func TestCollectResultSegments_ErrorResult(t *testing.T) {
	actx := testActionContext()
	r := NewErrorResult(actx, "err")

	segs := collectResultSegments(r)
	if len(segs) != 0 {
		t.Errorf("expected 0 segments for ErrorResult, got %d", len(segs))
	}
}

func TestCollectResultSegments_FilterResult(t *testing.T) {
	actx := testActionContext()
	r := NewFilterResult(actx, "filtered")

	segs := collectResultSegments(r)
	if len(segs) != 0 {
		t.Errorf("expected 0 segments for FilterResult, got %d", len(segs))
	}
}

func TestCollectResultSegments_EgressResult(t *testing.T) {
	actx := testActionContext()
	r := NewEgressResult(actx)

	segs := collectResultSegments(r)
	if len(segs) != 0 {
		t.Errorf("expected 0 segments for EgressResult, got %d", len(segs))
	}
}

func TestCollectResultSegments_TransformResults(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResults(actx)

	child1 := NewChildTransformResult(actx, "child-0")
	child1.AddContent(ActionContent{
		Name:     "c0.txt",
		Segments: []Segment{{UUID: "seg-a", DID: "child-did-0", Size: 100}},
	})

	child2 := NewChildTransformResult(actx, "child-1")
	child2.AddContent(ActionContent{
		Name:     "c1.txt",
		Segments: []Segment{{UUID: "seg-b", DID: "child-did-1", Size: 200}},
	})
	child2.AddContent(ActionContent{
		Name:     "c2.txt",
		Segments: []Segment{{UUID: "seg-c", DID: "child-did-1", Size: 300}},
	})

	r.AddChild(*child1)
	r.AddChild(*child2)

	segs := collectResultSegments(r)
	if len(segs) != 3 {
		t.Fatalf("expected 3 segments, got %d", len(segs))
	}

	uuids := map[string]bool{}
	for _, seg := range segs {
		uuids[seg.UUID] = true
	}
	for _, expected := range []string{"seg-a", "seg-b", "seg-c"} {
		if !uuids[expected] {
			t.Errorf("missing segment %q", expected)
		}
	}
}

func TestBuildTransformInputs_MultipleMessages(t *testing.T) {
	actx := &ActionContext{DID: "d1", ActionName: "JoinAction"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{
			{
				Metadata:    map[string]string{"index": "0"},
				ContentList: []ActionContent{{Name: "file0.txt"}},
			},
			{
				Metadata:    map[string]string{"index": "1"},
				ContentList: []ActionContent{{Name: "file1.txt"}, {Name: "file2.txt"}},
			},
			{
				ContentList: []ActionContent{{Name: "file3.txt"}},
			},
		},
		ActionParams: map[string]any{"p": "v"},
	}

	inputs := buildTransformInputs(actx, event)

	if len(inputs) != 3 {
		t.Fatalf("expected 3 inputs, got %d", len(inputs))
	}

	// First input
	if inputs[0].Metadata["index"] != "0" {
		t.Errorf("inputs[0] metadata index = %q", inputs[0].Metadata["index"])
	}
	if len(inputs[0].Content) != 1 || inputs[0].Content[0].Name != "file0.txt" {
		t.Errorf("inputs[0] content = %v", inputs[0].Content)
	}

	// Second input
	if len(inputs[1].Content) != 2 {
		t.Errorf("inputs[1] content count = %d, want 2", len(inputs[1].Content))
	}

	// Third input should have nil metadata initialized to empty map
	if inputs[2].Metadata == nil {
		t.Error("inputs[2] metadata should not be nil")
	}

	// All inputs should share ActionContext and Params
	for i, input := range inputs {
		if input.ActionContext != actx {
			t.Errorf("inputs[%d] ActionContext not set", i)
		}
		if input.Params["p"] != "v" {
			t.Errorf("inputs[%d] Params p = %v", i, input.Params["p"])
		}
	}
}

func TestBuildTransformInputs_EmptyMessages(t *testing.T) {
	actx := &ActionContext{DID: "d1"}
	event := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{},
	}

	inputs := buildTransformInputs(actx, event)
	if len(inputs) != 0 {
		t.Errorf("expected 0 inputs, got %d", len(inputs))
	}
}

func TestDeserializeQueueEvent_WithJoinConfig(t *testing.T) {
	raw := `{
		"deltaFileMessages": [
			{"metadata": {"index": "0"}, "contentList": []},
			{"metadata": {"index": "1"}, "contentList": []}
		],
		"actionContext": {
			"did": "join-did",
			"actionName": "JoinAction",
			"join": {
				"maxAge": 3.0,
				"minNum": 2,
				"maxNum": 10,
				"metadataKey": "groupKey"
			},
			"joinedDids": ["parent-1", "parent-2"]
		},
		"actionParams": {},
		"queueName": "JoinAction"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if event.ActionContext.Join == nil {
		t.Fatal("Join should not be nil")
	}
	if event.ActionContext.Join.MaxAge != 3.0 {
		t.Errorf("MaxAge = %f", event.ActionContext.Join.MaxAge)
	}
	if event.ActionContext.Join.MinNum != 2 {
		t.Errorf("MinNum = %d", event.ActionContext.Join.MinNum)
	}
	if event.ActionContext.Join.MaxNum != 10 {
		t.Errorf("MaxNum = %d", event.ActionContext.Join.MaxNum)
	}
	if event.ActionContext.Join.MetadataKey != "groupKey" {
		t.Errorf("MetadataKey = %q", event.ActionContext.Join.MetadataKey)
	}
	if len(event.ActionContext.JoinedDIDs) != 2 {
		t.Fatalf("JoinedDIDs count = %d, want 2", len(event.ActionContext.JoinedDIDs))
	}
	if event.ActionContext.JoinedDIDs[0] != "parent-1" {
		t.Errorf("JoinedDIDs[0] = %q", event.ActionContext.JoinedDIDs[0])
	}
}

func TestDeserializeQueueEvent_NoJoinConfig(t *testing.T) {
	raw := `{
		"deltaFileMessages": [],
		"actionContext": {"did": "d1", "actionName": "A"},
		"actionParams": {},
		"queueName": "A"
	}`

	event, err := deserializeQueueEvent([]byte(raw))
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if event.ActionContext.Join != nil {
		t.Error("Join should be nil when not present")
	}
	if event.ActionContext.JoinedDIDs != nil {
		t.Error("JoinedDIDs should be nil when not present")
	}
}

func TestDeserializeQueueEvent_FullRoundTrip(t *testing.T) {
	// Verify the event can survive JSON round-trip
	original := &queueEvent{
		DeltaFileMessages: []deltaFileMessage{{
			Metadata: map[string]string{"k": "v"},
			ContentList: []ActionContent{{
				Name:      "test.txt",
				MediaType: "text/plain",
				Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 50, DID: "d1"}},
				Tags:      []string{"tag1"},
			}},
		}},
		ActionContext: queueActionContext{
			DID:        "d1",
			FlowName:   "flow",
			ActionName: "Act",
		},
		ActionParams:  map[string]any{"p": "v"},
		QueueName:     "Act",
		ReturnAddress: "ret",
	}

	data, err := json.Marshal(original)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	parsed, err := deserializeQueueEvent(data)
	if err != nil {
		t.Fatalf("deserialize: %v", err)
	}

	if parsed.ActionContext.DID != "d1" {
		t.Errorf("DID = %q", parsed.ActionContext.DID)
	}
	if parsed.ReturnAddress != "ret" {
		t.Errorf("ReturnAddress = %q", parsed.ReturnAddress)
	}
	if parsed.DeltaFileMessages[0].ContentList[0].Tags[0] != "tag1" {
		t.Error("tags not preserved in round-trip")
	}
}
