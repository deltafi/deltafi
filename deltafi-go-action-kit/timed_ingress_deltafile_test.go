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
// ABOUTME: Tests for TimedIngressDeltafile and IngressDeltafile content/metadata/error wrappers.
// ABOUTME: Verifies item content saves, metadata/annotations, and error signaling reach the event.

package actionkit

import (
	"strings"
	"testing"
	"time"
)

func TestTimedIngress_Context(t *testing.T) {
	ti := &TimedIngressDeltafile{}
	if ti.Context() == nil {
		t.Error("Context should default to a non-nil context")
	}
}

func TestTimedIngress_ItemContentAndMetadata(t *testing.T) {
	tc := NewTestContext("did-1")
	ti := tc.NewTimedIngressDeltafile()

	item := ti.NewItem("file-1")
	c, err := item.SaveStringContent("f.txt", "text/plain", "hello")
	if err != nil {
		t.Fatalf("SaveStringContent: %v", err)
	}
	if got, _ := c.LoadString(); got != "hello" {
		t.Errorf("content = %q, want hello", got)
	}
	item.AddMetadata("k", "v").
		AddMetadataMap(map[string]string{"k2": "v2"}).
		AddAnnotation("a", "b")

	ev := timedIngressToEvent(ti, tc.ActionContext, time.Now())
	if ev.Type != "INGRESS" {
		t.Fatalf("Type = %q, want INGRESS", ev.Type)
	}
	if len(ev.Ingress.IngressItems) != 1 {
		t.Fatalf("items = %d, want 1", len(ev.Ingress.IngressItems))
	}
	got := ev.Ingress.IngressItems[0]
	if len(got.Content) != 1 {
		t.Errorf("content count = %d, want 1", len(got.Content))
	}
	if got.Metadata["k"] != "v" || got.Metadata["k2"] != "v2" {
		t.Errorf("metadata = %v", got.Metadata)
	}
	if got.Annotations["a"] != "b" {
		t.Errorf("annotations = %v", got.Annotations)
	}
}

func TestTimedIngress_ItemSaveVariants(t *testing.T) {
	tc := NewTestContext("did-1")
	item := tc.NewTimedIngressDeltafile().NewItem("file-1")

	if _, err := item.SaveBytesContent("b.txt", "text/plain", []byte("bytes")); err != nil {
		t.Fatalf("SaveBytesContent: %v", err)
	}
	if _, err := item.SaveReaderContent("r.txt", "text/plain", strings.NewReader("reader")); err != nil {
		t.Fatalf("SaveReaderContent: %v", err)
	}

	existing := tc.Content("e.txt", "text/plain", "existing")
	item.AddContent(existing).AddContentList([]ActionContent{existing})

	if got := item.df.GetNewContentList(); len(got) != 4 {
		t.Errorf("new content count = %d, want 4 (bytes, reader, add, addList)", len(got))
	}
}

func TestTimedIngress_Error(t *testing.T) {
	tc := NewTestContext("did-1")
	ti := tc.NewTimedIngressDeltafile()
	ti.Errorf("failed: %d", 42).SetErrorContext("extra context")

	ev := timedIngressToEvent(ti, tc.ActionContext, time.Now())
	if ev.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", ev.Type)
	}
	if ev.Error.Cause != "failed: 42" {
		t.Errorf("Cause = %q, want failed: 42", ev.Error.Cause)
	}
	if ev.Error.Context != "extra context" {
		t.Errorf("Context = %q, want extra context", ev.Error.Context)
	}
}
