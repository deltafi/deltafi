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
// ABOUTME: Tests for the package-level Lookup/Artifact facades over globalState.
// ABOUTME: Verifies delegation, exclusion application, and not-initialized errors.

package actionkit

import (
	"context"
	"errors"
	"io"
	"testing"
)

type fakeLookupClient struct {
	results  *LookupResults
	err      error
	gotTable string
	gotOpts  int
}

func (f *fakeLookupClient) Lookup(ctx context.Context, tableName string, opts ...LookupOption) (*LookupResults, error) {
	f.gotTable = tableName
	f.gotOpts = len(opts)
	return f.results, f.err
}

type fakeArtifactClient struct {
	fetched *FetchedArtifact
	stream  *ArtifactStream
	pub     *PublishResponse
	err     error
	calls   []string
}

func (f *fakeArtifactClient) GetArtifact(ctx context.Context, name, alias string) (*FetchedArtifact, error) {
	f.calls = append(f.calls, "GetArtifact:"+name)
	return f.fetched, f.err
}

func (f *fakeArtifactClient) GetSharedArtifact(ctx context.Context, name, alias string) (*FetchedArtifact, error) {
	f.calls = append(f.calls, "GetSharedArtifact:"+name)
	return f.fetched, f.err
}

func (f *fakeArtifactClient) PublishArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	f.calls = append(f.calls, "PublishArtifact:"+name)
	return f.pub, f.err
}

func (f *fakeArtifactClient) PublishSharedArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	f.calls = append(f.calls, "PublishSharedArtifact:"+name)
	return f.pub, f.err
}

func (f *fakeArtifactClient) StreamArtifact(ctx context.Context, name, alias string) (*ArtifactStream, error) {
	f.calls = append(f.calls, "StreamArtifact:"+name)
	return f.stream, f.err
}

func (f *fakeArtifactClient) StreamSharedArtifact(ctx context.Context, name, alias string) (*ArtifactStream, error) {
	f.calls = append(f.calls, "StreamSharedArtifact:"+name)
	return f.stream, f.err
}

// setGlobalState installs the given clients and restores the previous state on cleanup.
func setGlobalState(t *testing.T, lc LookupClientBase, ac ArtifactClientBase) {
	t.Helper()
	prev := globalState
	t.Cleanup(func() { globalState = prev })
	globalState.lookupClient = lc
	globalState.artifactClient = ac
	globalState.ctx = context.Background()
}

func TestGlobalLookup_NotInitialized(t *testing.T) {
	setGlobalState(t, nil, nil)
	if _, err := Lookup("t", nil); err == nil {
		t.Error("expected error when lookup client is not initialized")
	}
}

func TestGlobalLookup_DelegatesAndAppliesExclusions(t *testing.T) {
	fake := &fakeLookupClient{results: &LookupResults{
		TotalCount: 2,
		Rows: []Row{
			NewRow(map[string]string{"id": "1", "status": "active"}),
			NewRow(map[string]string{"id": "2", "status": "deleted"}),
		},
	}}
	setGlobalState(t, fake, nil)

	q := LookupQuery().WithMatch("id", "1", "2").ExcludingMatch("status", "deleted")
	res, err := Lookup("my_table", q)
	if err != nil {
		t.Fatalf("Lookup: %v", err)
	}
	if fake.gotTable != "my_table" {
		t.Errorf("delegated table = %q, want my_table", fake.gotTable)
	}
	if fake.gotOpts != 1 {
		t.Errorf("delegated option count = %d, want 1 (match only)", fake.gotOpts)
	}
	id, _ := res.Rows[0].Get("id")
	if res.TotalCount != 1 || len(res.Rows) != 1 || id != "1" {
		t.Errorf("exclusions not applied: %+v", res)
	}
}

func TestGlobalLookup_PropagatesError(t *testing.T) {
	fake := &fakeLookupClient{err: errors.New("boom")}
	setGlobalState(t, fake, nil)
	if _, err := Lookup("t", nil); err == nil {
		t.Error("expected propagated error from lookup client")
	}
}

func TestGlobalArtifacts_NotInitialized(t *testing.T) {
	setGlobalState(t, nil, nil)
	if _, err := GetArtifact("a", ""); err == nil {
		t.Error("GetArtifact: expected not-initialized error")
	}
	if _, err := PublishArtifact("a", nil, "text/plain", nil); err == nil {
		t.Error("PublishArtifact: expected not-initialized error")
	}
	if _, err := StreamArtifact("a", ""); err == nil {
		t.Error("StreamArtifact: expected not-initialized error")
	}
}

func TestGlobalArtifacts_Delegate(t *testing.T) {
	fake := &fakeArtifactClient{
		fetched: &FetchedArtifact{Data: []byte("x")},
		stream:  &ArtifactStream{},
		pub:     &PublishResponse{ArtifactName: "a"},
	}
	setGlobalState(t, nil, fake)

	if _, err := GetArtifact("a", ""); err != nil {
		t.Fatalf("GetArtifact: %v", err)
	}
	if _, err := GetSharedArtifact("a", ""); err != nil {
		t.Fatalf("GetSharedArtifact: %v", err)
	}
	if _, err := PublishArtifact("a", nil, "text/plain", nil); err != nil {
		t.Fatalf("PublishArtifact: %v", err)
	}
	if _, err := PublishSharedArtifact("a", nil, "text/plain", nil); err != nil {
		t.Fatalf("PublishSharedArtifact: %v", err)
	}
	if _, err := StreamArtifact("a", ""); err != nil {
		t.Fatalf("StreamArtifact: %v", err)
	}
	if _, err := StreamSharedArtifact("a", ""); err != nil {
		t.Fatalf("StreamSharedArtifact: %v", err)
	}

	want := []string{
		"GetArtifact:a", "GetSharedArtifact:a",
		"PublishArtifact:a", "PublishSharedArtifact:a",
		"StreamArtifact:a", "StreamSharedArtifact:a",
	}
	if len(fake.calls) != len(want) {
		t.Fatalf("calls = %v, want %v", fake.calls, want)
	}
	for i, c := range want {
		if fake.calls[i] != c {
			t.Errorf("calls[%d] = %q, want %q", i, fake.calls[i], c)
		}
	}
}

func TestInitGlobalState(t *testing.T) {
	prev := globalState
	t.Cleanup(func() { globalState = prev })

	lc := &fakeLookupClient{}
	ac := &fakeArtifactClient{}
	initGlobalState(context.Background(), lc, ac)

	if globalState.lookupClient != lc || globalState.artifactClient != ac || globalState.ctx == nil {
		t.Errorf("initGlobalState did not wire global state: %+v", globalState)
	}
}

func TestGlobalSharedArtifacts_NotInitialized(t *testing.T) {
	setGlobalState(t, nil, nil)
	if _, err := GetSharedArtifact("a", ""); err == nil {
		t.Error("GetSharedArtifact: expected not-initialized error")
	}
	if _, err := PublishSharedArtifact("a", nil, "text/plain", nil); err == nil {
		t.Error("PublishSharedArtifact: expected not-initialized error")
	}
	if _, err := StreamSharedArtifact("a", ""); err == nil {
		t.Error("StreamSharedArtifact: expected not-initialized error")
	}
}

func TestGlobalContext_DefaultsToBackground(t *testing.T) {
	prev := globalState
	t.Cleanup(func() { globalState = prev })
	globalState.ctx = nil
	if globalContext() == nil {
		t.Error("globalContext should default to a non-nil context")
	}
}
