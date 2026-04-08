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

// LookupRow is one row of a mock lookup table, mapping column name to value.
// It is a type alias for map[string]string so it interoperates freely with
// LookupResults.Rows in tests and assertions.
type LookupRow = map[string]string

// LookupTable is a static lookup table served by MockLookupClient. The mock
// applies LookupQuery semantics (match, sort, paginate, project) against the
// rows on every Lookup call, so tests just declare the data.
type LookupTable struct {
	// Name is the table name as referenced by actx.LookupClient.Lookup(ctx, name, ...).
	Name string
	// Rows are the underlying data rows. Each row maps column name to value.
	Rows []LookupRow
	// Created, when non-nil, is returned in LookupResults.Created.
	Created *time.Time
	// Modified, when non-nil, is returned in LookupResults.Modified.
	Modified *time.Time
}

// applyQuery returns the rows from this table after filtering, sorting,
// paginating, and projecting per the LookupQuery.
func (t LookupTable) applyQuery(query LookupQuery) []LookupRow {
	rows := filterLookupRows(t.Rows, query.MatchingColumnValues)
	rows = sortLookupRows(rows, query.SortColumn, query.SortDirection)
	rows = paginateLookupRows(rows, query.Offset, query.Limit)
	rows = projectLookupRows(rows, query.ResultColumns)
	return rows
}

// filterLookupRows keeps rows whose values match every column criterion.
// Within a column, any of the listed values is a match (OR). Across columns,
// every criterion must match (AND). An empty criteria map returns all rows.
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

// projectLookupRows reduces each row to only the named columns. Columns that
// do not exist in the row are silently dropped (matching SQL projection).
// An empty cols slice returns the rows unchanged.
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
// An empty col returns the rows unchanged. Comparison is string-based.
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

// paginateLookupRows applies offset and limit to the row slice. nil values
// for either are treated as "unset". Negative offsets are clamped to zero.
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
//
// Tests can wire it three ways, in order of precedence (highest first):
//  1. Err — return this error for every call.
//  2. LookupFunc — call this function for every Lookup; useful for custom logic.
//  3. Tables — declarative tables; the mock applies LookupQuery semantics
//     (match, sort, paginate, project) against the configured rows. This is
//     the cleanest option for typical tests.
//  4. Results — canned full LookupResults keyed by table name.
//
// Lookups that hit none of the above return an empty LookupResults.
type MockLookupClient struct {
	// Results maps table names to canned, fully-formed lookup results.
	Results map[string]*LookupResults

	// Tables is a declarative set of lookup tables. The mock applies the
	// LookupQuery against each matching table's rows automatically.
	Tables []LookupTable

	// LookupFunc, when set, handles all Lookup calls. Takes precedence over
	// Tables and Results. It receives the resolved LookupQuery so custom
	// handlers can inspect the match criteria without re-applying options.
	LookupFunc func(ctx context.Context, tableName string, query LookupQuery) (*LookupResults, error)

	// Err is returned for all lookups when non-nil, overriding everything else.
	Err error

	// LookupCalls records each call to Lookup for assertion.
	LookupCalls []MockLookupCall
}

// MockLookupCall records the arguments of a Lookup call.
type MockLookupCall struct {
	TableName string
	Query     LookupQuery
}

func (m *MockLookupClient) Lookup(ctx context.Context, tableName string, opts ...LookupOption) (*LookupResults, error) {
	query := buildLookupQuery(opts)
	m.LookupCalls = append(m.LookupCalls, MockLookupCall{TableName: tableName, Query: query})
	if m.Err != nil {
		return nil, m.Err
	}
	if m.LookupFunc != nil {
		return m.LookupFunc(ctx, tableName, query)
	}
	for _, t := range m.Tables {
		if t.Name == tableName {
			rows := t.applyQuery(query)
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
// The returned ActionContent is wired back to the TestContext's ActionContext
// so that content.LoadString(ctx) etc. work without explicit plumbing.
func (tc *TestContext) Content(name, mediaType, data string) ActionContent {
	c := tc.Store.CreateStringContent(tc.DID, name, mediaType, data)
	c.actx = tc.ActionContext
	return c
}

// NewTransformInput builds a TransformInput wired to the TestContext with the
// given content items, an empty metadata map, and an empty params map. Use the
// struct literal form directly when you need to populate metadata or params.
func NewTransformInput(tc *TestContext, contents ...ActionContent) TransformInput {
	return TransformInput{
		ActionContext: tc.ActionContext,
		Content:       contents,
		Metadata:      map[string]string{},
		Params:        map[string]any{},
	}
}

// NewEgressInput builds an EgressInput wired to the TestContext with the given
// content items, an empty metadata map, and an empty params map.
func NewEgressInput(tc *TestContext, contents ...ActionContent) EgressInput {
	return EgressInput{
		ActionContext: tc.ActionContext,
		Content:       contents,
		Metadata:      map[string]string{},
		Params:        map[string]any{},
	}
}

// NewIngressInput builds an IngressInput wired to the TestContext with an
// empty params map.
func NewIngressInput(tc *TestContext) IngressInput {
	return IngressInput{
		ActionContext: tc.ActionContext,
		Params:        map[string]any{},
	}
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
func RunTransform(t *testing.T, action TransformAction, input TransformInput) *ActionEvent {
	t.Helper()
	result := action.Transform(context.Background(), input)
	return ResultEvent(result, input.ActionContext)
}

// RunEgress executes an EgressAction and returns the ActionEvent.
func RunEgress(t *testing.T, action EgressAction, input EgressInput) *ActionEvent {
	t.Helper()
	result := action.Egress(context.Background(), input)
	return ResultEvent(result, input.ActionContext)
}

// RunIngress executes a TimedIngressAction and returns the ActionEvent.
func RunIngress(t *testing.T, action TimedIngressAction, input IngressInput) *ActionEvent {
	t.Helper()
	result := action.Ingress(context.Background(), input)
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
