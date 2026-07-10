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
	"slices"
	"sort"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
)

// --- InMemoryContentStore ---

// InMemoryContentStore implements ContentServiceBase with an in-memory map.
// Safe for concurrent use.
type InMemoryContentStore struct {
	mu         sync.RWMutex
	data       map[string][]byte // keyed by segment UUID
	saveCount  int               // incremented on each Save call
	failAfterN int               // if >= 0, Save returns an error once saveCount exceeds this value; -1 means disabled
}

// NewInMemoryContentStore creates an empty in-memory content store.
func NewInMemoryContentStore() *InMemoryContentStore {
	return &InMemoryContentStore{
		data:       make(map[string][]byte),
		failAfterN: -1,
	}
}

// FailAfterNthSave makes Save and SaveFromReader return an error on calls after the first n.
// Test-only hook for batch error aggregation tests.
func (m *InMemoryContentStore) FailAfterNthSave(n int) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.failAfterN = n
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
	m.saveCount++
	if m.failAfterN >= 0 && m.saveCount > m.failAfterN {
		m.mu.Unlock()
		return Segment{}, fmt.Errorf("save %d exceeded limit of %d (set by FailAfterNthSave)", m.saveCount, m.failAfterN)
	}
	m.data[segUUID] = stored
	m.mu.Unlock()

	return seg, nil
}

func (m *InMemoryContentStore) SaveFromReader(ctx context.Context, did string, reader io.Reader) (Segment, error) {
	data, err := io.ReadAll(reader)
	if err != nil {
		return Segment{}, fmt.Errorf("failed to read: %w", err)
	}
	// Record the bytes actually read, never the declared size, matching the
	// production store. Save already sets the segment size to len(data).
	return m.Save(ctx, did, data)
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
		if seg.Offset < 0 || seg.Size < 0 || seg.Offset > int64(len(data)) || seg.Size > int64(len(data))-seg.Offset {
			return nil, fmt.Errorf("segment %s range offset=%d size=%d exceeds stored size %d", seg.UUID, seg.Offset, seg.Size, len(data))
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

// LookupRow is one row of a mock lookup table, mapping column name to value.
// It is a type alias for map[string]string so it interoperates freely with
// LookupResults.Rows in tests and assertions.
type LookupRow = map[string]string

// LookupTable is a static lookup table served by MockLookupClient. The mock
// applies lookupQueryParams semantics (match, sort, paginate, project) against the
// rows on every Lookup call, so tests just declare the data.
type LookupTable struct {
	// Name is the table name as referenced by actionkit.Lookup(name, ...).
	Name string
	// Rows are the underlying data rows. Each row maps column name to value.
	Rows []LookupRow
	// Created, when non-nil, is returned in LookupResults.Created.
	Created *time.Time
	// Modified, when non-nil, is returned in LookupResults.Modified.
	Modified *time.Time
}

// applyQuery returns the rows from this table after filtering, sorting,
// paginating, and projecting per the lookupQueryParams.
func (t LookupTable) applyQuery(query lookupQueryParams) []LookupRow {
	rows := filterLookupRows(t.Rows, query.MatchingColumnValues)
	rows = sortLookupRows(rows, query.SortColumn, query.SortDirection)
	rows = paginateLookupRows(rows, query.Offset, query.Limit)
	rows = projectLookupRows(rows, query.ResultColumns)
	return rows
}

// filterLookupRows keeps rows whose values match every column criterion.
func filterLookupRows(rows []LookupRow, criteria map[string][]string) []LookupRow {
	if len(criteria) == 0 {
		return rows
	}
	out := make([]LookupRow, 0, len(rows))
	for _, row := range rows {
		match := true
		for col, want := range criteria {
			if !lookupValueIn(row[col], want) {
				match = false
				break
			}
		}
		if match {
			out = append(out, row)
		}
	}
	return out
}

// projectLookupRows reduces each row to only the named columns.
func projectLookupRows(rows []LookupRow, cols []string) []LookupRow {
	if len(cols) == 0 {
		return rows
	}
	out := make([]LookupRow, len(rows))
	for i, row := range rows {
		p := make(LookupRow, len(cols))
		for _, c := range cols {
			if v, ok := row[c]; ok {
				p[c] = v
			}
		}
		out[i] = p
	}
	return out
}

// sortLookupRows returns a stable copy of rows sorted by the named column.
func sortLookupRows(rows []LookupRow, col string, dir SortDirection) []LookupRow {
	if col == "" {
		return rows
	}
	sorted := make([]LookupRow, len(rows))
	copy(sorted, rows)
	sort.SliceStable(sorted, func(i, j int) bool {
		a, b := sorted[i][col], sorted[j][col]
		if dir == SortDESC {
			return a > b
		}
		return a < b
	})
	return sorted
}

// paginateLookupRows applies offset and limit to the row slice.
func paginateLookupRows(rows []LookupRow, offset, limit *int) []LookupRow {
	if offset != nil && *offset > 0 {
		if *offset >= len(rows) {
			return nil
		}
		rows = rows[*offset:]
	}
	if limit != nil && *limit >= 0 && *limit < len(rows) {
		rows = rows[:*limit]
	}
	return rows
}

// lookupValueIn returns true when value appears in the slice.
func lookupValueIn(value string, set []string) bool {
	return slices.Contains(set, value)
}

// MockLookupClient implements LookupClientBase with configurable canned responses.
type MockLookupClient struct {
	Results     map[string]*LookupResults
	Tables      []LookupTable
	LookupFunc  func(ctx context.Context, tableName string, query lookupQueryParams) (*LookupResults, error)
	Err         error
	LookupCalls []MockLookupCall
}

// MockLookupCall records the arguments of a Lookup call.
type MockLookupCall struct {
	TableName string
	Query     lookupQueryParams
}

func (m *MockLookupClient) Lookup(ctx context.Context, tableName string, opts ...LookupOption) (*LookupResults, error) {
	query := buildlookupQueryParams(opts)
	m.LookupCalls = append(m.LookupCalls, MockLookupCall{TableName: tableName, Query: query})
	if m.Err != nil {
		return nil, m.Err
	}
	if m.LookupFunc != nil {
		return m.LookupFunc(ctx, tableName, query)
	}
	for _, t := range m.Tables {
		if t.Name == tableName {
			rawRows := t.applyQuery(query)
			rows := make([]Row, len(rawRows))
			for i, r := range rawRows {
				rows[i] = NewRow(r)
			}
			return &LookupResults{
				TotalCount: len(rows),
				Created:    t.Created,
				Modified:   t.Modified,
				Rows:       rows,
			}, nil
		}
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
	Artifacts       map[string]*FetchedArtifact
	PublishResponse *PublishResponse
	Err             error
	PublishCalls    []MockPublishCall
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
// The returned ActionContent is wired back to the TestContext's ActionContext
// so that content.LoadString() etc. work without explicit plumbing.
func (tc *TestContext) Content(name, mediaType, data string) ActionContent {
	c := tc.Store.CreateStringContent(tc.DID, name, mediaType, data)
	c.actx = tc.ActionContext
	return c
}

// NewDeltafile creates a Deltafile wired to this TestContext with the given
// content items and empty metadata.
func (tc *TestContext) NewDeltafile(contents ...ActionContent) *Deltafile {
	return newDeltafile(tc.ActionContext, contents, nil)
}

// NewTimedIngressDeltafile creates a TimedIngressDeltafile wired to this
// TestContext, for use by RunIngress in unit tests.
func (tc *TestContext) NewTimedIngressDeltafile() *TimedIngressDeltafile {
	return newTimedIngressDeltafile(tc.ActionContext)
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
			ctx:            context.Background(),
		},
		Store: store,
	}
}

// SetLookupClient sets the lookup client on the TestContext and global state.
// The previous global state is restored when the test completes via t.Cleanup,
// making it safe to use in parallel tests.
func (tc *TestContext) SetLookupClient(t *testing.T, client LookupClientBase) {
	tc.ActionContext.LookupClient = client
	prev := globalState.lookupClient
	prevCtx := globalState.ctx
	globalState.lookupClient = client
	if globalState.ctx == nil {
		globalState.ctx = context.Background()
	}
	t.Cleanup(func() {
		globalState.lookupClient = prev
		globalState.ctx = prevCtx
	})
}

// SetArtifactClient sets the artifact client on the TestContext and global state.
// The previous global state is restored when the test completes via t.Cleanup,
// making it safe to use in parallel tests.
func (tc *TestContext) SetArtifactClient(t *testing.T, client ArtifactClientBase) {
	tc.ActionContext.ArtifactClient = client
	prev := globalState.artifactClient
	prevCtx := globalState.ctx
	globalState.artifactClient = client
	if globalState.ctx == nil {
		globalState.ctx = context.Background()
	}
	t.Cleanup(func() {
		globalState.artifactClient = prev
		globalState.ctx = prevCtx
	})
}

// --- Result Inspection ---

// RunTransform executes a transform action and returns the ActionEvent.
// If the action creates children via df.AddChild, the result is a multi-child
// transform event (split). Otherwise it's a single transform event.
func RunTransform[P any](t *testing.T, action interface {
	Transform(*Deltafile, P) *Deltafile
}, df *Deltafile, params P) *ActionEvent {
	t.Helper()
	startTime := time.Now()
	actx := df.actx
	df = action.Transform(df, params)
	if df == nil {
		return errorEvent(actx, startTime, "action returned nil Deltafile")
	}
	if len(df.children) > 0 {
		return splitToTransformEvent(df.children, df, df.actx, startTime)
	}
	return deltafileToTransformEvent(df, df.actx, startTime)
}

// RunEgress executes an egress action and returns the ActionEvent. The
// Deltafile is wrapped in an EgressDeltafile before being passed to the action,
// so the harness exercises the same protected surface as production.
func RunEgress[P any](t *testing.T, action interface {
	Egress(*EgressDeltafile, P) *EgressDeltafile
}, df *Deltafile, params P) *ActionEvent {
	t.Helper()
	startTime := time.Now()
	if action.Egress(newEgressDeltafile(df), params) == nil {
		return errorEvent(df.actx, startTime, "action returned nil Deltafile")
	}
	return deltafileToEgressEvent(df, df.actx, startTime)
}

// RunIngress executes a timed ingress action and returns the ActionEvent.
func RunIngress[P any](t *testing.T, action interface {
	Ingress(*TimedIngressDeltafile, P) *TimedIngressDeltafile
}, deltafile *TimedIngressDeltafile, params P) *ActionEvent {
	t.Helper()
	startTime := time.Now()
	actx := deltafile.actx
	deltafile = action.Ingress(deltafile, params)
	if deltafile == nil {
		return errorEvent(actx, startTime, "action returned nil TimedIngressDeltafile")
	}
	return timedIngressToEvent(deltafile, deltafile.actx, startTime)
}

// RunJoin executes a join action and returns the joined Deltafile.
func RunJoin[P any](t *testing.T, action interface {
	Join([]*Deltafile, *Deltafile, P) *Deltafile
}, inputs []*Deltafile, joined *Deltafile, params P) *Deltafile {
	t.Helper()
	return action.Join(inputs, joined, params)
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

// RequireSingleTransform asserts the event type is TRANSFORM with exactly one
// child and returns that single TransformEvent.
func (e *ActionEvent) RequireSingleTransform(t *testing.T) TransformEvent {
	t.Helper()
	te := e.RequireTransform(t)
	if len(te) != 1 {
		t.Fatalf("expected 1 transform event, got %d", len(te))
	}
	return te[0]
}

// RequireError asserts the event type is ERROR with the given cause.
func (e *ActionEvent) RequireError(t *testing.T, cause string) *ErrorEvent {
	t.Helper()
	if e.Type != "ERROR" {
		t.Fatalf("Type = %q, want ERROR", e.Type)
	}
	if e.Error == nil {
		t.Fatal("Error event payload is nil")
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
	if e.Filter == nil {
		t.Fatal("Filter event payload is nil")
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
	if e.Ingress == nil {
		t.Fatal("Ingress event payload is nil")
	}
	return e.Ingress
}

// RequireContentString loads the content at the given index as a string,
// failing the test on error.
func (te TransformEvent) RequireContentString(t *testing.T, index int) string {
	t.Helper()
	if index < 0 || index >= len(te.Content) {
		t.Fatalf("content index %d out of range (have %d)", index, len(te.Content))
	}
	data, err := te.Content[index].LoadString()
	if err != nil {
		t.Fatalf("failed to load content[%d]: %v", index, err)
	}
	return data
}
