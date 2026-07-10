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
// ABOUTME: Tests for pure Deltafile helper methods not exercised elsewhere.
// ABOUTME: Covers Context, PassthroughContent, ClearMetadata, GetChildren, SaveReaderContent.

package actionkit

import (
	"errors"
	"strings"
	"testing"
	"time"
)

func TestDeltafile_Context_DefaultsToBackground(t *testing.T) {
	df := &Deltafile{}
	if df.Context() == nil {
		t.Error("Context should default to a non-nil context")
	}
}

func TestEgressDeltafile_Context(t *testing.T) {
	tc := NewTestContext("did-1")
	e := newEgressDeltafile(tc.NewDeltafile())
	if e.Context() == nil {
		t.Error("EgressDeltafile.Context should return a non-nil context")
	}
}

func TestDeltafile_PassthroughContent(t *testing.T) {
	tc := NewTestContext("did-1")
	in := tc.Content("a.txt", "text/plain", "hello")
	df := tc.NewDeltafile(in)

	df.PassthroughContent()

	out := df.GetNewContentList()
	if len(out) != 1 || out[0].Name != "a.txt" {
		t.Errorf("passthrough output = %v, want the single input content", out)
	}
}

func TestDeltafile_ClearMetadata(t *testing.T) {
	tc := NewTestContext("did-1")
	df := newDeltafile(tc.ActionContext, nil, map[string]string{"k": "v"})

	if m := df.GetMetadataMap(); m["k"] != "v" {
		t.Fatalf("precondition: metadata not present: %v", m)
	}

	df.ClearMetadata()

	if m := df.GetMetadataMap(); len(m) != 0 {
		t.Errorf("metadata after ClearMetadata = %v, want empty", m)
	}
}

func TestDeltafile_GetChildren(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()

	df.AddChild("child-1")
	df.AddChild("child-2")

	if got := df.GetChildren(); len(got) != 2 {
		t.Errorf("GetChildren len = %d, want 2", len(got))
	}
}

func TestDeltafile_SaveReaderContent(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()

	content, err := df.SaveReaderContent("f.txt", "text/plain", strings.NewReader("streamed"))
	if err != nil {
		t.Fatalf("SaveReaderContent: %v", err)
	}
	if got, _ := content.LoadString(); got != "streamed" {
		t.Errorf("round-trip = %q, want streamed", got)
	}
	if len(df.GetNewContentList()) != 1 {
		t.Errorf("output content count = %d, want 1", len(df.GetNewContentList()))
	}
}

func TestFail_SetsErrorStateFromError(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()
	df.Fail(errors.New("disk gone"))
	event := deltafileToErrorEvent(df, tc.ActionContext, time.Now())
	if event.Error == nil || event.Error.Cause != "disk gone" {
		t.Fatalf("expected error cause 'disk gone', got %+v", event.Error)
	}
}

func TestFailf_FormatsErrorCause(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()
	df.Failf("bad index %d", 7)
	event := deltafileToErrorEvent(df, tc.ActionContext, time.Now())
	if event.Error == nil || event.Error.Cause != "bad index 7" {
		t.Fatalf("got %+v", event.Error)
	}
}

func TestEgressDeltafile_Fail_SetsErrorCause(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()
	e := newEgressDeltafile(df)
	e.Fail(errors.New("egress gone"))
	event := deltafileToEgressEvent(df, tc.ActionContext, time.Now())
	if event.Error == nil || event.Error.Cause != "egress gone" {
		t.Fatalf("expected error cause 'egress gone', got %+v", event.Error)
	}
}

func TestEgressDeltafile_Failf_FormatsErrorCause(t *testing.T) {
	tc := NewTestContext("did-1")
	df := tc.NewDeltafile()
	e := newEgressDeltafile(df)
	e.Failf("egress error %d", 5)
	event := deltafileToEgressEvent(df, tc.ActionContext, time.Now())
	if event.Error == nil || event.Error.Cause != "egress error 5" {
		t.Fatalf("expected error cause 'egress error 5', got %+v", event.Error)
	}
}

func TestTimedIngressDeltafile_Fail_SetsErrorCause(t *testing.T) {
	tc := NewTestContext("did-1")
	ti := tc.NewTimedIngressDeltafile()
	ti.Fail(errors.New("ingress gone"))
	ev := timedIngressToEvent(ti, tc.ActionContext, time.Now())
	if ev.Error == nil || ev.Error.Cause != "ingress gone" {
		t.Fatalf("expected error cause 'ingress gone', got %+v", ev.Error)
	}
}

func TestTimedIngressDeltafile_Failf_FormatsErrorCause(t *testing.T) {
	tc := NewTestContext("did-1")
	ti := tc.NewTimedIngressDeltafile()
	ti.Failf("ingress error %d", 3)
	ev := timedIngressToEvent(ti, tc.ActionContext, time.Now())
	if ev.Error == nil || ev.Error.Cause != "ingress error 3" {
		t.Fatalf("expected error cause 'ingress error 3', got %+v", ev.Error)
	}
}
