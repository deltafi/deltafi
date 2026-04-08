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
// ABOUTME: Tests for all result types and their ActionEvent generation.
// ABOUTME: Verifies constructors, builder methods, logging, and toActionEvent wire format output.

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

// --- TransformResult ---

func TestNewTransformResult(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResult(actx)

	if r.actx != actx {
		t.Error("actx not set")
	}
	if r.metadata == nil {
		t.Error("metadata should be initialized")
	}
	if r.annotations == nil {
		t.Error("annotations should be initialized")
	}
	if r.content == nil {
		t.Error("content should be initialized, not nil")
	}
	if len(r.content) != 0 {
		t.Error("content should be empty")
	}
}

func TestTransformResult_Builders(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResult(actx)

	content := ActionContent{Name: "out.txt", MediaType: "text/plain"}
	r.AddContent(content)
	r.AddMetadata("key1", "val1")
	r.AddMetadata("key2", "val2")
	r.DeleteMetadataKey("oldKey")
	r.AddAnnotation("ann1", "annVal1")

	if len(r.content) != 1 {
		t.Errorf("content count = %d, want 1", len(r.content))
	}
	if r.content[0].Name != "out.txt" {
		t.Errorf("content name = %q", r.content[0].Name)
	}
	if r.metadata["key1"] != "val1" {
		t.Errorf("metadata key1 = %q", r.metadata["key1"])
	}
	if r.metadata["key2"] != "val2" {
		t.Errorf("metadata key2 = %q", r.metadata["key2"])
	}
	if len(r.deleteMetadataKeys) != 1 || r.deleteMetadataKeys[0] != "oldKey" {
		t.Errorf("deleteMetadataKeys = %v", r.deleteMetadataKeys)
	}
	if r.annotations["ann1"] != "annVal1" {
		t.Errorf("annotation ann1 = %q", r.annotations["ann1"])
	}
}

func TestTransformResult_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResult(actx)
	r.AddContent(ActionContent{Name: "out.txt"})
	r.AddMetadata("k", "v")
	r.AddAnnotation("a", "b")
	r.DeleteMetadataKey("old")
	r.LogInfo("transform done")

	start := time.Now().Add(-1 * time.Second)
	event := r.toActionEvent(actx, start)

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

// --- TransformResults (multi-child) ---

func TestNewTransformResults(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResults(actx)

	if r.actx != actx {
		t.Error("actx not set")
	}
	if len(r.children) != 0 {
		t.Errorf("children count = %d, want 0", len(r.children))
	}
}

func TestNewChildTransformResult(t *testing.T) {
	actx := testActionContext()
	child := NewChildTransformResult(actx, "child-file.txt")

	if child.deltaFileName != "child-file.txt" {
		t.Errorf("deltaFileName = %q", child.deltaFileName)
	}
	if child.actx.DID == "" {
		t.Error("child DID should be auto-generated")
	}
	if child.actx.DID == actx.DID {
		t.Error("child DID should differ from parent")
	}
	if child.content == nil || len(child.content) != 0 {
		t.Error("content should be initialized empty")
	}
	if child.metadata == nil {
		t.Error("metadata should be initialized")
	}
	if child.annotations == nil {
		t.Error("annotations should be initialized")
	}
}

func TestChildTransformResult_Context(t *testing.T) {
	actx := testActionContext()
	child := NewChildTransformResult(actx, "child.txt")

	ctx := child.Context()
	if ctx == nil {
		t.Fatal("Context() returned nil")
	}
	if ctx.DID == actx.DID {
		t.Error("child context DID should differ from parent")
	}
	if ctx.FlowName != actx.FlowName {
		t.Errorf("child FlowName = %q, want %q", ctx.FlowName, actx.FlowName)
	}
}

func TestChildTransformResult_Builders(t *testing.T) {
	actx := testActionContext()
	child := NewChildTransformResult(actx, "child.txt")

	child.AddContent(ActionContent{Name: "data.txt"})
	child.AddMetadata("k", "v")
	child.AddAnnotation("a", "b")
	child.DeleteMetadataKey("old")
	child.LogInfo("child message")

	if len(child.content) != 1 {
		t.Errorf("content count = %d, want 1", len(child.content))
	}
	if child.metadata["k"] != "v" {
		t.Errorf("metadata k = %q", child.metadata["k"])
	}
	if child.annotations["a"] != "b" {
		t.Errorf("annotation a = %q", child.annotations["a"])
	}
	if len(child.deleteMetadataKeys) != 1 || child.deleteMetadataKeys[0] != "old" {
		t.Errorf("deleteMetadataKeys = %v", child.deleteMetadataKeys)
	}
	if len(child.messages) != 1 || child.messages[0].Message != "child message" {
		t.Errorf("messages = %v", child.messages)
	}
}

func TestTransformResults_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResults(actx)

	child1 := NewChildTransformResult(actx, "child-0.txt")
	child1.AddContent(ActionContent{Name: "c0.txt"})
	child1.AddMetadata("index", "0")
	child1.AddAnnotation("lang", "Go")
	child1.LogInfo("created child 0")

	child2 := NewChildTransformResult(actx, "child-1.txt")
	child2.AddContent(ActionContent{Name: "c1.txt"})
	child2.AddMetadata("index", "1")
	child2.LogWarning("child 1 warning")

	r.AddChild(*child1)
	r.AddChild(*child2)
	r.LogInfo("split complete")

	start := time.Now().Add(-1 * time.Second)
	event := r.toActionEvent(actx, start)

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

func TestTransformResults_EmptyChildren(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResults(actx)

	event := r.toActionEvent(actx, time.Now())

	if event.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want TRANSFORM", event.Type)
	}
	if len(event.Transform) != 0 {
		t.Errorf("Transform count = %d, want 0", len(event.Transform))
	}
}

// --- EgressResult ---

func TestNewEgressResult(t *testing.T) {
	actx := testActionContext()
	r := NewEgressResult(actx)

	if r.actx != actx {
		t.Error("actx not set")
	}
}

func TestEgressResult_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewEgressResult(actx)
	r.LogInfo("egressed")

	event := r.toActionEvent(actx, time.Now())

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

// --- IngressResult ---

func TestNewIngressResult(t *testing.T) {
	actx := testActionContext()
	r := NewIngressResult(actx)

	if r.status != "HEALTHY" {
		t.Errorf("default status = %q, want HEALTHY", r.status)
	}
	if r.executeImmediate {
		t.Error("default executeImmediate should be false")
	}
}

func TestIngressResult_Builders(t *testing.T) {
	actx := testActionContext()
	r := NewIngressResult(actx)

	item := NewIngressResultItem("test-file.txt")
	item.AddContent(ActionContent{Name: "data.txt"})
	item.AddMetadata("mk", "mv")
	item.AddAnnotation("ak", "av")

	r.AddItem(*item)
	r.SetMemo("state-42")
	r.SetStatus("UNHEALTHY")
	r.SetStatusMessage("something wrong")
	r.SetExecuteImmediate(true)

	if len(r.items) != 1 {
		t.Fatalf("items count = %d, want 1", len(r.items))
	}
	if r.items[0].deltaFileName != "test-file.txt" {
		t.Errorf("deltaFileName = %q", r.items[0].deltaFileName)
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

func TestIngressResultItem(t *testing.T) {
	item := NewIngressResultItem("my-file.txt")

	if item.did == "" {
		t.Error("DID should be auto-generated")
	}
	if item.deltaFileName != "my-file.txt" {
		t.Errorf("deltaFileName = %q", item.deltaFileName)
	}
	if item.content == nil {
		t.Error("content should be initialized, not nil")
	}
	if item.metadata == nil {
		t.Error("metadata should be initialized")
	}
	if item.annotations == nil {
		t.Error("annotations should be initialized")
	}

	item.AddContent(ActionContent{Name: "c1"})
	item.AddMetadata("k", "v")
	item.AddAnnotation("a", "b")

	if len(item.content) != 1 {
		t.Errorf("content count = %d", len(item.content))
	}
	if item.metadata["k"] != "v" {
		t.Errorf("metadata k = %q", item.metadata["k"])
	}
	if item.annotations["a"] != "b" {
		t.Errorf("annotations a = %q", item.annotations["a"])
	}
}

func TestIngressResult_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewIngressResult(actx)

	item := NewIngressResultItem("file.txt")
	item.AddContent(ActionContent{Name: "data"})
	item.AddMetadata("m", "v")
	item.AddAnnotation("a", "b")
	r.AddItem(*item)
	r.SetMemo("memo-1")
	r.SetStatusMessage("ok")

	event := r.toActionEvent(actx, time.Now())

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

// --- ErrorResult ---

func TestNewErrorResult(t *testing.T) {
	actx := testActionContext()
	r := NewErrorResult(actx, "something failed")

	if r.cause != "something failed" {
		t.Errorf("cause = %q", r.cause)
	}
	if r.errorContext != "" {
		t.Errorf("errorContext = %q, want empty", r.errorContext)
	}
	if r.annotations == nil {
		t.Error("annotations should be initialized")
	}
}

func TestErrorResult_SetContext_Chaining(t *testing.T) {
	actx := testActionContext()
	r := NewErrorResult(actx, "err").SetContext("detail")

	if r.cause != "err" {
		t.Errorf("cause = %q", r.cause)
	}
	if r.errorContext != "detail" {
		t.Errorf("errorContext = %q", r.errorContext)
	}
}

func TestErrorResult_AddAnnotation(t *testing.T) {
	actx := testActionContext()
	r := NewErrorResult(actx, "err")
	r.AddAnnotation("k", "v")

	if r.annotations["k"] != "v" {
		t.Errorf("annotation k = %q", r.annotations["k"])
	}
}

func TestErrorResult_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewErrorResult(actx, "bad thing").SetContext("stack trace")
	r.AddAnnotation("retry", "false")
	r.LogError("something went wrong")

	event := r.toActionEvent(actx, time.Now())

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

// --- FilterResult ---

func TestNewFilterResult(t *testing.T) {
	actx := testActionContext()
	r := NewFilterResult(actx, "not relevant")

	if r.message != "not relevant" {
		t.Errorf("message = %q", r.message)
	}
	if r.annotations == nil {
		t.Error("annotations should be initialized")
	}
}

func TestFilterResult_AddAnnotation(t *testing.T) {
	actx := testActionContext()
	r := NewFilterResult(actx, "filtered")
	r.AddAnnotation("reason", "test")

	if r.annotations["reason"] != "test" {
		t.Errorf("annotation reason = %q", r.annotations["reason"])
	}
}

func TestFilterResult_ToActionEvent(t *testing.T) {
	actx := testActionContext()
	r := NewFilterResult(actx, "no match")
	r.AddAnnotation("category", "spam")

	event := r.toActionEvent(actx, time.Now())

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

// --- Logging on results ---

func TestBaseResult_LogMethods(t *testing.T) {
	actx := testActionContext()
	r := NewTransformResult(actx)

	r.LogInfo("info msg")
	r.LogWarning("warn msg")
	r.LogError("error msg")

	if len(r.messages) != 3 {
		t.Fatalf("messages count = %d, want 3", len(r.messages))
	}

	if r.messages[0].Severity != SeverityInfo {
		t.Errorf("[0] severity = %q", r.messages[0].Severity)
	}
	if r.messages[0].Message != "info msg" {
		t.Errorf("[0] message = %q", r.messages[0].Message)
	}
	if r.messages[0].Source != "TestAction" {
		t.Errorf("[0] source = %q", r.messages[0].Source)
	}

	if r.messages[1].Severity != SeverityWarning {
		t.Errorf("[1] severity = %q", r.messages[1].Severity)
	}
	if r.messages[2].Severity != SeverityError {
		t.Errorf("[2] severity = %q", r.messages[2].Severity)
	}
}

func TestBaseResult_LogOnAllResultTypes(t *testing.T) {
	actx := testActionContext()

	// Each result type has its own LogInfo returning *T for chaining, so we
	// call the method directly rather than through a shared interface.
	NewTransformResult(actx).LogInfo("test message")
	NewTransformResults(actx).LogInfo("test message")
	NewChildTransformResult(actx, "child.txt").LogInfo("test message")
	NewEgressResult(actx).LogInfo("test message")
	NewIngressResult(actx).LogInfo("test message")
	NewErrorResult(actx, "err").LogInfo("test message")
	NewFilterResult(actx, "msg").LogInfo("test message")
}

func TestTransformResult_AddSelfHelpers(t *testing.T) {
	actx := &ActionContext{ActionName: "MyAction", DID: "d"}
	r := NewTransformResult(actx)

	r.AddSelfAnnotation("processed")
	r.AddSelfMetadata("done")

	if r.annotations["MyAction"] != "processed" {
		t.Errorf("annotations[%q] = %q, want %q", "MyAction", r.annotations["MyAction"], "processed")
	}
	if r.metadata["MyAction"] != "done" {
		t.Errorf("metadata[%q] = %q, want %q", "MyAction", r.metadata["MyAction"], "done")
	}
	if r.ActionName() != "MyAction" {
		t.Errorf("ActionName() = %q", r.ActionName())
	}
}

func TestChildTransformResult_AddSelfHelpers(t *testing.T) {
	parent := &ActionContext{ActionName: "ChildAction", DID: "d"}
	r := NewChildTransformResult(parent, "child.txt")

	r.AddSelfAnnotation("ok")
	r.AddSelfMetadata("done")

	if r.annotations["ChildAction"] != "ok" {
		t.Errorf("annotations[%q] = %q", "ChildAction", r.annotations["ChildAction"])
	}
	if r.metadata["ChildAction"] != "done" {
		t.Errorf("metadata[%q] = %q", "ChildAction", r.metadata["ChildAction"])
	}
}

func TestErrorResult_AddSelfAnnotation(t *testing.T) {
	actx := &ActionContext{ActionName: "ErrAction", DID: "d"}
	r := NewErrorResult(actx, "boom")
	r.AddSelfAnnotation("flagged")
	if r.annotations["ErrAction"] != "flagged" {
		t.Errorf("annotations[%q] = %q", "ErrAction", r.annotations["ErrAction"])
	}
}

func TestFilterResult_AddSelfAnnotation(t *testing.T) {
	actx := &ActionContext{ActionName: "FltAction", DID: "d"}
	r := NewFilterResult(actx, "skipped")
	r.AddSelfAnnotation("noted")
	if r.annotations["FltAction"] != "noted" {
		t.Errorf("annotations[%q] = %q", "FltAction", r.annotations["FltAction"])
	}
}

func TestTransformResult_FluentChaining(t *testing.T) {
	actx := testActionContext()
	actx.ActionName = "ChainAction"

	// Single-expression result construction.
	r := NewTransformResult(actx).
		AddMetadata("k1", "v1").
		AddMetadata("k2", "v2").
		AddAnnotation("ann", "val").
		DeleteMetadataKey("old").
		LogInfo("fluent chaining works").
		LogWarning("also %s formatting", "supports").
		LogError("plain message with 100%% no interpolation")

	if r.metadata["k1"] != "v1" || r.metadata["k2"] != "v2" {
		t.Errorf("metadata chain broke: %v", r.metadata)
	}
	if r.annotations["ann"] != "val" {
		t.Errorf("annotation chain broke: %v", r.annotations)
	}
	if len(r.deleteMetadataKeys) != 1 || r.deleteMetadataKeys[0] != "old" {
		t.Errorf("delete metadata keys: %v", r.deleteMetadataKeys)
	}
	if len(r.messages) != 3 {
		t.Fatalf("expected 3 log messages, got %d", len(r.messages))
	}
	if r.messages[0].Message != "fluent chaining works" {
		t.Errorf("plain log: %q", r.messages[0].Message)
	}
	if r.messages[1].Message != "also supports formatting" {
		t.Errorf("formatted log: %q", r.messages[1].Message)
	}
	// When called with no args the format string is used verbatim; %% stays literal.
	if r.messages[2].Message != "plain message with 100%% no interpolation" {
		t.Errorf("no-args log: %q", r.messages[2].Message)
	}
}

func TestLogMethods_VariadicFormatting(t *testing.T) {
	actx := testActionContext()

	cases := []struct {
		name    string
		call    func() []LogMessage
		wantMsg string
	}{
		{
			name: "TransformResult formatted",
			call: func() []LogMessage {
				return NewTransformResult(actx).LogInfo("n=%d", 42).messages
			},
			wantMsg: "n=42",
		},
		{
			name: "EgressResult plain",
			call: func() []LogMessage {
				return NewEgressResult(actx).LogInfo("no args here").messages
			},
			wantMsg: "no args here",
		},
		{
			name: "IngressResult multiple args",
			call: func() []LogMessage {
				return NewIngressResult(actx).LogWarning("%s = %d", "count", 7).messages
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
