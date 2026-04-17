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
// ABOUTME: Lookup table client for querying and uploading lookup tables via core's GraphQL/REST API.
// ABOUTME: Lookup table supplier interface and runner for providing table data to core on demand.

package actionkit

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/sync/singleflight"
)

const (
	graphqlPath   = "/api/v2/graphql"
	lookupAPIPath = "/api/v2/lookup/"
)

// SortDirection specifies the sort order for lookup queries.
type SortDirection string

const (
	SortASC  SortDirection = "ASC"
	SortDESC SortDirection = "DESC"
)

// lookupQueryParams is the resolved set of options for a single Lookup call.
// Callers build a lookupQueryParams by passing LookupOption values to Lookup
// (WithMatch, WithResultColumns, etc.); MockLookupClient exposes the resolved
// lookupQueryParams on MockLookupCall for test assertions.
type lookupQueryParams struct {
	MatchingColumnValues map[string][]string
	ResultColumns        []string
	SortColumn           string
	SortDirection        SortDirection
	Offset               *int
	Limit                *int
}

// LookupOption configures a lookupQueryParams. Options are applied in order, so
// later options override earlier ones for single-valued fields.
type LookupOption func(*lookupQueryParams)

// WithMatch adds a match criterion: the named column must contain any of the
// given values. Calling WithMatch multiple times with the same column appends
// values; calling it with different columns adds additional match criteria.
func WithMatch(column string, values ...string) LookupOption {
	return func(q *lookupQueryParams) {
		if q.MatchingColumnValues == nil {
			q.MatchingColumnValues = map[string][]string{}
		}
		q.MatchingColumnValues[column] = append(q.MatchingColumnValues[column], values...)
	}
}

// WithMatches merges a map of column → values into the query. Use when the
// set of columns is built dynamically; prefer WithMatch for static queries.
func WithMatches(columnValues map[string][]string) LookupOption {
	return func(q *lookupQueryParams) {
		if q.MatchingColumnValues == nil {
			q.MatchingColumnValues = map[string][]string{}
		}
		for k, v := range columnValues {
			q.MatchingColumnValues[k] = append(q.MatchingColumnValues[k], v...)
		}
	}
}

// WithResultColumns restricts the query to return only the named columns.
// Calling it multiple times appends to the list.
func WithResultColumns(columns ...string) LookupOption {
	return func(q *lookupQueryParams) {
		q.ResultColumns = append(q.ResultColumns, columns...)
	}
}

// WithSort orders results by the given column in the given direction.
func WithSort(column string, direction SortDirection) LookupOption {
	return func(q *lookupQueryParams) {
		q.SortColumn = column
		q.SortDirection = direction
	}
}

// WithOffset skips the first N results. The zero value is a valid offset.
func WithOffset(offset int) LookupOption {
	return func(q *lookupQueryParams) { q.Offset = &offset }
}

// WithLimit caps the number of returned rows. The zero value returns no rows.
func WithLimit(limit int) LookupOption {
	return func(q *lookupQueryParams) { q.Limit = &limit }
}

// buildlookupQueryParams resolves a variadic slice of LookupOptions into a single
// lookupQueryParams. Returns the zero-valued lookupQueryParams when opts is empty. Nil
// entries in opts are skipped.
func buildlookupQueryParams(opts []LookupOption) lookupQueryParams {
	var q lookupQueryParams
	for _, opt := range opts {
		if opt == nil {
			continue
		}
		opt(&q)
	}
	return q
}

// LookupResults holds the results of a lookup table query.
type LookupResults struct {
	TotalCount int
	Created    *time.Time
	Modified   *time.Time
	Rows       []map[string]string
}

// LookupTableDefinition describes a lookup table for plugin registration.
type LookupTableDefinition struct {
	Name                 string     `json:"name"`
	Columns              []string   `json:"columns"`
	KeyColumns           []string   `json:"keyColumns"`
	ServiceBacked        bool       `json:"serviceBacked"`
	BackingServiceActive bool       `json:"backingServiceActive"`
	PullThrough          bool       `json:"pullThrough"`
	RefreshDuration      string     `json:"refreshDuration,omitempty"`
	Variables            []Variable `json:"variables,omitempty"`
}

// LookupTableSupplier provides lookup table data to core on demand.
// Implementations must be safe for concurrent use.
type LookupTableSupplier interface {
	// Table returns the lookup table definition for registration.
	Table() LookupTableDefinition

	// GetRows returns rows matching the given criteria.
	GetRows(ctx context.Context, variables map[string]string, matchingColumnValues map[string][]string, resultColumns []string) ([]map[string]string, error)
}

// LookupClientBase is the interface for lookup table queries from actions.
// LookupClient (HTTP) and MockLookupClient (testing) both implement this.
type LookupClientBase interface {
	Lookup(ctx context.Context, tableName string, opts ...LookupOption) (*LookupResults, error)
}

// lookupClient queries and uploads lookup table data via core's API.
//
// cacheTTL and failureCacheTTL are stored as int64 nanoseconds via atomic ops
// so SetCacheTTL is safe to call from any goroutine. The cache map itself is
// protected by mu. The singleflight group collapses concurrent first-time
// lookups for the same cache key into a single underlying request.
type lookupClient struct {
	coreURL         string
	httpClient      *http.Client
	cacheTTL        atomic.Int64 // nanoseconds
	failureCacheTTL atomic.Int64 // nanoseconds
	mu              sync.RWMutex
	cache           map[string]*lookupCacheEntry
	flight          singleflight.Group
}

type lookupCacheEntry struct {
	results   *LookupResults
	err       error
	expiresAt time.Time
}

const defaultLookupCacheTTL = 30 * time.Second
const defaultFailureCacheTTL = 1 * time.Second

// newLookupClient creates a client for lookup table operations.
// Cache TTL defaults to 30 seconds, overridable via TABLE_LOOKUP_CACHE_TTL.
// Failure cache TTL defaults to 1 second, overridable via TABLE_LOOKUP_FAILURE_CACHE_TTL.
// Both accept integer seconds or Go duration strings like "5s", "1m".
func newLookupClient(coreURL string) *lookupClient {
	c := &lookupClient{
		coreURL:    coreURL,
		httpClient: &http.Client{Transport: platformTransport.Clone(), Timeout: 30 * time.Second},
		cache:      make(map[string]*lookupCacheEntry),
	}
	c.cacheTTL.Store(int64(parseCacheTTL(os.Getenv("TABLE_LOOKUP_CACHE_TTL"), defaultLookupCacheTTL)))
	c.failureCacheTTL.Store(int64(parseCacheTTL(os.Getenv("TABLE_LOOKUP_FAILURE_CACHE_TTL"), defaultFailureCacheTTL)))
	return c
}

// parseCacheTTL parses a TTL string as either integer seconds or a Go duration.
// Returns the fallback if the string is empty or unparseable.
func parseCacheTTL(s string, fallback time.Duration) time.Duration {
	if s == "" {
		return fallback
	}
	if secs, err := strconv.Atoi(s); err == nil {
		return time.Duration(secs) * time.Second
	}
	if d, err := time.ParseDuration(s); err == nil {
		return d
	}
	log.Printf("Warning: invalid cache TTL %q, using default %v", s, fallback)
	return fallback
}

// SetCacheTTL configures how long lookup results are cached.
// Set to 0 to disable caching. Safe to call concurrently with Lookup.
func (lc *lookupClient) SetCacheTTL(ttl time.Duration) {
	lc.cacheTTL.Store(int64(ttl))
}

// ClearCache evicts all cached lookup results.
func (lc *lookupClient) ClearCache() {
	lc.mu.Lock()
	defer lc.mu.Unlock()
	lc.cache = make(map[string]*lookupCacheEntry)
}

// startCacheEvictor runs a goroutine that periodically removes expired entries.
func (lc *lookupClient) startCacheEvictor(ctx context.Context, wg *sync.WaitGroup) {
	wg.Go(func() {
		ticker := time.NewTicker(60 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				lc.evictExpired()
			}
		}
	})
}

// evictExpired removes all expired entries from the cache.
func (lc *lookupClient) evictExpired() {
	now := time.Now()
	lc.mu.Lock()
	defer lc.mu.Unlock()
	for key, entry := range lc.cache {
		if !now.Before(entry.expiresAt) {
			delete(lc.cache, key)
		}
	}
}

// lookupCacheKey builds a deterministic in-process cache key for a lookup
// call. Map iteration order is non-deterministic, so MatchingColumnValues is
// canonicalized by sorting column names (and their value slices) before they
// are written into the key.
func lookupCacheKey(tableName string, query lookupQueryParams) string {
	var b strings.Builder
	b.Grow(64 + len(tableName))
	b.WriteString(tableName)
	b.WriteByte('|')

	if len(query.MatchingColumnValues) > 0 {
		cols := make([]string, 0, len(query.MatchingColumnValues))
		for col := range query.MatchingColumnValues {
			cols = append(cols, col)
		}
		sort.Strings(cols)
		for _, col := range cols {
			b.WriteString(col)
			b.WriteByte('=')
			vals := append([]string(nil), query.MatchingColumnValues[col]...)
			sort.Strings(vals)
			b.WriteString(strings.Join(vals, ","))
			b.WriteByte(';')
		}
	}
	b.WriteByte('|')
	if len(query.ResultColumns) > 0 {
		sorted := make([]string, len(query.ResultColumns))
		copy(sorted, query.ResultColumns)
		sort.Strings(sorted)
		b.WriteString(strings.Join(sorted, ","))
	}
	b.WriteByte('|')
	b.WriteString(query.SortColumn)
	b.WriteByte(':')
	b.WriteString(string(query.SortDirection))
	b.WriteByte('|')
	if query.Offset != nil {
		b.WriteString(strconv.Itoa(*query.Offset))
	}
	b.WriteByte('|')
	if query.Limit != nil {
		b.WriteString(strconv.Itoa(*query.Limit))
	}
	return b.String()
}

// setAuthHeaders adds the standard DeltaFi auth headers to a request.
func (lc *lookupClient) setAuthHeaders(req *http.Request) {
	req.Header.Set("X-User-Permissions", "Admin")
	req.Header.Set("X-User-Name", "go-plugin")
}

// Lookup queries a lookup table by name with the given options.
// Successful results are cached for the configured TTL. Failures are cached
// for the failure TTL (default 1s) to prevent flooding core during outages.
// Concurrent first-time lookups for the same key are collapsed into a single
// upstream request via singleflight.
func (lc *lookupClient) Lookup(ctx context.Context, tableName string, opts ...LookupOption) (*LookupResults, error) {
	query := buildlookupQueryParams(opts)
	cacheKey := lookupCacheKey(tableName, query)

	if results, err, ok := lc.cacheGet(cacheKey); ok {
		return results, err
	}

	// singleflight collapses concurrent cache misses for the same key.
	v, err, _ := lc.flight.Do(cacheKey, func() (any, error) {
		// Re-check the cache inside the flight to absorb the case where a
		// concurrent caller populated it while we were waiting for the lock.
		if results, err, ok := lc.cacheGet(cacheKey); ok {
			return results, err
		}
		results, err := lc.fetchLookup(ctx, tableName, query)
		lc.cachePut(cacheKey, results, err)
		return results, err
	})
	if v == nil {
		return nil, err
	}
	return v.(*LookupResults), err
}

// cacheGet returns a cached entry if it is still valid. The third return is
// true when the cache hit is fresh (and the caller should not refetch).
func (lc *lookupClient) cacheGet(key string) (*LookupResults, error, bool) {
	lc.mu.RLock()
	defer lc.mu.RUnlock()
	entry, ok := lc.cache[key]
	if !ok || !time.Now().Before(entry.expiresAt) {
		return nil, nil, false
	}
	return entry.results, entry.err, true
}

// cachePut stores a result with the appropriate TTL (success vs failure).
// Skipped entirely when the success TTL is zero (caching disabled).
func (lc *lookupClient) cachePut(key string, results *LookupResults, fetchErr error) {
	ttl := time.Duration(lc.cacheTTL.Load())
	if fetchErr != nil {
		ttl = time.Duration(lc.failureCacheTTL.Load())
	}
	if ttl <= 0 {
		return
	}
	lc.mu.Lock()
	lc.cache[key] = &lookupCacheEntry{
		results:   results,
		err:       fetchErr,
		expiresAt: time.Now().Add(ttl),
	}
	lc.mu.Unlock()
}

// fetchLookup performs the actual GraphQL query to core.
func (lc *lookupClient) fetchLookup(ctx context.Context, tableName string, query lookupQueryParams) (*LookupResults, error) {
	variables := map[string]any{
		"lookupTableName": tableName,
	}

	if len(query.MatchingColumnValues) > 0 {
		var mcv []map[string]any
		for col, vals := range query.MatchingColumnValues {
			mcv = append(mcv, map[string]any{
				"column": col,
				"value":  vals,
			})
		}
		variables["matchingColumnValues"] = mcv
	}
	if len(query.ResultColumns) > 0 {
		variables["resultColumns"] = query.ResultColumns
	}
	if query.SortColumn != "" {
		variables["sortColumn"] = query.SortColumn
	}
	if query.SortDirection != "" {
		variables["sortDirection"] = string(query.SortDirection)
	}
	if query.Offset != nil {
		variables["offset"] = *query.Offset
	}
	if query.Limit != nil {
		variables["limit"] = *query.Limit
	}

	reqBody := graphqlRequest{
		Query:     lookupQuery,
		Variables: variables,
	}

	data, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal graphql request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, lc.coreURL+graphqlPath, bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	lc.setAuthHeaders(req)

	resp, err := lc.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("graphql request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("graphql request returned HTTP %d: %s", resp.StatusCode, string(body))
	}

	var gqlResp graphqlResponse
	if err := json.Unmarshal(body, &gqlResp); err != nil {
		return nil, fmt.Errorf("failed to parse graphql response: %w", err)
	}

	if len(gqlResp.Errors) > 0 {
		return nil, fmt.Errorf("graphql error: %s", gqlResp.Errors[0].Message)
	}

	return parseLookupResults(&gqlResp.Data.Lookup)
}

// UploadTable uploads table data to a lookup table via REST.
// ContentType should be "application/json" for JSON or "text/csv" for CSV.
func (lc *lookupClient) UploadTable(ctx context.Context, tableName string, contentType string, data string) error {
	url := lc.coreURL + lookupAPIPath + tableName

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader([]byte(data)))
	if err != nil {
		return fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("Content-Type", contentType)
	lc.setAuthHeaders(req)

	resp, err := lc.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("upload request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("upload returned HTTP %d: %s", resp.StatusCode, string(body))
	}

	return nil
}

// graphqlRequest is the JSON body sent to the GraphQL endpoint.
type graphqlRequest struct {
	Query     string         `json:"query"`
	Variables map[string]any `json:"variables"`
}

// graphqlResponse is the JSON response from the GraphQL endpoint.
type graphqlResponse struct {
	Data   graphqlData    `json:"data"`
	Errors []graphqlError `json:"errors,omitempty"`
}

type graphqlData struct {
	Lookup graphqlLookupResults `json:"lookup"`
}

type graphqlError struct {
	Message string `json:"message"`
}

type graphqlLookupResults struct {
	TotalCount int                    `json:"totalCount"`
	Created    *string                `json:"created"`
	Modified   *string                `json:"modified"`
	Rows       [][]graphqlColumnValue `json:"rows"`
}

type graphqlColumnValue struct {
	Column string `json:"column"`
	Value  string `json:"value"`
}

const lookupQuery = `query($lookupTableName: String!, $matchingColumnValues: [MatchingColumnValueInput!], $resultColumns: [String!], $sortColumn: String, $sortDirection: SortDirection, $offset: Int, $limit: Int) {
  lookup(lookupTableName: $lookupTableName, matchingColumnValues: $matchingColumnValues, resultColumns: $resultColumns, sortColumn: $sortColumn, sortDirection: $sortDirection, offset: $offset, limit: $limit) {
    totalCount
    created
    modified
    rows {
      column
      value
    }
  }
}`

func parseLookupResults(gql *graphqlLookupResults) (*LookupResults, error) {
	results := &LookupResults{
		TotalCount: gql.TotalCount,
		Rows:       make([]map[string]string, len(gql.Rows)),
	}

	if gql.Created != nil {
		t, err := time.Parse(time.RFC3339, *gql.Created)
		if err == nil {
			results.Created = &t
		}
	}
	if gql.Modified != nil {
		t, err := time.Parse(time.RFC3339, *gql.Modified)
		if err == nil {
			results.Modified = &t
		}
	}

	for i, row := range gql.Rows {
		m := make(map[string]string, len(row))
		for _, cv := range row {
			m[cv.Column] = cv.Value
		}
		results.Rows[i] = m
	}

	return results, nil
}

// lookupTableEvent is a request from core to query a supplier's table data.
type lookupTableEvent struct {
	ID                   string              `json:"id"`
	LookupTableName      string              `json:"lookupTableName"`
	MatchingColumnValues map[string][]string `json:"matchingColumnValues"`
	ResultColumns        []string            `json:"resultColumns"`
	Variables            map[string]string   `json:"variables"`
}

// lookupTableEventResult is the response sent back to core with the supplier's data.
type lookupTableEventResult struct {
	LookupTableEventID string              `json:"lookupTableEventId"`
	LookupTableName    string              `json:"lookupTableName"`
	Rows               []map[string]string `json:"rows"`
}

const lookupTableEventPrefix = "lookup-table-event"

func lookupTableEventKey(tableName string) string {
	return lookupTableEventPrefix + "-" + tableName
}

// uploadSupplierData fetches rows from each supplier and uploads them to core.
// Called once after registration so tables start with data.
func uploadSupplierData(ctx context.Context, client *lookupClient, suppliers map[string]LookupTableSupplier) {
	for name, supplier := range suppliers {
		rows, err := supplier.GetRows(ctx, nil, nil, nil)
		if err != nil {
			log.Printf("[lookup-supplier] failed to get initial rows for %q: %v", name, err)
			continue
		}
		if len(rows) == 0 {
			log.Printf("[lookup-supplier] no initial rows for %q, skipping upload", name)
			continue
		}

		data, err := json.Marshal(rows)
		if err != nil {
			log.Printf("[lookup-supplier] failed to marshal initial rows for %q: %v", name, err)
			continue
		}

		if err := client.UploadTable(ctx, name, "application/json", string(data)); err != nil {
			log.Printf("[lookup-supplier] failed to upload initial data for %q: %v", name, err)
			continue
		}

		log.Printf("[lookup-supplier] uploaded %d initial rows for %q", len(rows), name)
	}
}

// startLookupTableSupplierRunner launches a goroutine that listens for lookup table
// events and dispatches them to the appropriate supplier.
func startLookupTableSupplierRunner(ctx context.Context, wg *sync.WaitGroup, queue *actionEventQueue, suppliers map[string]LookupTableSupplier) {
	queueNames := make([]string, 0, len(suppliers))
	for name := range suppliers {
		queueNames = append(queueNames, lookupTableEventKey(name))
	}

	wg.Go(func() {
		for {
			if ctx.Err() != nil {
				return
			}

			raw, err := queue.TakeFromAny(ctx, queueNames...)
			if err != nil {
				if ctx.Err() != nil {
					return
				}
				log.Printf("[lookup-supplier] poll error: %v", err)
				continue
			}
			if raw == nil {
				continue
			}

			var event lookupTableEvent
			if err := json.Unmarshal(raw, &event); err != nil {
				log.Printf("[lookup-supplier] failed to deserialize event: %v", err)
				continue
			}

			supplier, ok := suppliers[event.LookupTableName]
			if !ok {
				log.Printf("[lookup-supplier] no supplier for table %q", event.LookupTableName)
				continue
			}

			rows, err := supplier.GetRows(ctx, event.Variables, event.MatchingColumnValues, event.ResultColumns)
			if err != nil {
				log.Printf("[lookup-supplier] error getting rows for %q: %v", event.LookupTableName, err)
				rows = []map[string]string{}
			}

			result := lookupTableEventResult{
				LookupTableEventID: event.ID,
				LookupTableName:    event.LookupTableName,
				Rows:               rows,
			}

			resultJSON, err := json.Marshal(result)
			if err != nil {
				log.Printf("[lookup-supplier] failed to marshal result: %v", err)
				continue
			}

			if err := queue.Put(ctx, event.ID, resultJSON); err != nil {
				log.Printf("[lookup-supplier] failed to publish result: %v", err)
			}
		}
	})
}
