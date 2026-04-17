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
// ABOUTME: Tests for Deltafile-to-ActionEvent conversion and IngressResult construction.
// ABOUTME: Verifies transform, split, egress, error, filter, and ingress event generation.

package actionkit

import (
	"testing"
	"time"
)

func testActionContext() *ActionContext {
	return &ActionContext{
		DID:        "test-did-123",
		FlowName:   "test-flow",
		FlowID:     "flow-id-456",
		ActionName: "TestAction",
	}
}

// --- Transform Event ---

func TestDeltafile_TransformEvent(t *testing.T) {
	actx := testActionContext()
	df := newDeltafile(actx, nil, nil)

	df.AddContent(ActionContent{Name: "out.txt"})
	df.AddMetadata("k", "v")
	df.AddAnnotation("a", "b")
	df.DeleteMetadataKeys("old")
	df.LogInfo("transform done")

	start := time.Now().Add(-1 * time.Second)
	event := deltafileToTransformEvent(df, actx, start)

	if event.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want TRANSFORM", event.Type)
	}
	if event.DID != "test-did-123" {
		t.Errorf("DID = %q", event.DID)
	}
	if event.FlowName != "test-flow" {
		t.Errorf("FlowName = %q", event.FlowName)
	}
	if event.FlowID != "flow-id-456" {
		t.Errorf("FlowID = %q", event.FlowID)
	}
	if event.ActionName != "TestAction" {
		t.Errorf("ActionName = %q", event.ActionName)
	}
	if len(event.Transform) != 1 {
		t.Fatalf("Transform count = %d, want 1", len(event.Transform))
	}

	te := event.Transform[0]
	if te.DID != "test-did-123" {
		t.Errorf("transform DID = %q", te.DID)
	}
	if len(te.Content) != 1 {
		t.Errorf("transform content count = %d", len(te.Content))
	}
	if te.Metadata["k"] != "v" {
		t.Errorf("transform metadata k = %q", te.Metadata["k"])
	}
	if te.Annotations["a"] != "b" {
		t.Errorf("transform annotations a = %q", te.Annotations["a"])
	}
	if len(te.DeleteMetadataKeys) != 1 || te.DeleteMetadataKeys[0] != "old" {
		t.Errorf("transform deleteMetadataKeys = %v", te.DeleteMetadataKeys)
	}

	if len(event.Messages) != 1 {
		t.Fatalf("Messages count = %d, want 1", len(event.Messages))
	}
	if event.Messages[0].Severity != SeverityInfo {
		t.Errorf("message severity = %q", event.Messages[0].Severity)
	}
	if event.Messages[0].Message != "transform done" {
		t.Errorf("message text = %q", event.Messages[0].Message)
	}

	// Start/Stop should be parseable and ordered
	startParsed, _ := time.Parse(time.RFC3339Nano, event.Start)
	stopParsed, _ := time.Parse(time.RFC3339Nano, event.Stop)
	if !stopParsed.After(startParsed) && !stopParsed.Equal(startParsed) {
		t.Errorf("Stop %v should be >= Start %v", stopParsed, startParsed)
	}

	// Error, Filter, Ingress should be nil
	if event.Error != nil {
		t.Error("Error should be nil")
	}
	if event.Filter != nil {
		t.Error("Filter should be nil")
	}
	if event.Ingress != nil {
		t.Error("Ingress should be nil")
	}
}

// --- Split Transform Event (multi-child) ---

func TestSplitToTransformEvent(t *testing.T) {
	actx := testActionContext()
	parent := newDeltafile(actx, nil, nil)
	parent.LogInfo("split complete")

	child0 := newEmptyDeltafile("child-0.txt", actx)
	child0.AddContent(ActionContent{Name: "c0.txt"})
	child0.AddMetadata("index", "0")
	child0.AddAnnotation("lang", "Go")
	child0.LogInfo("created child 0")

	child1 := newEmptyDeltafile("child-1.txt", actx)
	child1.AddContent(ActionContent{Name: "c1.txt"})
	child1.AddMetadata("index", "1")
	child1.LogWarn("child 1 warning")

	start := time.Now().Add(-1 * time.Second)
	event := splitToTransformEvent([]*Deltafile{child0, child1}, parent, actx, start)

	// Top-level event
	if event.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want TRANSFORM", event.Type)
	}
	if event.DID != "test-did-123" {
		t.Errorf("DID = %q, want parent DID", event.DID)
	}
	if len(event.Messages) != 1 || event.Messages[0].Message != "split complete" {
		t.Errorf("top-level messages = %v", event.Messages)
	}

	// Transform array
	if len(event.Transform) != 2 {
		t.Fatalf("Transform count = %d, want 2", len(event.Transform))
	}

	te0 := event.Transform[0]
	if te0.Name != "child-0.txt" {
		t.Errorf("child 0 name = %q", te0.Name)
	}
	if te0.DID == actx.DID {
		t.Error("child 0 DID should differ from parent")
	}
	if te0.DID == "" {
		t.Error("child 0 DID should not be empty")
	}
	if len(te0.Content) != 1 || te0.Content[0].Name != "c0.txt" {
		t.Errorf("child 0 content = %v", te0.Content)
	}
	if te0.Metadata["index"] != "0" {
		t.Errorf("child 0 metadata index = %q", te0.Metadata["index"])
	}
	if te0.Annotations["lang"] != "Go" {
		t.Errorf("child 0 annotation lang = %q", te0.Annotations["lang"])
	}
	if len(te0.Messages) != 1 || te0.Messages[0].Message != "created child 0" {
		t.Errorf("child 0 messages = %v", te0.Messages)
	}

	te1 := event.Transform[1]
	if te1.Name != "child-1.txt" {
		t.Errorf("child 1 name = %q", te1.Name)
	}
	if te1.DID == te0.DID {
		t.Error("children should have different DIDs")
	}
	if len(te1.Messages) != 1 || te1.Messages[0].Severity != SeverityWarning {
		t.Errorf("child 1 messages = %v", te1.Messages)
	}

	// Other fields should be nil
	if event.Error != nil {
		t.Error("Error should be nil")
	}
	if event.Ingress != nil {
		t.Error("Ingress should be nil")
	}
}

func TestSplitToTransformEvent_EmptyChildren(t *testing.T) {
	actx := testActionContext()
	parent := newDeltafile(actx, nil, nil)

	event := splitToTransformEvent([]*Deltafile{}, parent, actx, time.Now())

	if event.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want TRANSFORM", event.Type)
	}
	if len(event.Transform) != 0 {
		t.Errorf("Transform count = %d, want 0", len(event.Transform))
	}
}

// --- Egress Event ---

func TestDeltafile_EgressEvent(t *testing.T) {
	actx := testActionContext()
	df := newDeltafile(actx, nil, nil)
	df.LogInfo("egressed")

	event := deltafileToEgressEvent(df, actx, time.Now())

	if event.Type != "EGRESS" {
		t.Errorf("Type = %q, want EGRESS", event.Type)
	}
	if event.Transform != nil {
		t.Error("Transform should be nil")
	}
	if event.Error != nil {
		t.Error("Error should be nil")
	}
	if len(event.Messages) != 1 {
		t.Errorf("Messages count = %d, want 1", len(event.Messages))
	}
}

// --- Ingress Event ---

func TestIngressResult_Construction(t *testing.T) {
	r := NewIngressResult()

	if r.status != "HEALTHY" {
		t.Errorf("default status = %q, want HEALTHY", r.status)
	}
	if r.executeImmediate {
		t.Error("default executeImmediate should be false")
	}
}

func TestIngressResult_Builders(t *testing.T) {
	actx := testActionContext()
	r := NewIngressResult()

	item := newEmptyDeltafile("test-file.txt", actx)
	item.AddContent(ActionContent{Name: "data.txt"})
	item.AddMetadata("mk", "mv")
	item.AddAnnotation("ak", "av")

	r.AddItem(item)
	r.SetMemo("state-42")
	r.SetStatus("UNHEALTHY")
	r.SetStatusMessage("something wrong")
	r.SetExecuteImmediate(true)

	if len(r.items) != 1 {
		t.Fatalf("items count = %d, want 1", len(r.items))
	}
	if r.items[0].Name != "test-file.txt" {
		t.Errorf("deltaFileName = %q", r.items[0].Name)
	}
	if r.memo != "state-42" {
		t.Errorf("memo = %q", r.memo)
	}
	if r.status != "UNHEALTHY" {
		t.Errorf("status = %q", r.status)
	}
	if r.statusMessage != "something wrong" {
		t.Errorf("statusMessage = %q", r.statusMessage)
	}
	if !r.executeImmediate {
		t.Error("executeImmediate should be true")
	}
}

func TestIngressResult_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewIngressResult()

	item := newEmptyDeltafile("file.txt", actx)
	item.AddContent(ActionContent{Name: "data"})
	item.AddMetadata("m", "v")
	item.AddAnnotation("a", "b")
	r.AddItem(item)
	r.SetMemo("memo-1")
	r.SetStatusMessage("ok")

	event := ingressResultToEvent(r, actx, time.Now())

	if event.Type != "INGRESS" {
		t.Errorf("Type = %q, want INGRESS", event.Type)
	}
	if event.Ingress == nil {
		t.Fatal("Ingress should not be nil")
	}
	if event.Ingress.Memo != "memo-1" {
		t.Errorf("memo = %q", event.Ingress.Memo)
	}
	if event.Ingress.Status != "HEALTHY" {
		t.Errorf("status = %q", event.Ingress.Status)
	}
	if event.Ingress.StatusMessage != "ok" {
		t.Errorf("statusMessage = %q", event.Ingress.StatusMessage)
	}
	if event.Ingress.ExecuteImmediate {
		t.Error("executeImmediate should be false")
	}
	if len(event.Ingress.IngressItems) != 1 {
		t.Fatalf("IngressItems count = %d, want 1", len(event.Ingress.IngressItems))
	}

	ei := event.Ingress.IngressItems[0]
	if ei.DID == "" {
		t.Error("item DID should be set")
	}
	if ei.DeltaFileName != "file.txt" {
		t.Errorf("item deltaFileName = %q", ei.DeltaFileName)
	}
	if len(ei.Content) != 1 {
		t.Errorf("item content count = %d", len(ei.Content))
	}
	if ei.Metadata["m"] != "v" {
		t.Errorf("item metadata m = %q", ei.Metadata["m"])
	}
	if ei.Annotations["a"] != "b" {
		t.Errorf("item annotations a = %q", ei.Annotations["a"])
	}
}

// --- Error Event ---

func TestDeltafile_ErrorEvent(t *testing.T) {
	actx := testActionContext()
	df := newDeltafile(actx, nil, nil)
	df.Errorf("bad thing")
	df.SetErrorContext("stack trace")
	df.AddAnnotation("retry", "false")
	df.LogError("something went wrong")

	event := deltafileToErrorEvent(df, actx, time.Now())

	if event.Type != "ERROR" {
		t.Errorf("Type = %q, want ERROR", event.Type)
	}
	if event.Error == nil {
		t.Fatal("Error should not be nil")
	}
	if event.Error.Cause != "bad thing" {
		t.Errorf("cause = %q", event.Error.Cause)
	}
	if event.Error.Context != "stack trace" {
		t.Errorf("context = %q", event.Error.Context)
	}
	if event.Error.Annotations["retry"] != "false" {
		t.Errorf("annotation retry = %q", event.Error.Annotations["retry"])
	}
	if event.Transform != nil {
		t.Error("Transform should be nil")
	}
	if len(event.Messages) != 1 {
		t.Fatalf("Messages count = %d, want 1", len(event.Messages))
	}
	if event.Messages[0].Severity != SeverityError {
		t.Errorf("message severity = %q", event.Messages[0].Severity)
	}
}

// --- Filter Event ---

func TestDeltafile_FilterEvent(t *testing.T) {
	actx := testActionContext()
	df := newDeltafile(actx, nil, nil)
	df.Filterf("no match")
	df.AddAnnotation("category", "spam")

	event := deltafileToFilterEvent(df, actx, time.Now())

	if event.Type != "FILTER" {
		t.Errorf("Type = %q, want FILTER", event.Type)
	}
	if event.Filter == nil {
		t.Fatal("Filter should not be nil")
	}
	if event.Filter.Message != "no match" {
		t.Errorf("message = %q", event.Filter.Message)
	}
	if event.Filter.Annotations["category"] != "spam" {
		t.Errorf("annotation category = %q", event.Filter.Annotations["category"])
	}
	if event.Transform != nil {
		t.Error("Transform should be nil")
	}
	if event.Error != nil {
		t.Error("Error should be nil")
	}
}

// --- Logging ---

func TestDeltafile_LogMethods(t *testing.T) {
	actx := testActionContext()
	df := newDeltafile(actx, nil, nil)

	df.LogInfo("info msg")
	df.LogWarn("warn msg")
	df.LogError("error msg")

	if len(df.messages) != 3 {
		t.Fatalf("messages count = %d, want 3", len(df.messages))
	}

	if df.messages[0].Severity != SeverityInfo {
		t.Errorf("[0] severity = %q", df.messages[0].Severity)
	}
	if df.messages[0].Message != "info msg" {
		t.Errorf("[0] message = %q", df.messages[0].Message)
	}
	if df.messages[0].Source != "TestAction" {
		t.Errorf("[0] source = %q", df.messages[0].Source)
	}

	if df.messages[1].Severity != SeverityWarning {
		t.Errorf("[1] severity = %q", df.messages[1].Severity)
	}
	if df.messages[2].Severity != SeverityError {
		t.Errorf("[2] severity = %q", df.messages[2].Severity)
	}
}

func TestDeltafile_LogFormatting(t *testing.T) {
	actx := testActionContext()

	cases := []struct {
		name    string
		call    func() []LogMessage
		wantMsg string
	}{
		{
			name: "formatted",
			call: func() []LogMessage {
				df := newDeltafile(actx, nil, nil)
				df.LogInfo("n=%d", 42)
				return df.messages
			},
			wantMsg: "n=42",
		},
		{
			name: "plain",
			call: func() []LogMessage {
				df := newDeltafile(actx, nil, nil)
				df.LogInfo("no args here")
				return df.messages
			},
			wantMsg: "no args here",
		},
		{
			name: "multiple args",
			call: func() []LogMessage {
				df := newDeltafile(actx, nil, nil)
				df.LogWarn("%s = %d", "count", 7)
				return df.messages
			},
			wantMsg: "count = 7",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			msgs := tc.call()
			if len(msgs) != 1 {
				t.Fatalf("expected 1 message, got %d", len(msgs))
			}
			if msgs[0].Message != tc.wantMsg {
				t.Errorf("message = %q, want %q", msgs[0].Message, tc.wantMsg)
			}
		})
	}
}

func TestIngressResult_LogMethods(t *testing.T) {
	r := NewIngressResult()
	r.LogInfo("info msg")
	r.LogWarn("warn msg")
	r.LogError("error msg")

	if len(r.messages) != 3 {
		t.Fatalf("messages count = %d, want 3", len(r.messages))
	}
	if r.messages[0].Severity != SeverityInfo {
		t.Errorf("[0] severity = %q", r.messages[0].Severity)
	}
	if r.messages[1].Severity != SeverityWarning {
		t.Errorf("[1] severity = %q", r.messages[1].Severity)
	}
	if r.messages[2].Severity != SeverityError {
		t.Errorf("[2] severity = %q", r.messages[2].Severity)
	}
}
