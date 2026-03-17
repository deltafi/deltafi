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
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"
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

// LookupOptions configures a lookup table query.
type LookupOptions struct {
	MatchingColumnValues map[string][]string
	ResultColumns        []string
	SortColumn           string
	SortDirection        SortDirection
	Offset               *int
	Limit                *int
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
	Lookup(ctx context.Context, tableName string, opts *LookupOptions) (*LookupResults, error)
}

// LookupClient queries and uploads lookup table data via core's API.
type LookupClient struct {
	coreURL         string
	httpClient      *http.Client
	cacheTTL        time.Duration
	failureCacheTTL time.Duration
	mu              sync.RWMutex
	cache           map[string]*lookupCacheEntry
}

type lookupCacheEntry struct {
	results   *LookupResults
	err       error
	expiresAt time.Time
}

const defaultLookupCacheTTL = 30 * time.Second
const defaultFailureCacheTTL = 1 * time.Second

// NewLookupClient creates a client for lookup table operations.
// Cache TTL defaults to 30 seconds, overridable via TABLE_LOOKUP_CACHE_TTL.
// Failure cache TTL defaults to 1 second, overridable via TABLE_LOOKUP_FAILURE_CACHE_TTL.
// Both accept integer seconds or Go duration strings like "5s", "1m".
func NewLookupClient(coreURL string) *LookupClient {
	return &LookupClient{
		coreURL:         coreURL,
		httpClient:      &http.Client{Timeout: 30 * time.Second},
		cacheTTL:        parseCacheTTL(os.Getenv("TABLE_LOOKUP_CACHE_TTL"), defaultLookupCacheTTL),
		failureCacheTTL: parseCacheTTL(os.Getenv("TABLE_LOOKUP_FAILURE_CACHE_TTL"), defaultFailureCacheTTL),
		cache:           make(map[string]*lookupCacheEntry),
	}
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
// Set to 0 to disable caching.
func (lc *LookupClient) SetCacheTTL(ttl time.Duration) {
	lc.mu.Lock()
	defer lc.mu.Unlock()
	lc.cacheTTL = ttl
}

// ClearCache evicts all cached lookup results.
func (lc *LookupClient) ClearCache() {
	lc.mu.Lock()
	defer lc.mu.Unlock()
	lc.cache = make(map[string]*lookupCacheEntry)
}

func lookupCacheKey(tableName string, opts *LookupOptions) string {
	h := sha256.New()
	h.Write([]byte(tableName))
	if opts != nil {
		data, _ := json.Marshal(opts)
		h.Write(data)
	}
	return fmt.Sprintf("%x", h.Sum(nil))
}

// setAuthHeaders adds the standard DeltaFi auth headers to a request.
func (lc *LookupClient) setAuthHeaders(req *http.Request) {
	req.Header.Set("X-User-Permissions", "Admin")
	req.Header.Set("X-User-Name", "go-plugin")
}

// Lookup queries a lookup table by name with the given options.
// Successful results are cached for the configured TTL.
// Failures are cached for 1 second to prevent flooding core during outages.
func (lc *LookupClient) Lookup(ctx context.Context, tableName string, opts *LookupOptions) (*LookupResults, error) {
	cacheKey := lookupCacheKey(tableName, opts)

	lc.mu.RLock()
	if entry, ok := lc.cache[cacheKey]; ok && time.Now().Before(entry.expiresAt) {
		lc.mu.RUnlock()
		return entry.results, entry.err
	}
	lc.mu.RUnlock()

	results, err := lc.fetchLookup(ctx, tableName, opts)

	lc.mu.Lock()
	ttl := lc.cacheTTL
	if err != nil {
		ttl = lc.failureCacheTTL
	}
	if ttl > 0 {
		lc.cache[cacheKey] = &lookupCacheEntry{
			results:   results,
			err:       err,
			expiresAt: time.Now().Add(ttl),
		}
	}
	lc.mu.Unlock()

	return results, err
}

// fetchLookup performs the actual GraphQL query to core.
func (lc *LookupClient) fetchLookup(ctx context.Context, tableName string, opts *LookupOptions) (*LookupResults, error) {
	variables := map[string]any{
		"lookupTableName": tableName,
	}

	if opts != nil {
		if len(opts.MatchingColumnValues) > 0 {
			var mcv []map[string]any
			for col, vals := range opts.MatchingColumnValues {
				mcv = append(mcv, map[string]any{
					"column": col,
					"value":  vals,
				})
			}
			variables["matchingColumnValues"] = mcv
		}
		if len(opts.ResultColumns) > 0 {
			variables["resultColumns"] = opts.ResultColumns
		}
		if opts.SortColumn != "" {
			variables["sortColumn"] = opts.SortColumn
		}
		if opts.SortDirection != "" {
			variables["sortDirection"] = string(opts.SortDirection)
		}
		if opts.Offset != nil {
			variables["offset"] = *opts.Offset
		}
		if opts.Limit != nil {
			variables["limit"] = *opts.Limit
		}
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
func (lc *LookupClient) UploadTable(ctx context.Context, tableName string, contentType string, data string) error {
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
func uploadSupplierData(ctx context.Context, client *LookupClient, suppliers map[string]LookupTableSupplier) {
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
func startLookupTableSupplierRunner(ctx context.Context, wg *sync.WaitGroup, queue *ActionEventQueue, suppliers map[string]LookupTableSupplier) {
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
			if raw == "" {
				continue
			}

			var event lookupTableEvent
			if err := json.Unmarshal([]byte(raw), &event); err != nil {
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

			if err := queue.Put(ctx, event.ID, string(resultJSON)); err != nil {
				log.Printf("[lookup-supplier] failed to publish result: %v", err)
			}
		}
	})
}
