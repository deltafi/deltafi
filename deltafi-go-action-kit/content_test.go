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
// ABOUTME: Tests for content types (no MinIO required).
// ABOUTME: Verifies Segment.objectPath, ActionContent.Size, ActionContent.AddTag, and JSON serialization.

package actionkit

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"testing"
)

func TestSegmentObjectPath(t *testing.T) {
	tests := []struct {
		did  string
		uuid string
		want string
	}{
		{"abcdef-1234", "seg-uuid-1", "abc/abcdef-1234/seg-uuid-1"},
		{"123456-7890", "uuid-2", "123/123456-7890/uuid-2"},
		{"xyz", "u1", "xyz/xyz/u1"}, // short DID (3 chars exactly)
		{"ab", "u2", "ab/ab/u2"},    // DID shorter than 3 chars
		{"", "u3", "//u3"},          // empty DID
	}

	for _, tt := range tests {
		seg := Segment{UUID: tt.uuid, DID: tt.did}
		got := seg.objectPath()
		if got != tt.want {
			t.Errorf("objectPath(%q, %q) = %q, want %q", tt.did, tt.uuid, got, tt.want)
		}
	}
}

func TestActionContentSize(t *testing.T) {
	ac := ActionContent{
		Segments: []Segment{
			{Size: 100},
			{Size: 200},
			{Size: 50},
		},
	}
	if got := ac.Size(); got != 350 {
		t.Errorf("Size() = %d, want 350", got)
	}
}

func TestActionContentSize_Empty(t *testing.T) {
	ac := ActionContent{}
	if got := ac.Size(); got != 0 {
		t.Errorf("Size() = %d, want 0", got)
	}
}

func TestActionContentAddTag(t *testing.T) {
	ac := &ActionContent{Name: "test"}

	ac.AddTag("tag1")
	ac.AddTag("tag2")

	if len(ac.Tags) != 2 {
		t.Fatalf("expected 2 tags, got %d", len(ac.Tags))
	}
	if ac.Tags[0] != "tag1" {
		t.Errorf("Tags[0] = %q, want %q", ac.Tags[0], "tag1")
	}
	if ac.Tags[1] != "tag2" {
		t.Errorf("Tags[1] = %q, want %q", ac.Tags[1], "tag2")
	}
}

// --- Copy ---

func TestActionContent_Copy(t *testing.T) {
	original := ActionContent{
		Name:      "file.txt",
		MediaType: "text/plain",
		Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 100, DID: "d1"}},
		Tags:      []string{"tag1", "tag2"},
	}

	copied := original.Copy()

	if copied.Name != original.Name {
		t.Errorf("Name = %q", copied.Name)
	}
	if copied.MediaType != original.MediaType {
		t.Errorf("MediaType = %q", copied.MediaType)
	}
	if len(copied.Segments) != 1 || copied.Segments[0].UUID != "u1" {
		t.Errorf("Segments = %v", copied.Segments)
	}
	if len(copied.Tags) != 2 || copied.Tags[0] != "tag1" {
		t.Errorf("Tags = %v", copied.Tags)
	}

	// Mutating the copy should not affect the original
	copied.Segments[0].UUID = "modified"
	copied.Tags[0] = "modified"
	if original.Segments[0].UUID != "u1" {
		t.Error("original segments mutated by copy")
	}
	if original.Tags[0] != "tag1" {
		t.Error("original tags mutated by copy")
	}
}

func TestActionContent_Copy_NilTags(t *testing.T) {
	original := ActionContent{Name: "file.txt", Segments: []Segment{}}
	copied := original.Copy()
	if copied.Tags != nil {
		t.Errorf("Tags should be nil, got %v", copied.Tags)
	}
}

func TestActionContent_Copy_PreservesActx(t *testing.T) {
	tc := NewTestContext("test-did")
	content := tc.Content("file.txt", "text/plain", "hello world")

	copied := content.Copy()

	// The copy should be able to load content via the preserved actx
	data, err := copied.LoadString()
	if err != nil {
		t.Fatalf("LoadString on copy failed: %v", err)
	}
	if data != "hello world" {
		t.Errorf("LoadString = %q, want %q", data, "hello world")
	}
}

func TestActionContent_Prepend_DoesNotMutateOther(t *testing.T) {
	// Give "other" extra backing-array capacity so a naive append could
	// silently overwrite memory beyond its length.
	otherSegs := make([]Segment, 1, 4)
	otherSegs[0] = Segment{UUID: "u-other", Offset: 0, Size: 200, DID: "d1"}
	other := ActionContent{Segments: otherSegs}

	ac := &ActionContent{
		Segments: []Segment{{UUID: "u-self", Offset: 0, Size: 100, DID: "d1"}},
	}

	ac.Prepend(other)

	// ac should have [u-other, u-self]
	if len(ac.Segments) != 2 || ac.Segments[0].UUID != "u-other" || ac.Segments[1].UUID != "u-self" {
		t.Errorf("ac.Segments = %v, want [u-other, u-self]", ac.Segments)
	}

	// other must still have exactly its original segment
	if len(other.Segments) != 1 || other.Segments[0].UUID != "u-other" {
		t.Errorf("other.Segments was mutated: %v", other.Segments)
	}
}

// --- Subcontent ---

func TestActionContent_Subcontent_SingleSegment(t *testing.T) {
	// One segment of 100 bytes, take bytes 20-69 (50 bytes)
	ac := ActionContent{
		Name:      "data.bin",
		MediaType: "application/octet-stream",
		Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 100, DID: "d1"}},
	}

	sub := ac.Subcontent(20, 50)

	if sub.Name != "data.bin" {
		t.Errorf("Name = %q", sub.Name)
	}
	if sub.MediaType != "application/octet-stream" {
		t.Errorf("MediaType = %q", sub.MediaType)
	}
	if len(sub.Segments) != 1 {
		t.Fatalf("expected 1 segment, got %d", len(sub.Segments))
	}

	seg := sub.Segments[0]
	if seg.UUID != "u1" {
		t.Errorf("UUID = %q", seg.UUID)
	}
	if seg.Offset != 20 {
		t.Errorf("Offset = %d, want 20", seg.Offset)
	}
	if seg.Size != 50 {
		t.Errorf("Size = %d, want 50", seg.Size)
	}
}

func TestActionContent_Subcontent_SpanMultipleSegments(t *testing.T) {
	// Three segments: [0-99], [100-199], [200-299]
	// Take bytes 50-249 (200 bytes), spanning all three
	ac := ActionContent{
		Segments: []Segment{
			{UUID: "u1", Offset: 0, Size: 100, DID: "d1"},
			{UUID: "u2", Offset: 0, Size: 100, DID: "d1"},
			{UUID: "u3", Offset: 0, Size: 100, DID: "d1"},
		},
	}

	sub := ac.Subcontent(50, 200)

	if len(sub.Segments) != 3 {
		t.Fatalf("expected 3 segments, got %d", len(sub.Segments))
	}

	// First segment: skip 50 bytes, take remaining 50
	if sub.Segments[0].UUID != "u1" || sub.Segments[0].Offset != 50 || sub.Segments[0].Size != 50 {
		t.Errorf("seg[0] = %+v, want {u1, offset:50, size:50}", sub.Segments[0])
	}
	// Second segment: take all 100
	if sub.Segments[1].UUID != "u2" || sub.Segments[1].Offset != 0 || sub.Segments[1].Size != 100 {
		t.Errorf("seg[1] = %+v, want {u2, offset:0, size:100}", sub.Segments[1])
	}
	// Third segment: take only 50 of 100
	if sub.Segments[2].UUID != "u3" || sub.Segments[2].Offset != 0 || sub.Segments[2].Size != 50 {
		t.Errorf("seg[2] = %+v, want {u3, offset:0, size:50}", sub.Segments[2])
	}

	if sub.Size() != 200 {
		t.Errorf("total size = %d, want 200", sub.Size())
	}
}

func TestActionContent_Subcontent_SkipFirstSegment(t *testing.T) {
	// Two segments: [0-99], [100-199]
	// Take bytes 100-149 (entirely in second segment)
	ac := ActionContent{
		Segments: []Segment{
			{UUID: "u1", Offset: 0, Size: 100, DID: "d1"},
			{UUID: "u2", Offset: 0, Size: 100, DID: "d1"},
		},
	}

	sub := ac.Subcontent(100, 50)

	if len(sub.Segments) != 1 {
		t.Fatalf("expected 1 segment, got %d", len(sub.Segments))
	}
	if sub.Segments[0].UUID != "u2" || sub.Segments[0].Offset != 0 || sub.Segments[0].Size != 50 {
		t.Errorf("seg = %+v, want {u2, offset:0, size:50}", sub.Segments[0])
	}
}

func TestActionContent_Subcontent_WithExistingOffset(t *testing.T) {
	// Segment already has offset 10, size 100 (bytes 10-109 in storage)
	// Subcontent at logical offset 20, size 30
	// Should produce: storage offset 30, size 30
	ac := ActionContent{
		Segments: []Segment{{UUID: "u1", Offset: 10, Size: 100, DID: "d1"}},
	}

	sub := ac.Subcontent(20, 30)

	if len(sub.Segments) != 1 {
		t.Fatalf("expected 1 segment, got %d", len(sub.Segments))
	}
	if sub.Segments[0].Offset != 30 || sub.Segments[0].Size != 30 {
		t.Errorf("seg = offset:%d size:%d, want offset:30 size:30", sub.Segments[0].Offset, sub.Segments[0].Size)
	}
}

func TestActionContent_Subcontent_EntireContent(t *testing.T) {
	ac := ActionContent{
		Segments: []Segment{
			{UUID: "u1", Offset: 0, Size: 50, DID: "d1"},
			{UUID: "u2", Offset: 0, Size: 50, DID: "d1"},
		},
	}

	sub := ac.Subcontent(0, 100)

	if len(sub.Segments) != 2 {
		t.Fatalf("expected 2 segments, got %d", len(sub.Segments))
	}
	if sub.Size() != 100 {
		t.Errorf("size = %d, want 100", sub.Size())
	}
}

func TestActionContent_Subcontent_ZeroSize(t *testing.T) {
	ac := ActionContent{
		Segments: []Segment{{UUID: "u1", Offset: 0, Size: 100, DID: "d1"}},
	}

	sub := ac.Subcontent(50, 0)

	if len(sub.Segments) != 0 {
		t.Errorf("expected 0 segments for zero-size subcontent, got %d", len(sub.Segments))
	}
}

func TestActionContent_SubcontentNamed(t *testing.T) {
	ac := ActionContent{
		Name:      "original.txt",
		MediaType: "text/plain",
		Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 100, DID: "d1"}},
	}

	sub := ac.SubcontentNamed(0, 50, "subset.bin", "application/octet-stream")

	if sub.Name != "subset.bin" {
		t.Errorf("Name = %q", sub.Name)
	}
	if sub.MediaType != "application/octet-stream" {
		t.Errorf("MediaType = %q", sub.MediaType)
	}
}

// --- Append / Prepend ---

func TestActionContent_Append(t *testing.T) {
	ac := ActionContent{
		Segments: []Segment{{UUID: "u1", Offset: 0, Size: 100, DID: "d1"}},
	}
	other := ActionContent{
		Segments: []Segment{{UUID: "u2", Offset: 0, Size: 200, DID: "d1"}},
	}

	ac.Append(other)

	if len(ac.Segments) != 2 {
		t.Fatalf("expected 2 segments, got %d", len(ac.Segments))
	}
	if ac.Segments[0].UUID != "u1" || ac.Segments[1].UUID != "u2" {
		t.Errorf("segments = %v", ac.Segments)
	}
	if ac.Size() != 300 {
		t.Errorf("size = %d, want 300", ac.Size())
	}
}

func TestActionContent_Prepend(t *testing.T) {
	ac := ActionContent{
		Segments: []Segment{{UUID: "u1", Offset: 0, Size: 100, DID: "d1"}},
	}
	other := ActionContent{
		Segments: []Segment{{UUID: "u2", Offset: 0, Size: 200, DID: "d1"}},
	}

	ac.Prepend(other)

	if len(ac.Segments) != 2 {
		t.Fatalf("expected 2 segments, got %d", len(ac.Segments))
	}
	if ac.Segments[0].UUID != "u2" || ac.Segments[1].UUID != "u1" {
		t.Errorf("segments = %v (expected u2 first)", ac.Segments)
	}
	if ac.Size() != 300 {
		t.Errorf("size = %d, want 300", ac.Size())
	}
}

// --- SetName / SetMediaType ---

func TestActionContent_SetName(t *testing.T) {
	ac := ActionContent{Name: "old.txt"}
	ac.SetName("new.txt")
	if ac.Name != "new.txt" {
		t.Errorf("Name = %q", ac.Name)
	}
}

func TestActionContent_SetMediaType(t *testing.T) {
	ac := ActionContent{MediaType: "text/plain"}
	ac.SetMediaType("application/json")
	if ac.MediaType != "application/json" {
		t.Errorf("MediaType = %q", ac.MediaType)
	}
}

// --- RemoveTag / HasTag ---

func TestActionContent_HasTag(t *testing.T) {
	ac := ActionContent{Tags: []string{"alpha", "beta"}}

	if !ac.HasTag("alpha") {
		t.Error("HasTag(alpha) = false")
	}
	if !ac.HasTag("beta") {
		t.Error("HasTag(beta) = false")
	}
	if ac.HasTag("gamma") {
		t.Error("HasTag(gamma) = true")
	}
}

func TestActionContent_HasTag_Empty(t *testing.T) {
	ac := ActionContent{}
	if ac.HasTag("any") {
		t.Error("HasTag should return false for nil tags")
	}
}

func TestActionContent_RemoveTag(t *testing.T) {
	ac := ActionContent{Tags: []string{"alpha", "beta", "gamma"}}

	if !ac.RemoveTag("beta") {
		t.Error("RemoveTag(beta) returned false")
	}
	if len(ac.Tags) != 2 {
		t.Fatalf("expected 2 tags, got %d", len(ac.Tags))
	}
	if ac.HasTag("beta") {
		t.Error("beta should be removed")
	}
	if !ac.HasTag("alpha") || !ac.HasTag("gamma") {
		t.Error("alpha and gamma should remain")
	}
}

func TestActionContent_RemoveTag_NotFound(t *testing.T) {
	ac := ActionContent{Tags: []string{"alpha"}}

	if ac.RemoveTag("missing") {
		t.Error("RemoveTag(missing) returned true")
	}
	if len(ac.Tags) != 1 {
		t.Errorf("tags should be unchanged, got %v", ac.Tags)
	}
}

func TestSegmentJSON(t *testing.T) {
	seg := Segment{
		UUID:   "my-uuid",
		Offset: 10,
		Size:   500,
		DID:    "my-did",
	}

	data, err := json.Marshal(seg)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if parsed["uuid"] != "my-uuid" {
		t.Errorf("uuid = %v", parsed["uuid"])
	}
	if parsed["offset"] != float64(10) {
		t.Errorf("offset = %v", parsed["offset"])
	}
	if parsed["size"] != float64(500) {
		t.Errorf("size = %v", parsed["size"])
	}
	if parsed["did"] != "my-did" {
		t.Errorf("did = %v", parsed["did"])
	}
}

func TestActionContentJSON(t *testing.T) {
	ac := ActionContent{
		Name:      "file.txt",
		MediaType: "text/plain",
		Segments: []Segment{
			{UUID: "u1", Offset: 0, Size: 100, DID: "d1"},
		},
		Tags: []string{"hello"},
	}

	data, err := json.Marshal(ac)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if parsed["name"] != "file.txt" {
		t.Errorf("name = %v", parsed["name"])
	}
	if parsed["mediaType"] != "text/plain" {
		t.Errorf("mediaType = %v", parsed["mediaType"])
	}

	segments := parsed["segments"].([]any)
	if len(segments) != 1 {
		t.Fatalf("expected 1 segment, got %d", len(segments))
	}

	tags := parsed["tags"].([]any)
	if len(tags) != 1 || tags[0] != "hello" {
		t.Errorf("tags = %v", tags)
	}
}

// --- multiCloser ---

func TestMultiCloser_ReadsFromMultipleReaders(t *testing.T) {
	r1 := strings.NewReader("hello ")
	r2 := strings.NewReader("world")

	mc := &multiCloser{
		Reader:  io.MultiReader(r1, r2),
		closers: nil,
	}

	data, err := io.ReadAll(mc)
	if err != nil {
		t.Fatalf("ReadAll: %v", err)
	}
	if string(data) != "hello world" {
		t.Errorf("got %q, want %q", string(data), "hello world")
	}
}

func TestMultiCloser_ClosesAllUnderlyingClosers(t *testing.T) {
	var closed []string
	c1 := &trackCloser{name: "c1", closed: &closed}
	c2 := &trackCloser{name: "c2", closed: &closed}

	mc := &multiCloser{
		Reader:  strings.NewReader("data"),
		closers: []io.Closer{c1, c2},
	}

	if err := mc.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}
	if len(closed) != 2 || closed[0] != "c1" || closed[1] != "c2" {
		t.Errorf("closed = %v, want [c1, c2]", closed)
	}
}

func TestMultiCloser_ReturnsFirstCloseError(t *testing.T) {
	var closed []string
	c1 := &trackCloser{name: "c1", closed: &closed, err: fmt.Errorf("c1 error")}
	c2 := &trackCloser{name: "c2", closed: &closed}
	c3 := &trackCloser{name: "c3", closed: &closed, err: fmt.Errorf("c3 error")}

	mc := &multiCloser{
		Reader:  strings.NewReader(""),
		closers: []io.Closer{c1, c2, c3},
	}

	err := mc.Close()
	if err == nil || err.Error() != "c1 error" {
		t.Errorf("Close error = %v, want 'c1 error'", err)
	}
	// All closers should still be called even after an error
	if len(closed) != 3 {
		t.Errorf("expected all 3 closers called, got %v", closed)
	}
}

func TestMultiCloser_EmptyClosers(t *testing.T) {
	mc := &multiCloser{
		Reader:  strings.NewReader("data"),
		closers: nil,
	}

	data, err := io.ReadAll(mc)
	if err != nil {
		t.Fatalf("ReadAll: %v", err)
	}
	if string(data) != "data" {
		t.Errorf("got %q", string(data))
	}
	if err := mc.Close(); err != nil {
		t.Errorf("Close: %v", err)
	}
}

// trackCloser records when Close is called for testing.
type trackCloser struct {
	name   string
	closed *[]string
	err    error
}

func (tc *trackCloser) Close() error {
	*tc.closed = append(*tc.closed, tc.name)
	return tc.err
}

func TestActionContentJSON_OmitsEmptyTags(t *testing.T) {
	ac := ActionContent{
		Name:      "file.txt",
		MediaType: "text/plain",
		Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 10, DID: "d1"}},
	}

	data, err := json.Marshal(ac)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if _, ok := parsed["tags"]; ok {
		t.Error("tags should be omitted when nil")
	}
}
