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
// ABOUTME: Verifies InMemoryContentStore, MockLookupClient, MockArtifactClient, TestContext, and RunTransform/RunEgress/RunIngress.

package actionkit

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"strings"
	"testing"
	"time"
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

	// Content created directly via the store has no ActionContext back-reference;
	// load through the store directly.
	loaded, err := store.Load(context.Background(), content.Segments)
	if err != nil {
		t.Fatalf("store.Load error: %v", err)
	}
	if string(loaded) != "hello" {
		t.Errorf("Load = %q, want %q", loaded, "hello")
	}

	// Unbound content's LoadString must report a clear error.
	if _, err := content.LoadString(); err == nil {
		t.Error("expected error from unbound content.LoadString")
	}
}

func TestInMemoryContentStore_CreateStringContent(t *testing.T) {
	store := NewInMemoryContentStore()
	content := store.CreateStringContent("did-1", "test.txt", "text/plain", "world")

	loaded, err := store.Load(context.Background(), content.Segments)
	if err != nil {
		t.Fatalf("store.Load error: %v", err)
	}
	if string(loaded) != "world" {
		t.Errorf("Load = %q, want %q", loaded, "world")
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

	result, err := mock.Lookup(context.Background(), "colors")
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

	result, err := mock.Lookup(context.Background(), "missing")
	if err != nil {
		t.Fatalf("Lookup error: %v", err)
	}
	if result.TotalCount != 0 {
		t.Errorf("TotalCount = %d, want 0", result.TotalCount)
	}
}

func TestMockLookupClient_LookupError(t *testing.T) {
	mock := &MockLookupClient{Err: fmt.Errorf("lookup failed")}

	_, err := mock.Lookup(context.Background(), "any")
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
	// Content from tc.Content is bound to the TestContext's ActionContext,
	// so LoadString works without explicit plumbing.
	loaded, err := content.LoadString()
	if err != nil {
		t.Fatalf("LoadString error: %v", err)
	}
	if loaded != "hello" {
		t.Errorf("LoadString = %q, want %q", loaded, "hello")
	}
}

func TestTestContext_NewDeltafile(t *testing.T) {
	tc := NewTestContext("did")
	c1 := tc.Content("a.txt", "text/plain", "alpha")
	c2 := tc.Content("b.txt", "text/plain", "beta")

	df := tc.NewDeltafile(c1, c2)
	if df.Did != "did" {
		t.Errorf("Did = %q, want %q", df.Did, "did")
	}
	if !df.HasContent() {
		t.Error("expected content")
	}
	content := df.GetContentSlice()
	if len(content) != 2 {
		t.Errorf("Content len = %d, want 2", len(content))
	}
}

func TestTestContext_NewDeltafile_WithChild(t *testing.T) {
	tc := NewTestContext("did")
	c := tc.Content("in.txt", "text/plain", "data")

	df := tc.NewDeltafile(c)
	if !df.HasContent() {
		t.Error("expected content")
	}

	// NewChild should create a child with a different DID
	child := df.NewChild("child.txt")
	if child.Did == df.Did {
		t.Error("child DID should differ from parent")
	}
	if child.Name != "child.txt" {
		t.Errorf("child Name = %q", child.Name)
	}
}

func TestTestContext_NewIngressInput(t *testing.T) {
	tc := NewTestContext("did")
	input := tc.NewIngressInput()
	if input.FlowName != tc.ActionContext.FlowName {
		t.Errorf("FlowName = %q", input.FlowName)
	}
	if input.ActionName != tc.ActionContext.ActionName {
		t.Errorf("ActionName = %q", input.ActionName)
	}

	// NewDeltafile from IngressInput
	df := input.NewDeltafile("ingress-file.txt")
	if df.Name != "ingress-file.txt" {
		t.Errorf("Name = %q", df.Name)
	}
	if df.Did == "" {
		t.Error("DID should be auto-generated")
	}
}

// --- RunTransform / RunEgress / RunIngress helpers ---

type testRunTransformAction struct{}

func (a *testRunTransformAction) Transform(df *Deltafile, params noParams) *Deltafile {
	df.AddContent(ActionContent{Name: "out.txt", MediaType: "text/plain"})
	df.AddMetadata("key", "value")
	df.AddAnnotation("ann", "val")
	df.LogInfo("test message")
	return df
}

func TestRunTransform(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()

	event := RunTransform(t, &testRunTransformAction{}, df, noParams{})
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

type testRunErrorAction struct{}

func (a *testRunErrorAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df.Errorf("something broke").SetErrorContext("extra context")
}

func TestRunTransform_Error(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()

	event := RunTransform(t, &testRunErrorAction{}, df, noParams{})
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

type testRunFilterAction struct{}

func (a *testRunFilterAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df.Filterf("filtered out")
}

func TestRunTransform_Filter(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()

	event := RunTransform(t, &testRunFilterAction{}, df, noParams{})
	if event.Type != "FILTER" {
		t.Errorf("Type = %q, want FILTER", event.Type)
	}
	if event.Filter.Message != "filtered out" {
		t.Errorf("Message = %q", event.Filter.Message)
	}
}

type testRunEgressAction struct{}

func (a *testRunEgressAction) Egress(df *Deltafile, params noParams) *Deltafile {
	df.LogInfo("egressed")
	return df
}

func TestRunEgress(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()

	event := RunEgress(t, &testRunEgressAction{}, df, noParams{})
	if event.Type != "EGRESS" {
		t.Errorf("Type = %q, want EGRESS", event.Type)
	}
	if len(event.Messages) != 1 {
		t.Errorf("Messages = %d, want 1", len(event.Messages))
	}
}

type testRunIngressAction struct{}

func (a *testRunIngressAction) Ingress(input *IngressInput, params noParams) *IngressResult {
	r := NewIngressResult()
	item := input.NewDeltafile("ingressed-file")
	item.AddContent(ActionContent{Name: "data.txt"})
	item.AddMetadata("key", "val")
	r.AddItem(item)
	r.SetMemo("42")
	return r
}

func TestRunIngress(t *testing.T) {
	tc := NewTestContext("test-did")
	input := tc.NewIngressInput()

	event := RunIngress(t, &testRunIngressAction{}, input, noParams{})
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

type testRunSplitAction struct{}

func (a *testRunSplitAction) Transform(df *Deltafile, params noParams) *Deltafile {
	child := df.NewChild("child-1")
	child.AddContent(ActionContent{Name: "child-content"})
	child.AddMetadata("ck", "cv")
	return df
}

func TestRunSplit(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()

	event := RunTransform(t, &testRunSplitAction{}, df, noParams{})
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

func TestMockLookupClient_TablesFilter(t *testing.T) {
	now := time.Now()
	mock := &MockLookupClient{
		Tables: []LookupTable{
			{
				Name:     "colors",
				Created:  &now,
				Modified: &now,
				Rows: []LookupRow{
					{"name": "red", "hex": "#FF0000", "primary": "true"},
					{"name": "green", "hex": "#00FF00", "primary": "true"},
					{"name": "blue", "hex": "#0000FF", "primary": "true"},
					{"name": "purple", "hex": "#800080", "primary": "false"},
				},
			},
		},
	}

	// No filter -> all rows + Created/Modified populated.
	all, err := mock.Lookup(context.Background(), "colors")
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if all.TotalCount != 4 {
		t.Errorf("TotalCount = %d, want 4", all.TotalCount)
	}
	if all.Created == nil || all.Modified == nil {
		t.Errorf("Created/Modified should be non-nil when set on the table")
	}

	// Single-column match.
	red, err := mock.Lookup(context.Background(), "colors", WithMatch("name", "red"))
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if red.TotalCount != 1 || red.Rows[0]["hex"] != "#FF0000" {
		t.Errorf("red lookup: %+v", red.Rows)
	}

	// Multi-value match (any-of within a column).
	rgb, err := mock.Lookup(context.Background(), "colors", WithMatch("name", "red", "blue"))
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if rgb.TotalCount != 2 {
		t.Errorf("rgb count = %d, want 2", rgb.TotalCount)
	}

	// Multi-column match (AND across columns).
	primaryRed, err := mock.Lookup(context.Background(), "colors",
		WithMatch("name", "red"),
		WithMatch("primary", "true"),
	)
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if primaryRed.TotalCount != 1 {
		t.Errorf("primaryRed count = %d, want 1", primaryRed.TotalCount)
	}

	// No match across columns -> empty results.
	primaryPurple, err := mock.Lookup(context.Background(), "colors",
		WithMatch("name", "purple"),
		WithMatch("primary", "true"),
	)
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if primaryPurple.TotalCount != 0 {
		t.Errorf("primaryPurple count = %d, want 0", primaryPurple.TotalCount)
	}
}

func TestMockLookupClient_TablesProjection(t *testing.T) {
	mock := &MockLookupClient{
		Tables: []LookupTable{
			{
				Name: "users",
				Rows: []LookupRow{
					{"id": "1", "name": "Alice", "email": "alice@example.com", "secret": "abc"},
					{"id": "2", "name": "Bob", "email": "bob@example.com", "secret": "xyz"},
				},
			},
		},
	}

	r, err := mock.Lookup(context.Background(), "users",
		WithResultColumns("id", "name"),
	)
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if len(r.Rows) != 2 {
		t.Fatalf("Rows len = %d, want 2", len(r.Rows))
	}
	for _, row := range r.Rows {
		if _, ok := row["secret"]; ok {
			t.Errorf("projected row should not contain secret column: %v", row)
		}
		if _, ok := row["email"]; ok {
			t.Errorf("projected row should not contain email column: %v", row)
		}
		if row["id"] == "" || row["name"] == "" {
			t.Errorf("projected row missing required columns: %v", row)
		}
	}
}

func TestMockLookupClient_TablesSortAndPaginate(t *testing.T) {
	mock := &MockLookupClient{
		Tables: []LookupTable{
			{
				Name: "items",
				Rows: []LookupRow{
					{"name": "banana"},
					{"name": "apple"},
					{"name": "cherry"},
					{"name": "date"},
					{"name": "elderberry"},
				},
			},
		},
	}

	// Sort ascending, take 2.
	asc, err := mock.Lookup(context.Background(), "items",
		WithSort("name", SortASC),
		WithLimit(2),
	)
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if asc.TotalCount != 2 || asc.Rows[0]["name"] != "apple" || asc.Rows[1]["name"] != "banana" {
		t.Errorf("asc rows = %v", asc.Rows)
	}

	// Sort descending, offset 1, limit 2.
	desc, err := mock.Lookup(context.Background(), "items",
		WithSort("name", SortDESC),
		WithOffset(1),
		WithLimit(2),
	)
	if err != nil {
		t.Fatalf("Lookup err: %v", err)
	}
	if desc.TotalCount != 2 || desc.Rows[0]["name"] != "date" || desc.Rows[1]["name"] != "cherry" {
		t.Errorf("desc rows = %v", desc.Rows)
	}
}

func TestMockLookupClient_TablesFallback(t *testing.T) {
	// Tables and Results coexist; Tables wins for matching names, Results
	// fills in for unknown names.
	mock := &MockLookupClient{
		Tables: []LookupTable{
			{Name: "primary", Rows: []LookupRow{{"k": "v"}}},
		},
		Results: map[string]*LookupResults{
			"backup": {TotalCount: 99, Rows: []LookupRow{{"src": "results"}}},
		},
	}

	pri, _ := mock.Lookup(context.Background(), "primary")
	if pri.TotalCount != 1 || pri.Rows[0]["k"] != "v" {
		t.Errorf("primary fell through Tables: %+v", pri)
	}

	bak, _ := mock.Lookup(context.Background(), "backup")
	if bak.TotalCount != 99 {
		t.Errorf("backup should come from Results: %+v", bak)
	}

	unknown, _ := mock.Lookup(context.Background(), "missing")
	if unknown.TotalCount != 0 {
		t.Errorf("unknown table should return empty results: %+v", unknown)
	}
}
