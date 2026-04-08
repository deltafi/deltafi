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
// ABOUTME: Tests for lookup table client, supplier interface, and supplier runner.
// ABOUTME: Verifies GraphQL query construction, result parsing, and supplier event dispatching.

package actionkit

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"
)

func TestLookupClient_Lookup(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v2/graphql" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("unexpected content-type: %s", r.Header.Get("Content-Type"))
		}

		body, _ := io.ReadAll(r.Body)
		var req graphqlRequest
		if err := json.Unmarshal(body, &req); err != nil {
			t.Fatalf("failed to parse request: %v", err)
		}

		vars := req.Variables
		if vars["lookupTableName"] != "test_table" {
			t.Errorf("unexpected table name: %v", vars["lookupTableName"])
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{
					"totalCount": 2,
					"created":    "2026-03-13T10:00:00Z",
					"modified":   "2026-03-13T11:00:00Z",
					"rows": [][]map[string]string{
						{{"column": "color", "value": "red"}, {"column": "score", "value": "10"}},
						{{"column": "color", "value": "blue"}, {"column": "score", "value": "20"}},
					},
				},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	results, err := client.Lookup(context.Background(), "test_table",
		WithMatch("color", "red", "blue"),
		WithResultColumns("score"),
	)
	if err != nil {
		t.Fatalf("Lookup failed: %v", err)
	}

	if results.TotalCount != 2 {
		t.Errorf("TotalCount = %d, want 2", results.TotalCount)
	}
	if results.Created == nil {
		t.Error("Created should not be nil")
	}
	if len(results.Rows) != 2 {
		t.Fatalf("Rows length = %d, want 2", len(results.Rows))
	}
	if results.Rows[0]["color"] != "red" {
		t.Errorf("Rows[0][color] = %q, want %q", results.Rows[0]["color"], "red")
	}
	if results.Rows[1]["score"] != "20" {
		t.Errorf("Rows[1][score] = %q, want %q", results.Rows[1]["score"], "20")
	}
}

func TestLookupClient_Lookup_NilOptions(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		var req graphqlRequest
		json.Unmarshal(body, &req)

		// Should only have lookupTableName variable
		if _, ok := req.Variables["matchingColumnValues"]; ok {
			t.Error("matchingColumnValues should not be set with nil options")
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{
					"totalCount": 0,
					"rows":       []any{},
				},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	results, err := client.Lookup(context.Background(), "empty_table")
	if err != nil {
		t.Fatalf("Lookup failed: %v", err)
	}
	if results.TotalCount != 0 {
		t.Errorf("TotalCount = %d, want 0", results.TotalCount)
	}
}

func TestLookupClient_Lookup_Cached(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{
					"totalCount": callCount,
					"rows":       []any{},
				},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)

	// First call hits the server
	r1, err := client.Lookup(context.Background(), "t", WithResultColumns("a"))
	if err != nil {
		t.Fatalf("Lookup 1 failed: %v", err)
	}
	if callCount != 1 {
		t.Fatalf("expected 1 server call, got %d", callCount)
	}

	// Second call should be cached
	r2, err := client.Lookup(context.Background(), "t", WithResultColumns("a"))
	if err != nil {
		t.Fatalf("Lookup 2 failed: %v", err)
	}
	if callCount != 1 {
		t.Errorf("expected 1 server call (cached), got %d", callCount)
	}
	if r2.TotalCount != r1.TotalCount {
		t.Errorf("cached result should match: got %d, want %d", r2.TotalCount, r1.TotalCount)
	}

	// Different options should miss cache
	_, err = client.Lookup(context.Background(), "t", nil)
	if err != nil {
		t.Fatalf("Lookup 3 failed: %v", err)
	}
	if callCount != 2 {
		t.Errorf("expected 2 server calls (cache miss), got %d", callCount)
	}
}

func TestLookupClient_Lookup_CacheDisabled(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{"totalCount": 0, "rows": []any{}},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	client.SetCacheTTL(0)

	client.Lookup(context.Background(), "t", nil)
	client.Lookup(context.Background(), "t", nil)

	if callCount != 2 {
		t.Errorf("with TTL=0, expected 2 server calls, got %d", callCount)
	}
}

func TestLookupClient_ClearCache(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{"totalCount": callCount, "rows": []any{}},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	client.SetCacheTTL(1 * time.Minute)

	client.Lookup(context.Background(), "t", nil)
	if callCount != 1 {
		t.Fatalf("expected 1 call, got %d", callCount)
	}

	client.ClearCache()

	r, _ := client.Lookup(context.Background(), "t", nil)
	if callCount != 2 {
		t.Errorf("after ClearCache, expected 2 calls, got %d", callCount)
	}
	if r.TotalCount != 2 {
		t.Errorf("expected fresh result with TotalCount=2, got %d", r.TotalCount)
	}
}

func TestLookupClient_Lookup_FailureCached(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("server error"))
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)

	// First call fails and should be cached
	_, err := client.Lookup(context.Background(), "t", nil)
	if err == nil {
		t.Fatal("expected error")
	}
	if callCount != 1 {
		t.Fatalf("expected 1 server call, got %d", callCount)
	}

	// Second call should return cached error (no new server call)
	_, err2 := client.Lookup(context.Background(), "t", nil)
	if err2 == nil {
		t.Fatal("expected cached error")
	}
	if callCount != 1 {
		t.Errorf("expected 1 server call (failure cached), got %d", callCount)
	}

	// After 1 second, failure cache should expire and retry
	time.Sleep(1100 * time.Millisecond)
	_, err3 := client.Lookup(context.Background(), "t", nil)
	if err3 == nil {
		t.Fatal("expected error on retry")
	}
	if callCount != 2 {
		t.Errorf("expected 2 server calls after failure TTL expiry, got %d", callCount)
	}
}

func TestLookupClient_Lookup_FailureRecovery(t *testing.T) {
	callCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		callCount++
		if callCount == 1 {
			w.WriteHeader(http.StatusInternalServerError)
			w.Write([]byte("server error"))
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{"totalCount": 5, "rows": []any{}},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)

	// First call fails
	_, err := client.Lookup(context.Background(), "t", nil)
	if err == nil {
		t.Fatal("expected error")
	}

	// After failure TTL expires, should recover with success
	time.Sleep(1100 * time.Millisecond)
	results, err := client.Lookup(context.Background(), "t", nil)
	if err != nil {
		t.Fatalf("expected recovery, got error: %v", err)
	}
	if results.TotalCount != 5 {
		t.Errorf("TotalCount = %d, want 5", results.TotalCount)
	}
}

func TestParseCacheTTL_IntegerSeconds(t *testing.T) {
	d := parseCacheTTL("10", 30*time.Second)
	if d != 10*time.Second {
		t.Errorf("parseCacheTTL(%q) = %v, want 10s", "10", d)
	}
}

func TestParseCacheTTL_GoDuration(t *testing.T) {
	d := parseCacheTTL("2m30s", 30*time.Second)
	if d != 2*time.Minute+30*time.Second {
		t.Errorf("parseCacheTTL(%q) = %v, want 2m30s", "2m30s", d)
	}
}

func TestParseCacheTTL_Empty(t *testing.T) {
	d := parseCacheTTL("", 30*time.Second)
	if d != 30*time.Second {
		t.Errorf("parseCacheTTL(%q) = %v, want 30s", "", d)
	}
}

func TestParseCacheTTL_Invalid(t *testing.T) {
	d := parseCacheTTL("garbage", 30*time.Second)
	if d != 30*time.Second {
		t.Errorf("parseCacheTTL(%q) = %v, want 30s fallback", "garbage", d)
	}
}

func TestParseCacheTTL_Zero(t *testing.T) {
	d := parseCacheTTL("0", 30*time.Second)
	if d != 0 {
		t.Errorf("parseCacheTTL(%q) = %v, want 0 (disabled)", "0", d)
	}
}

func TestLookupClient_CacheTTL_FromEnv(t *testing.T) {
	os.Setenv("TABLE_LOOKUP_CACHE_TTL", "45")
	defer os.Unsetenv("TABLE_LOOKUP_CACHE_TTL")

	client := NewLookupClient("http://localhost")
	if got := time.Duration(client.cacheTTL.Load()); got != 45*time.Second {
		t.Errorf("cacheTTL = %v, want 45s from env", got)
	}
}

func TestLookupClient_CacheTTL_FromEnvDuration(t *testing.T) {
	os.Setenv("TABLE_LOOKUP_CACHE_TTL", "5m")
	defer os.Unsetenv("TABLE_LOOKUP_CACHE_TTL")

	client := NewLookupClient("http://localhost")
	if got := time.Duration(client.cacheTTL.Load()); got != 5*time.Minute {
		t.Errorf("cacheTTL = %v, want 5m from env", got)
	}
}

func TestLookupClient_FailureCacheTTL_FromEnv(t *testing.T) {
	os.Setenv("TABLE_LOOKUP_FAILURE_CACHE_TTL", "3s")
	defer os.Unsetenv("TABLE_LOOKUP_FAILURE_CACHE_TTL")

	client := NewLookupClient("http://localhost")
	if got := time.Duration(client.failureCacheTTL.Load()); got != 3*time.Second {
		t.Errorf("failureCacheTTL = %v, want 3s from env", got)
	}
}

func TestLookupClient_FailureCacheTTL_Default(t *testing.T) {
	client := NewLookupClient("http://localhost")
	if got := time.Duration(client.failureCacheTTL.Load()); got != 1*time.Second {
		t.Errorf("failureCacheTTL = %v, want 1s default", got)
	}
}

func TestLookupClient_Lookup_GraphQLError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"errors": []map[string]string{
				{"message": "table not found"},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	_, err := client.Lookup(context.Background(), "missing_table", nil)
	if err == nil {
		t.Fatal("expected error for missing table")
	}
	if err.Error() != "graphql error: table not found" {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestLookupClient_Lookup_HTTPError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	_, err := client.Lookup(context.Background(), "table", nil)
	if err == nil {
		t.Fatal("expected error for HTTP 500")
	}
}

func TestLookupClient_Lookup_WithAllOptions(t *testing.T) {
	var capturedVars map[string]any

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		var req graphqlRequest
		json.Unmarshal(body, &req)
		capturedVars = req.Variables

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]any{
			"data": map[string]any{
				"lookup": map[string]any{"totalCount": 0, "rows": []any{}},
			},
		})
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	client.Lookup(context.Background(), "t",
		WithSort("name", SortDESC),
		WithOffset(5),
		WithLimit(10),
	)

	if capturedVars["sortColumn"] != "name" {
		t.Errorf("sortColumn = %v, want %q", capturedVars["sortColumn"], "name")
	}
	if capturedVars["sortDirection"] != "DESC" {
		t.Errorf("sortDirection = %v, want %q", capturedVars["sortDirection"], "DESC")
	}
	// JSON numbers unmarshal as float64
	if capturedVars["offset"] != float64(5) {
		t.Errorf("offset = %v, want 5", capturedVars["offset"])
	}
	if capturedVars["limit"] != float64(10) {
		t.Errorf("limit = %v, want 10", capturedVars["limit"])
	}
}

func TestLookupClient_UploadTable(t *testing.T) {
	var capturedBody string
	var capturedContentType string
	var capturedHeaders http.Header

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v2/lookup/test_table" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		capturedContentType = r.Header.Get("Content-Type")
		capturedHeaders = r.Header
		body, _ := io.ReadAll(r.Body)
		capturedBody = string(body)
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	data := `[{"color":"red","score":"10"}]`
	err := client.UploadTable(context.Background(), "test_table", "application/json", data)
	if err != nil {
		t.Fatalf("UploadTable failed: %v", err)
	}

	if capturedBody != data {
		t.Errorf("body = %q, want %q", capturedBody, data)
	}
	if capturedContentType != "application/json" {
		t.Errorf("content-type = %q, want application/json", capturedContentType)
	}
	if capturedHeaders.Get("X-User-Permissions") != "Admin" {
		t.Error("missing X-User-Permissions header")
	}
}

func TestLookupClient_UploadTable_Error(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("bad request"))
	}))
	defer server.Close()

	client := NewLookupClient(server.URL)
	err := client.UploadTable(context.Background(), "test_table", "text/csv", "bad,data")
	if err == nil {
		t.Fatal("expected error for HTTP 400")
	}
}

func TestLookupTableEventKey(t *testing.T) {
	key := lookupTableEventKey("my_table")
	if key != "lookup-table-event-my_table" {
		t.Errorf("key = %q, want %q", key, "lookup-table-event-my_table")
	}
}

func TestParseLookupResults_EmptyRows(t *testing.T) {
	gql := &graphqlLookupResults{
		TotalCount: 0,
		Rows:       [][]graphqlColumnValue{},
	}
	results, err := parseLookupResults(gql)
	if err != nil {
		t.Fatalf("parseLookupResults failed: %v", err)
	}
	if results.TotalCount != 0 {
		t.Errorf("TotalCount = %d, want 0", results.TotalCount)
	}
	if len(results.Rows) != 0 {
		t.Errorf("Rows length = %d, want 0", len(results.Rows))
	}
	if results.Created != nil {
		t.Error("Created should be nil")
	}
}

func TestParseLookupResults_WithTimestamps(t *testing.T) {
	created := "2026-03-13T10:00:00Z"
	gql := &graphqlLookupResults{
		TotalCount: 1,
		Created:    &created,
		Rows: [][]graphqlColumnValue{
			{
				{Column: "a", Value: "1"},
				{Column: "b", Value: "2"},
			},
		},
	}
	results, err := parseLookupResults(gql)
	if err != nil {
		t.Fatalf("parseLookupResults failed: %v", err)
	}
	if results.Created == nil {
		t.Fatal("Created should not be nil")
	}
	if results.Rows[0]["a"] != "1" {
		t.Errorf("Rows[0][a] = %q, want %q", results.Rows[0]["a"], "1")
	}
}

func TestPlugin_RegisterLookupTableSupplier(t *testing.T) {
	p := &Plugin{}
	supplier := &testLookupTableSupplier{
		table: LookupTableDefinition{
			Name:          "test_table",
			Columns:       []string{"a", "b"},
			KeyColumns:    []string{"a"},
			ServiceBacked: true,
		},
	}

	p.RegisterLookupTableSupplier(supplier)

	if len(p.suppliers) != 1 {
		t.Fatalf("suppliers count = %d, want 1", len(p.suppliers))
	}
	if _, ok := p.suppliers["test_table"]; !ok {
		t.Error("supplier not registered under 'test_table'")
	}

	tables := p.lookupTables()
	if len(tables) != 1 {
		t.Fatalf("lookupTables count = %d, want 1", len(tables))
	}
	if tables[0].Name != "test_table" {
		t.Errorf("table name = %q, want %q", tables[0].Name, "test_table")
	}
}

func TestPlugin_LookupTables_Empty(t *testing.T) {
	p := &Plugin{}
	tables := p.lookupTables()
	if len(tables) != 0 {
		t.Errorf("lookupTables count = %d, want 0", len(tables))
	}
}

// testLookupTableSupplier is a test implementation of LookupTableSupplier.
type testLookupTableSupplier struct {
	table LookupTableDefinition
	rows  []map[string]string
}

func (s *testLookupTableSupplier) Table() LookupTableDefinition {
	return s.table
}

func (s *testLookupTableSupplier) GetRows(_ context.Context, _ map[string]string, _ map[string][]string, _ []string) ([]map[string]string, error) {
	return s.rows, nil
}

func TestLookupTableDefinition_JSON(t *testing.T) {
	def := LookupTableDefinition{
		Name:                 "colors",
		Columns:              []string{"color", "hex"},
		KeyColumns:           []string{"color"},
		ServiceBacked:        true,
		BackingServiceActive: true,
		PullThrough:          false,
		RefreshDuration:      "PT5M",
	}

	data, err := json.Marshal(def)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var parsed LookupTableDefinition
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if parsed.Name != "colors" {
		t.Errorf("Name = %q, want %q", parsed.Name, "colors")
	}
	if !parsed.ServiceBacked {
		t.Error("ServiceBacked should be true")
	}
	if parsed.RefreshDuration != "PT5M" {
		t.Errorf("RefreshDuration = %q, want %q", parsed.RefreshDuration, "PT5M")
	}
}

func TestLookupTableEventResult_JSON(t *testing.T) {
	result := lookupTableEventResult{
		LookupTableEventID: "lookup-table-event-abc-0",
		LookupTableName:    "test_table",
		Rows: []map[string]string{
			{"a": "1", "b": "2"},
		},
	}

	data, err := json.Marshal(result)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}

	var parsed lookupTableEventResult
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}

	if parsed.LookupTableEventID != "lookup-table-event-abc-0" {
		t.Errorf("LookupTableEventID = %q, want %q", parsed.LookupTableEventID, "lookup-table-event-abc-0")
	}
	if len(parsed.Rows) != 1 {
		t.Fatalf("Rows length = %d, want 1", len(parsed.Rows))
	}
	if parsed.Rows[0]["a"] != "1" {
		t.Errorf("Rows[0][a] = %q, want %q", parsed.Rows[0]["a"], "1")
	}
}
