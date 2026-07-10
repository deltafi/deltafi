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
// ABOUTME: Tests for EgressDeltafile field population and method delegation.
// ABOUTME: Verifies Errorf/Filterf/AddAnnotation/Log* propagate to the underlying Deltafile.

package actionkit

import "testing"

func TestEgressDeltafile_FieldsCopied(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	df.DataSource = "src"
	df.FlowName = "flow"
	df.FlowID = "fid"
	df.ActionName = "act"
	df.Memo = "memo"
	df.Name = "name"

	e := newEgressDeltafile(df)

	cases := map[string]string{
		"Did":        e.Did,
		"Name":       e.Name,
		"DataSource": e.DataSource,
		"FlowName":   e.FlowName,
		"FlowID":     e.FlowID,
		"ActionName": e.ActionName,
		"Memo":       e.Memo,
	}
	want := map[string]string{
		"Did":        df.Did,
		"Name":       "name",
		"DataSource": "src",
		"FlowName":   "flow",
		"FlowID":     "fid",
		"ActionName": "act",
		"Memo":       "memo",
	}
	for k, got := range cases {
		if got != want[k] {
			t.Errorf("%s = %q, want %q", k, got, want[k])
		}
	}
}

func TestEgressDeltafile_ContentReadsDelegate(t *testing.T) {
	tc := NewTestContext("test-did")
	c1 := tc.Content("a.txt", "text/plain", "alpha")
	c2 := tc.Content("b.txt", "text/plain", "beta")
	df := tc.NewDeltafile(c1, c2)
	e := newEgressDeltafile(df)

	if !e.HasContent() {
		t.Fatal("HasContent = false")
	}
	if e.ContentCount() != 2 {
		t.Errorf("ContentCount = %d, want 2", e.ContentCount())
	}
	first, err := e.FirstContent()
	if err != nil || first.Name != "a.txt" {
		t.Errorf("FirstContent = %+v, %v", first, err)
	}
	at, err := e.ContentAt(1)
	if err != nil || at.Name != "b.txt" {
		t.Errorf("ContentAt(1) = %+v, %v", at, err)
	}
	named, err := e.ContentNamed("b.txt")
	if err != nil || named.Name != "b.txt" {
		t.Errorf("ContentNamed = %+v, %v", named, err)
	}
	if got := e.GetContentList(); len(got) != 2 {
		t.Errorf("GetContentList length = %d, want 2", len(got))
	}
}

func TestEgressDeltafile_MetadataReadsDelegate(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	df.metadata["k1"] = "v1"
	e := newEgressDeltafile(df)

	got, err := e.GetMetadata("k1")
	if err != nil || got != "v1" {
		t.Errorf("GetMetadata = %q, %v", got, err)
	}
	if got := e.GetMetadataOrDefault("missing", "def"); got != "def" {
		t.Errorf("GetMetadataOrDefault = %q, want \"def\"", got)
	}
	if m := e.GetMetadataMap(); m["k1"] != "v1" {
		t.Errorf("GetMetadataMap = %v", m)
	}
}

func TestEgressDeltafile_ErrorfPropagates(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	e := newEgressDeltafile(df)

	ret := e.Errorf("boom %d", 42).SetErrorContext("ctx")
	if ret != e {
		t.Error("Errorf did not return receiver")
	}
	if !df.hasError {
		t.Error("underlying Deltafile not marked errored")
	}
	if df.errCause != "boom 42" {
		t.Errorf("errCause = %q", df.errCause)
	}
	if df.errContext != "ctx" {
		t.Errorf("errContext = %q", df.errContext)
	}
}

func TestEgressDeltafile_FilterfPropagates(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	e := newEgressDeltafile(df)

	e.Filterf("dropped")
	if !df.hasFilter {
		t.Error("underlying Deltafile not marked filtered")
	}
	if df.filterMessage != "dropped" {
		t.Errorf("filterMessage = %q", df.filterMessage)
	}
}

func TestEgressDeltafile_AddAnnotationPropagates(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	e := newEgressDeltafile(df)

	e.AddAnnotation("status", "401").AddAnnotation("target", "url")
	if df.annotations["status"] != "401" || df.annotations["target"] != "url" {
		t.Errorf("annotations = %v", df.annotations)
	}
}

func TestEgressDeltafile_LogsPropagate(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	e := newEgressDeltafile(df)

	e.LogInfo("i").LogWarn("w").LogError("e")
	if len(df.messages) != 3 {
		t.Fatalf("messages = %d, want 3", len(df.messages))
	}
	if df.messages[0].Severity != SeverityInfo || df.messages[1].Severity != SeverityWarning || df.messages[2].Severity != SeverityError {
		t.Errorf("severities = %v", df.messages)
	}
}

// TestEgressDeltafile_ErrorEventCarriesAnnotations confirms that an egress
// action can annotate an Errorf result — a legitimate use of AddAnnotation
// on the EgressDeltafile.
func TestEgressDeltafile_ErrorEventCarriesAnnotations(t *testing.T) {
	tc := NewTestContext("test-did")
	df := tc.NewDeltafile()
	event := RunEgress(t, &egressAnnotatingAction{}, df, noParams{})

	if event.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", event.Type)
	}
	if event.Error == nil || event.Error.Annotations["status"] != "401" {
		t.Errorf("Error.Annotations = %v", event.Error)
	}
}

type egressAnnotatingAction struct{}

func (*egressAnnotatingAction) Egress(df *EgressDeltafile, _ noParams) *EgressDeltafile {
	return df.AddAnnotation("status", "401").Errorf("auth failure")
}
