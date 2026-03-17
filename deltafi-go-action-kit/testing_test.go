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
// ABOUTME: Tests for the test framework helpers.
// ABOUTME: Verifies InMemoryContentStore, MockLookupClient, MockArtifactClient, and ResultEvent.

package actionkit

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"strings"
	"testing"
)

func TestInMemoryContentStore_SaveAndLoad(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	data := []byte("hello world")
	seg, err := store.Save(ctx, "did-1", data)
	if err != nil {
		t.Fatalf("Save error: %v", err)
	}
	if seg.Size != int64(len(data)) {
		t.Errorf("Size = %d, want %d", seg.Size, len(data))
	}
	if seg.DID != "did-1" {
		t.Errorf("DID = %q, want %q", seg.DID, "did-1")
	}

	loaded, err := store.Load(ctx, []Segment{seg})
	if err != nil {
		t.Fatalf("Load error: %v", err)
	}
	if string(loaded) != "hello world" {
		t.Errorf("Load = %q, want %q", string(loaded), "hello world")
	}
}

func TestInMemoryContentStore_SaveFromReader(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	reader := strings.NewReader("streamed data")
	seg, err := store.SaveFromReader(ctx, "did-1", reader, 13)
	if err != nil {
		t.Fatalf("SaveFromReader error: %v", err)
	}
	if seg.Size != 13 {
		t.Errorf("Size = %d, want 13", seg.Size)
	}

	loaded, err := store.Load(ctx, []Segment{seg})
	if err != nil {
		t.Fatalf("Load error: %v", err)
	}
	if string(loaded) != "streamed data" {
		t.Errorf("Load = %q, want %q", string(loaded), "streamed data")
	}
}

func TestInMemoryContentStore_SaveFromReader_UnknownSize(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	reader := strings.NewReader("auto size")
	seg, err := store.SaveFromReader(ctx, "did-1", reader, -1)
	if err != nil {
		t.Fatalf("SaveFromReader error: %v", err)
	}
	if seg.Size != 9 {
		t.Errorf("Size = %d, want 9", seg.Size)
	}
}

func TestInMemoryContentStore_LoadReader(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	seg, _ := store.Save(ctx, "did-1", []byte("reader test"))
	rc, err := store.LoadReader(ctx, []Segment{seg})
	if err != nil {
		t.Fatalf("LoadReader error: %v", err)
	}
	defer rc.Close()

	data, _ := io.ReadAll(rc)
	if string(data) != "reader test" {
		t.Errorf("LoadReader = %q, want %q", string(data), "reader test")
	}
}

func TestInMemoryContentStore_LoadMultipleSegments(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	seg1, _ := store.Save(ctx, "did-1", []byte("hello "))
	seg2, _ := store.Save(ctx, "did-1", []byte("world"))

	loaded, err := store.Load(ctx, []Segment{seg1, seg2})
	if err != nil {
		t.Fatalf("Load error: %v", err)
	}
	if string(loaded) != "hello world" {
		t.Errorf("Load = %q, want %q", string(loaded), "hello world")
	}
}

func TestInMemoryContentStore_LoadMissingSegment(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	_, err := store.Load(ctx, []Segment{{UUID: "nonexistent", Size: 5}})
	if err == nil {
		t.Error("expected error for missing segment")
	}
}

func TestInMemoryContentStore_Delete(t *testing.T) {
	store := NewInMemoryContentStore()
	ctx := context.Background()

	seg, _ := store.Save(ctx, "did-1", []byte("to delete"))
	if err := store.Delete(ctx, []Segment{seg}); err != nil {
		t.Fatalf("Delete error: %v", err)
	}

	_, err := store.Load(ctx, []Segment{seg})
	if err == nil {
		t.Error("expected error loading deleted segment")
	}
}

func TestInMemoryContentStore_CreateContent(t *testing.T) {
	store := NewInMemoryContentStore()
	content := store.CreateContent("did-1", "test.txt", "text/plain", []byte("hello"))

	if content.Name != "test.txt" {
		t.Errorf("Name = %q, want %q", content.Name, "test.txt")
	}
	if content.MediaType != "text/plain" {
		t.Errorf("MediaType = %q, want %q", content.MediaType, "text/plain")
	}

	loaded, err := content.LoadString(context.Background(), store)
	if err != nil {
		t.Fatalf("LoadString error: %v", err)
	}
	if loaded != "hello" {
		t.Errorf("LoadString = %q, want %q", loaded, "hello")
	}
}

func TestInMemoryContentStore_CreateStringContent(t *testing.T) {
	store := NewInMemoryContentStore()
	content := store.CreateStringContent("did-1", "test.txt", "text/plain", "world")

	loaded, err := content.LoadString(context.Background(), store)
	if err != nil {
		t.Fatalf("LoadString error: %v", err)
	}
	if loaded != "world" {
		t.Errorf("LoadString = %q, want %q", loaded, "world")
	}
}

func TestMockLookupClient_Lookup(t *testing.T) {
	mock := &MockLookupClient{
		Results: map[string]*LookupResults{
			"colors": {
				TotalCount: 2,
				Rows: []map[string]string{
					{"name": "red"},
					{"name": "blue"},
				},
			},
		},
	}

	result, err := mock.Lookup(context.Background(), "colors", nil)
	if err != nil {
		t.Fatalf("Lookup error: %v", err)
	}
	if result.TotalCount != 2 {
		t.Errorf("TotalCount = %d, want 2", result.TotalCount)
	}
	if len(mock.LookupCalls) != 1 {
		t.Errorf("LookupCalls = %d, want 1", len(mock.LookupCalls))
	}
	if mock.LookupCalls[0].TableName != "colors" {
		t.Errorf("TableName = %q, want %q", mock.LookupCalls[0].TableName, "colors")
	}
}

func TestMockLookupClient_LookupMissingTable(t *testing.T) {
	mock := &MockLookupClient{}

	result, err := mock.Lookup(context.Background(), "missing", nil)
	if err != nil {
		t.Fatalf("Lookup error: %v", err)
	}
	if result.TotalCount != 0 {
		t.Errorf("TotalCount = %d, want 0", result.TotalCount)
	}
}

func TestMockLookupClient_LookupError(t *testing.T) {
	mock := &MockLookupClient{Err: fmt.Errorf("lookup failed")}

	_, err := mock.Lookup(context.Background(), "any", nil)
	if err == nil {
		t.Error("expected error")
	}
}

func TestMockArtifactClient_GetArtifact(t *testing.T) {
	mock := &MockArtifactClient{
		Artifacts: map[string]*FetchedArtifact{
			"plugin/config.json": {
				Data:     []byte(`{"key":"value"}`),
				Manifest: ArtifactManifest{ContentType: "application/json"},
			},
		},
	}

	a, err := mock.GetArtifact(context.Background(), "config.json", "")
	if err != nil {
		t.Fatalf("GetArtifact error: %v", err)
	}
	if string(a.Data) != `{"key":"value"}` {
		t.Errorf("Data = %q", string(a.Data))
	}
}

func TestMockArtifactClient_GetArtifactNotFound(t *testing.T) {
	mock := &MockArtifactClient{}

	_, err := mock.GetArtifact(context.Background(), "missing", "")
	if err == nil {
		t.Error("expected error for missing artifact")
	}
}

func TestMockArtifactClient_GetSharedArtifact(t *testing.T) {
	mock := &MockArtifactClient{
		Artifacts: map[string]*FetchedArtifact{
			"shared/global.txt": {Data: []byte("shared data")},
		},
	}

	a, err := mock.GetSharedArtifact(context.Background(), "global.txt", "")
	if err != nil {
		t.Fatalf("GetSharedArtifact error: %v", err)
	}
	if string(a.Data) != "shared data" {
		t.Errorf("Data = %q", string(a.Data))
	}
}

func TestMockArtifactClient_PublishArtifact(t *testing.T) {
	mock := &MockArtifactClient{}

	_, err := mock.PublishArtifact(context.Background(), "test.txt", bytes.NewReader([]byte("content")), "text/plain", nil)
	if err != nil {
		t.Fatalf("PublishArtifact error: %v", err)
	}
	if len(mock.PublishCalls) != 1 {
		t.Fatalf("PublishCalls = %d, want 1", len(mock.PublishCalls))
	}
	if string(mock.PublishCalls[0].Data) != "content" {
		t.Errorf("Data = %q, want %q", string(mock.PublishCalls[0].Data), "content")
	}
	if mock.PublishCalls[0].Shared {
		t.Error("should not be shared")
	}
}

func TestMockArtifactClient_PublishSharedArtifact(t *testing.T) {
	mock := &MockArtifactClient{}

	_, err := mock.PublishSharedArtifact(context.Background(), "shared.txt", bytes.NewReader([]byte("data")), "text/plain", nil)
	if err != nil {
		t.Fatalf("error: %v", err)
	}
	if !mock.PublishCalls[0].Shared {
		t.Error("should be shared")
	}
}

func TestMockArtifactClient_StreamArtifact(t *testing.T) {
	mock := &MockArtifactClient{
		Artifacts: map[string]*FetchedArtifact{
			"plugin/data.bin": {Data: []byte("binary data")},
		},
	}

	stream, err := mock.StreamArtifact(context.Background(), "data.bin", "")
	if err != nil {
		t.Fatalf("StreamArtifact error: %v", err)
	}
	defer stream.Reader.Close()

	data, _ := io.ReadAll(stream.Reader)
	if string(data) != "binary data" {
		t.Errorf("Data = %q", string(data))
	}
}

func TestMockArtifactClient_Error(t *testing.T) {
	mock := &MockArtifactClient{Err: fmt.Errorf("failed")}

	_, err := mock.GetArtifact(context.Background(), "any", "")
	if err == nil {
		t.Error("expected error")
	}

	_, err = mock.PublishArtifact(context.Background(), "any", bytes.NewReader(nil), "", nil)
	if err == nil {
		t.Error("expected error")
	}
}

func TestNewTestContext(t *testing.T) {
	tc := NewTestContext("test-did-123")

	if tc.DID != "test-did-123" {
		t.Errorf("DID = %q, want %q", tc.DID, "test-did-123")
	}
	if tc.ContentService == nil {
		t.Error("ContentService should not be nil")
	}
	if tc.ContentService != tc.Store {
		t.Error("ContentService should be the returned store")
	}
	if tc.ActionName != "TestAction" {
		t.Errorf("ActionName = %q, want %q", tc.ActionName, "TestAction")
	}
}

func TestTestContext_Content(t *testing.T) {
	tc := NewTestContext("test-did")
	content := tc.Content("file.txt", "text/plain", "hello")

	if content.Name != "file.txt" {
		t.Errorf("Name = %q, want %q", content.Name, "file.txt")
	}
	loaded, err := content.LoadString(context.Background(), tc.Store)
	if err != nil {
		t.Fatalf("LoadString error: %v", err)
	}
	if loaded != "hello" {
		t.Errorf("LoadString = %q, want %q", loaded, "hello")
	}
}

func TestResultEvent_Transform(t *testing.T) {
	tc := NewTestContext("test-did")
	result := NewTransformResult(tc.ActionContext)
	result.AddContent(ActionContent{Name: "out.txt", MediaType: "text/plain"})
	result.AddMetadata("key", "value")
	result.AddAnnotation("ann", "val")
	result.LogInfo("test message")

	event := ResultEvent(result, tc.ActionContext)
	if event.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want TRANSFORM", event.Type)
	}
	if len(event.Transform) != 1 {
		t.Fatalf("Transform count = %d, want 1", len(event.Transform))
	}
	te := event.Transform[0]
	if len(te.Content) != 1 || te.Content[0].Name != "out.txt" {
		t.Error("content mismatch")
	}
	if te.Metadata["key"] != "value" {
		t.Error("metadata mismatch")
	}
	if te.Annotations["ann"] != "val" {
		t.Error("annotation mismatch")
	}
	if len(event.Messages) != 1 || event.Messages[0].Message != "test message" {
		t.Error("messages mismatch")
	}
}

func TestResultEvent_Error(t *testing.T) {
	tc := NewTestContext("test-did")
	result := NewErrorResult(tc.ActionContext, "something broke").SetContext("extra context")

	event := ResultEvent(result, tc.ActionContext)
	if event.Type != "ERROR" {
		t.Errorf("Type = %q, want ERROR", event.Type)
	}
	if event.Error.Cause != "something broke" {
		t.Errorf("Cause = %q", event.Error.Cause)
	}
	if event.Error.Context != "extra context" {
		t.Errorf("Context = %q", event.Error.Context)
	}
}

func TestResultEvent_Filter(t *testing.T) {
	tc := NewTestContext("test-did")
	result := NewFilterResult(tc.ActionContext, "filtered out")

	event := ResultEvent(result, tc.ActionContext)
	if event.Type != "FILTER" {
		t.Errorf("Type = %q, want FILTER", event.Type)
	}
	if event.Filter.Message != "filtered out" {
		t.Errorf("Message = %q", event.Filter.Message)
	}
}

func TestResultEvent_Egress(t *testing.T) {
	tc := NewTestContext("test-did")
	result := NewEgressResult(tc.ActionContext)
	result.LogInfo("egressed")

	event := ResultEvent(result, tc.ActionContext)
	if event.Type != "EGRESS" {
		t.Errorf("Type = %q, want EGRESS", event.Type)
	}
	if len(event.Messages) != 1 {
		t.Errorf("Messages = %d, want 1", len(event.Messages))
	}
}

func TestResultEvent_Ingress(t *testing.T) {
	tc := NewTestContext("test-did")
	result := NewIngressResult(tc.ActionContext)
	item := NewIngressResultItem("ingressed-file")
	item.AddContent(ActionContent{Name: "data.txt"})
	item.AddMetadata("key", "val")
	result.AddItem(*item)
	result.SetMemo("42")

	event := ResultEvent(result, tc.ActionContext)
	if event.Type != "INGRESS" {
		t.Errorf("Type = %q, want INGRESS", event.Type)
	}
	if event.Ingress.Memo != "42" {
		t.Errorf("Memo = %q", event.Ingress.Memo)
	}
	if len(event.Ingress.IngressItems) != 1 {
		t.Fatalf("Items = %d, want 1", len(event.Ingress.IngressItems))
	}
	if event.Ingress.IngressItems[0].DeltaFileName != "ingressed-file" {
		t.Errorf("DeltaFileName = %q", event.Ingress.IngressItems[0].DeltaFileName)
	}
}

func TestResultEvent_TransformResults(t *testing.T) {
	tc := NewTestContext("test-did")
	result := NewTransformResults(tc.ActionContext)

	child := NewChildTransformResult(tc.ActionContext, "child-1")
	child.AddContent(ActionContent{Name: "child-content"})
	child.AddMetadata("ck", "cv")
	result.AddChild(*child)

	event := ResultEvent(result, tc.ActionContext)
	if event.Type != "TRANSFORM" {
		t.Errorf("Type = %q, want TRANSFORM", event.Type)
	}
	if len(event.Transform) != 1 {
		t.Fatalf("Transform count = %d, want 1", len(event.Transform))
	}
	if event.Transform[0].Name != "child-1" {
		t.Errorf("Name = %q, want child-1", event.Transform[0].Name)
	}
}
