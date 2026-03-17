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
// ABOUTME: Test helpers for unit testing DeltaFi actions without external services.
// ABOUTME: Provides in-memory content storage, mock clients, and result inspection utilities.

package actionkit

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
)

// --- InMemoryContentStore ---

// InMemoryContentStore implements ContentServiceBase with an in-memory map.
// Safe for concurrent use.
type InMemoryContentStore struct {
	mu   sync.RWMutex
	data map[string][]byte // keyed by segment UUID
}

// NewInMemoryContentStore creates an empty in-memory content store.
func NewInMemoryContentStore() *InMemoryContentStore {
	return &InMemoryContentStore{
		data: make(map[string][]byte),
	}
}

func (m *InMemoryContentStore) Save(ctx context.Context, did string, data []byte) (Segment, error) {
	segUUID := uuid.New().String()
	seg := Segment{
		UUID:   segUUID,
		Offset: 0,
		Size:   int64(len(data)),
		DID:    did,
	}
	stored := make([]byte, len(data))
	copy(stored, data)

	m.mu.Lock()
	m.data[segUUID] = stored
	m.mu.Unlock()

	return seg, nil
}

func (m *InMemoryContentStore) SaveFromReader(ctx context.Context, did string, reader io.Reader, size int64) (Segment, error) {
	data, err := io.ReadAll(reader)
	if err != nil {
		return Segment{}, fmt.Errorf("failed to read: %w", err)
	}
	seg, err := m.Save(ctx, did, data)
	if err != nil {
		return Segment{}, err
	}
	if size >= 0 {
		seg.Size = size
	} else {
		seg.Size = int64(len(data))
	}
	return seg, nil
}

func (m *InMemoryContentStore) Load(ctx context.Context, segments []Segment) ([]byte, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	var buf bytes.Buffer
	for _, seg := range segments {
		data, ok := m.data[seg.UUID]
		if !ok {
			return nil, fmt.Errorf("segment %s not found", seg.UUID)
		}
		end := min(seg.Offset+seg.Size, int64(len(data)))
		buf.Write(data[seg.Offset:end])
	}
	return buf.Bytes(), nil
}

func (m *InMemoryContentStore) LoadReader(ctx context.Context, segments []Segment) (io.ReadCloser, error) {
	data, err := m.Load(ctx, segments)
	if err != nil {
		return nil, err
	}
	return io.NopCloser(bytes.NewReader(data)), nil
}

func (m *InMemoryContentStore) Delete(ctx context.Context, segments []Segment) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, seg := range segments {
		delete(m.data, seg.UUID)
	}
	return nil
}

// CreateContent stores data and returns an ActionContent ready for use in test inputs.
func (m *InMemoryContentStore) CreateContent(did, name, mediaType string, data []byte) ActionContent {
	seg, _ := m.Save(context.Background(), did, data)
	return ActionContent{
		Name:      name,
		MediaType: mediaType,
		Segments:  []Segment{seg},
	}
}

// CreateStringContent stores a string and returns an ActionContent ready for use in test inputs.
func (m *InMemoryContentStore) CreateStringContent(did, name, mediaType, data string) ActionContent {
	return m.CreateContent(did, name, mediaType, []byte(data))
}

// --- MockLookupClient ---

// MockLookupClient implements LookupClientBase with configurable canned responses.
type MockLookupClient struct {
	// Results maps table names to lookup results. If a table name is not present,
	// an empty LookupResults is returned. Ignored when LookupFunc is set.
	Results map[string]*LookupResults

	// LookupFunc, when set, handles all Lookup calls. Takes precedence over Results.
	LookupFunc func(ctx context.Context, tableName string, opts *LookupOptions) (*LookupResults, error)

	// Err is returned for all lookups when non-nil, overriding Results and LookupFunc.
	Err error

	// LookupCalls records each call to Lookup for assertion.
	LookupCalls []MockLookupCall
}

// MockLookupCall records the arguments of a Lookup call.
type MockLookupCall struct {
	TableName string
	Opts      *LookupOptions
}

func (m *MockLookupClient) Lookup(ctx context.Context, tableName string, opts *LookupOptions) (*LookupResults, error) {
	m.LookupCalls = append(m.LookupCalls, MockLookupCall{TableName: tableName, Opts: opts})
	if m.Err != nil {
		return nil, m.Err
	}
	if m.LookupFunc != nil {
		return m.LookupFunc(ctx, tableName, opts)
	}
	if m.Results != nil {
		if r, ok := m.Results[tableName]; ok {
			return r, nil
		}
	}
	return &LookupResults{}, nil
}

// --- MockArtifactClient ---

// MockArtifactClient implements ArtifactClientBase with configurable responses.
type MockArtifactClient struct {
	// Artifacts maps "owner/name" to fetched artifacts for Get calls.
	// Plugin-scoped keys use "plugin/name", shared use "shared/name".
	Artifacts map[string]*FetchedArtifact

	// PublishResponse is returned by all Publish calls.
	PublishResponse *PublishResponse

	// Err is returned for all calls when non-nil.
	Err error

	// PublishCalls records each publish call for assertion.
	PublishCalls []MockPublishCall
}

// MockPublishCall records the arguments of a Publish call.
type MockPublishCall struct {
	Name        string
	ContentType string
	Data        []byte
	Opts        *PublishOptions
	Shared      bool
}

func (m *MockArtifactClient) PublishArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	return m.recordPublish(name, data, contentType, opts, false)
}

func (m *MockArtifactClient) PublishSharedArtifact(ctx context.Context, name string, data io.Reader, contentType string, opts *PublishOptions) (*PublishResponse, error) {
	return m.recordPublish(name, data, contentType, opts, true)
}

func (m *MockArtifactClient) recordPublish(name string, data io.Reader, contentType string, opts *PublishOptions, shared bool) (*PublishResponse, error) {
	if m.Err != nil {
		return nil, m.Err
	}
	body, _ := io.ReadAll(data)
	m.PublishCalls = append(m.PublishCalls, MockPublishCall{
		Name:        name,
		ContentType: contentType,
		Data:        body,
		Opts:        opts,
		Shared:      shared,
	})
	if m.PublishResponse != nil {
		return m.PublishResponse, nil
	}
	return &PublishResponse{}, nil
}

func (m *MockArtifactClient) GetArtifact(ctx context.Context, name string, alias string) (*FetchedArtifact, error) {
	return m.getArtifact("plugin", name)
}

func (m *MockArtifactClient) GetSharedArtifact(ctx context.Context, name string, alias string) (*FetchedArtifact, error) {
	return m.getArtifact("shared", name)
}

func (m *MockArtifactClient) getArtifact(owner, name string) (*FetchedArtifact, error) {
	if m.Err != nil {
		return nil, m.Err
	}
	if m.Artifacts != nil {
		key := owner + "/" + name
		if a, ok := m.Artifacts[key]; ok {
			return a, nil
		}
	}
	return nil, fmt.Errorf("artifact %s not found", name)
}

func (m *MockArtifactClient) StreamArtifact(ctx context.Context, name string, alias string) (*ArtifactStream, error) {
	a, err := m.GetArtifact(ctx, name, alias)
	if err != nil {
		return nil, err
	}
	return &ArtifactStream{
		Reader:   io.NopCloser(bytes.NewReader(a.Data)),
		Manifest: a.Manifest,
	}, nil
}

func (m *MockArtifactClient) StreamSharedArtifact(ctx context.Context, name string, alias string) (*ArtifactStream, error) {
	a, err := m.GetSharedArtifact(ctx, name, alias)
	if err != nil {
		return nil, err
	}
	return &ArtifactStream{
		Reader:   io.NopCloser(bytes.NewReader(a.Data)),
		Manifest: a.Manifest,
	}, nil
}

// --- Test Context ---

// TestContext wraps an ActionContext and InMemoryContentStore for concise test setup.
type TestContext struct {
	*ActionContext
	Store *InMemoryContentStore
}

// Content creates an ActionContent from a string, using the TestContext's DID.
func (tc *TestContext) Content(name, mediaType, data string) ActionContent {
	return tc.Store.CreateStringContent(tc.DID, name, mediaType, data)
}

// NewTestContext creates a TestContext wired to an InMemoryContentStore.
func NewTestContext(did string) *TestContext {
	store := NewInMemoryContentStore()
	return &TestContext{
		ActionContext: &ActionContext{
			DID:            did,
			DeltaFileName:  "test-file",
			DataSource:     "test-data-source",
			FlowName:       "test-flow",
			FlowID:         uuid.New().String(),
			ActionName:     "TestAction",
			ActionVersion:  "0.0.0",
			Hostname:       "test-host",
			SystemName:     "test-system",
			StartTime:      time.Now(),
			ContentService: store,
		},
		Store: store,
	}
}

// --- Result Inspection ---

// ResultEvent converts a Result to an ActionEvent for test assertions.
// The ActionEvent contains all result data in exported, inspectable fields.
func ResultEvent(result Result, actx *ActionContext) *ActionEvent {
	return result.toActionEvent(actx, actx.StartTime)
}

// --- Action Runners ---

// RunTransform executes a TransformAction and returns the ActionEvent.
// Fails the test if the action returns a Go error.
func RunTransform(t *testing.T, action TransformAction, input TransformInput) *ActionEvent {
	t.Helper()
	result, err := action.Transform(context.Background(), input)
	if err != nil {
		t.Fatalf("Transform returned error: %v", err)
	}
	return ResultEvent(result, input.ActionContext)
}

// RunEgress executes an EgressAction and returns the ActionEvent.
// Fails the test if the action returns a Go error.
func RunEgress(t *testing.T, action EgressAction, input EgressInput) *ActionEvent {
	t.Helper()
	result, err := action.Egress(context.Background(), input)
	if err != nil {
		t.Fatalf("Egress returned error: %v", err)
	}
	return ResultEvent(result, input.ActionContext)
}

// RunIngress executes a TimedIngressAction and returns the ActionEvent.
// Fails the test if the action returns a Go error.
func RunIngress(t *testing.T, action TimedIngressAction, input IngressInput) *ActionEvent {
	t.Helper()
	result, err := action.Ingress(context.Background(), input)
	if err != nil {
		t.Fatalf("Ingress returned error: %v", err)
	}
	return ResultEvent(result, input.ActionContext)
}

// --- Result Assertions ---

// RequireTransform asserts the event type is TRANSFORM and returns the transform events.
func (e *ActionEvent) RequireTransform(t *testing.T) []TransformEvent {
	t.Helper()
	if e.Type != "TRANSFORM" {
		t.Fatalf("Type = %q, want TRANSFORM", e.Type)
	}
	return e.Transform
}

// RequireError asserts the event type is ERROR with the given cause.
func (e *ActionEvent) RequireError(t *testing.T, cause string) *ErrorEvent {
	t.Helper()
	if e.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", e.Type)
	}
	if e.Error.Cause != cause {
		t.Fatalf("Cause = %q, want %q", e.Error.Cause, cause)
	}
	return e.Error
}

// RequireFilter asserts the event type is FILTER with the given message.
func (e *ActionEvent) RequireFilter(t *testing.T, message string) *FilterEvent {
	t.Helper()
	if e.Type != "FILTER" {
		t.Fatalf("Type = %q, want FILTER", e.Type)
	}
	if e.Filter.Message != message {
		t.Fatalf("Message = %q, want %q", e.Filter.Message, message)
	}
	return e.Filter
}

// RequireEgress asserts the event type is EGRESS.
func (e *ActionEvent) RequireEgress(t *testing.T) {
	t.Helper()
	if e.Type != "EGRESS" {
		t.Fatalf("Type = %q, want EGRESS", e.Type)
	}
}

// RequireIngress asserts the event type is INGRESS and returns the ingress event.
func (e *ActionEvent) RequireIngress(t *testing.T) *IngressEvent {
	t.Helper()
	if e.Type != "INGRESS" {
		t.Fatalf("Type = %q, want INGRESS", e.Type)
	}
	return e.Ingress
}
