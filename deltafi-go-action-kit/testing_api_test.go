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
// ABOUTME: Tests for the kit's public test API: Run* runners and Require* assertions.
// ABOUTME: Exercises the helpers plugin authors use so the harness itself is covered.

package actionkit

import "testing"

type contentTransform struct{}

func (contentTransform) Transform(df *Deltafile, p noParams) *Deltafile {
	_, _ = df.SaveBytesContent("f.txt", "text/plain", []byte("payload"))
	return df
}

func TestRunTransform_AndRequireContentString(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunTransform(t, contentTransform{}, tc.NewDeltafile(), noParams{})

	ev.RequireTransform(t)
	te := ev.RequireSingleTransform(t)
	if got := te.RequireContentString(t, 0); got != "payload" {
		t.Errorf("content = %q, want payload", got)
	}
}

func TestRunTransform_RequireErrorAndFilter(t *testing.T) {
	tc := NewTestContext("did-1")

	errEvent := RunTransform(t, dispatchTransform{}, tc.NewDeltafile(), dispatchParams{Name: "fail"})
	errEvent.RequireError(t, "boom")

	filterEvent := RunTransform(t, dispatchTransform{}, tc.NewDeltafile(), dispatchParams{Name: "filter"})
	filterEvent.RequireFilter(t, "nope")
}

func TestRunTransform_UsesReturnedDeltafile(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunTransform(t, dispatchReturnedTransform{}, tc.NewDeltafile(), noParams{})

	te := ev.RequireSingleTransform(t)
	if got := te.Metadata["returned"]; got != "yes" {
		t.Fatalf("returned metadata = %q, want yes", got)
	}
}

func TestRunTransform_NilReturnBecomesError(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunTransform(t, dispatchNilTransform{}, tc.NewDeltafile(), noParams{})

	ev.RequireError(t, "action returned nil Deltafile")
}

func TestRunEgress_RequireEgress(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunEgress(t, dispatchEgress{}, tc.NewDeltafile(), dispatchParams{Name: "ok"})
	ev.RequireEgress(t)
}

func TestRunEgress_NilReturnBecomesError(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunEgress(t, dispatchNilEgress{}, tc.NewDeltafile(), noParams{})

	ev.RequireError(t, "action returned nil Deltafile")
}

func TestRunIngress_RequireIngress(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunIngress(t, dispatchIngress{}, tc.NewTimedIngressDeltafile(), noParams{})

	ing := ev.RequireIngress(t)
	if ing.Memo != "next-memo" {
		t.Errorf("Memo = %q, want next-memo", ing.Memo)
	}
}

func TestRunIngress_NilReturnBecomesError(t *testing.T) {
	tc := NewTestContext("did-1")
	ev := RunIngress(t, dispatchNilIngress{}, tc.NewTimedIngressDeltafile(), noParams{})

	ev.RequireError(t, "action returned nil TimedIngressDeltafile")
}

func TestRunJoin(t *testing.T) {
	tc := NewTestContext("did-1")
	inputs := []*Deltafile{tc.NewDeltafile()}
	joined := RunJoin(t, dispatchJoin{}, inputs, tc.NewDeltafile(), noParams{})

	if joined == nil {
		t.Fatal("RunJoin returned nil")
	}
	if v, _ := joined.GetMetadata("joined"); v != "yes" {
		t.Errorf("joined metadata = %q, want yes", v)
	}
}

func TestRunJoin_ReturnsActionResult(t *testing.T) {
	tc := NewTestContext("did-1")
	inputs := []*Deltafile{tc.NewDeltafile()}

	if joined := RunJoin(t, dispatchNilJoin{}, inputs, tc.NewDeltafile(), noParams{}); joined != nil {
		t.Fatalf("RunJoin returned %v, want nil from action", joined)
	}
}

func TestTestContext_SetClients(t *testing.T) {
	tc := NewTestContext("did-1")
	lc := &fakeLookupClient{}
	ac := &fakeArtifactClient{}

	tc.SetLookupClient(t, lc)
	tc.SetArtifactClient(t, ac)

	if tc.LookupClient != lc {
		t.Error("SetLookupClient did not wire the client")
	}
	if tc.ArtifactClient != ac {
		t.Error("SetArtifactClient did not wire the client")
	}
}
