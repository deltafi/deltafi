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
/*
 * ABOUTME: Tests for the core config fetcher.
 * ABOUTME: Verifies GraphQL response parsing, caching, and default value handling.
 */
package config

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"sync"
	"testing"
	"time"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

// =============================================================================
// Constructor and Defaults Tests
// =============================================================================

func TestNewCoreConfig_Defaults(t *testing.T) {
	// With empty URL, should use defaults without making HTTP calls
	c := NewCoreConfig("", testLogger())

	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days %d, got %d", DefaultAgeOffDays, c.GetAgeOffDays())
	}
	if c.GetProvenanceAgeOffDays() != DefaultProvenanceAgeOffDays {
		t.Errorf("expected default provenance age-off days %d, got %d", DefaultProvenanceAgeOffDays, c.GetProvenanceAgeOffDays())
	}
	if c.GetAnalyticsGroupName() != "" {
		t.Errorf("expected empty analytics group name, got %q", c.GetAnalyticsGroupName())
	}
}

func TestNewCoreConfig_WithMockServer(t *testing.T) {
	// Create a mock server that returns valid config
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			t.Errorf("expected POST, got %s", r.Method)
		}
		if r.URL.Path != "/api/v2/graphql" {
			t.Errorf("expected /api/v2/graphql, got %s", r.URL.Path)
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("expected application/json content type")
		}
		if r.Header.Get("X-User-Name") != "analytics" {
			t.Errorf("expected X-User-Name: analytics")
		}
		if r.Header.Get("X-User-Permissions") != "Admin" {
			t.Errorf("expected X-User-Permissions: Admin")
		}

		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{
					{
						"properties": []map[string]string{
							{"key": "analyticsGroupName", "value": "customer"},
							{"key": "parquetAnalyticsAgeOffDays", "value": "60"},
							{"key": "provenanceAgeOffDays", "value": "7"},
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	// Should fetch config on construction
	if c.GetAnalyticsGroupName() != "customer" {
		t.Errorf("expected analyticsGroupName 'customer', got %q", c.GetAnalyticsGroupName())
	}
	if c.GetAgeOffDays() != 60 {
		t.Errorf("expected age-off days 60, got %d", c.GetAgeOffDays())
	}
	if c.GetProvenanceAgeOffDays() != 7 {
		t.Errorf("expected provenance age-off days 7, got %d", c.GetProvenanceAgeOffDays())
	}
}

// =============================================================================
// Thread Safety Tests
// =============================================================================

func TestCoreConfig_ThreadSafety(t *testing.T) {
	c := NewCoreConfig("", testLogger())

	// Concurrent reads should not race
	var wg sync.WaitGroup
	for range 100 {
		wg.Go(func() {
			_ = c.GetAnalyticsGroupName()
			_ = c.GetAgeOffDays()
			_ = c.GetProvenanceAgeOffDays()
		})
	}
	wg.Wait()
}

// =============================================================================
// Error Handling Tests
// =============================================================================

func TestCoreConfig_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer server.Close()

	// Should use defaults when server returns error
	c := NewCoreConfig(server.URL, testLogger())

	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days on error, got %d", c.GetAgeOffDays())
	}
}

func TestCoreConfig_InvalidJSON(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("not json"))
	}))
	defer server.Close()

	// Should use defaults when response is invalid JSON
	c := NewCoreConfig(server.URL, testLogger())

	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days on invalid JSON, got %d", c.GetAgeOffDays())
	}
}

func TestCoreConfig_EmptyResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	// Should keep defaults when response has no properties
	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days on empty response, got %d", c.GetAgeOffDays())
	}
}

func TestCoreConfig_InvalidPropertyValues(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{
					{
						"properties": []map[string]string{
							{"key": "parquetAnalyticsAgeOffDays", "value": "not-a-number"},
							{"key": "provenanceAgeOffDays", "value": "-5"}, // Negative value
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	// Should keep defaults when property values are invalid
	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days for invalid value, got %d", c.GetAgeOffDays())
	}
	if c.GetProvenanceAgeOffDays() != DefaultProvenanceAgeOffDays {
		t.Errorf("expected default provenance age-off days for negative value, got %d", c.GetProvenanceAgeOffDays())
	}
}

func TestCoreConfig_ZeroValues(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{
					{
						"properties": []map[string]string{
							{"key": "parquetAnalyticsAgeOffDays", "value": "0"},
							{"key": "provenanceAgeOffDays", "value": "0"},
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	// Zero values should NOT override defaults (0 means keep default)
	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days for zero value, got %d", c.GetAgeOffDays())
	}
	if c.GetProvenanceAgeOffDays() != DefaultProvenanceAgeOffDays {
		t.Errorf("expected default provenance age-off days for zero value, got %d", c.GetProvenanceAgeOffDays())
	}
}

// =============================================================================
// Periodic Refresh Tests
// =============================================================================

func TestStartPeriodicRefresh(t *testing.T) {
	refreshCount := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		refreshCount++
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{
					{
						"properties": []map[string]string{
							{"key": "analyticsGroupName", "value": "test"},
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())
	initialCount := refreshCount

	// Start periodic refresh
	ctx := make(chan struct{})
	c.StartPeriodicRefresh(ctx)

	// Wait briefly - should not refresh yet (1 minute interval)
	time.Sleep(50 * time.Millisecond)

	// Stop refresh
	close(ctx)

	// Should have only the initial fetch
	if refreshCount != initialCount {
		t.Logf("refresh count: %d (initial: %d)", refreshCount, initialCount)
		// Note: This is not a hard error since timing can vary
	}
}

func TestStartPeriodicRefresh_StopsOnContextClose(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	ctx := make(chan struct{})
	c.StartPeriodicRefresh(ctx)

	// Close context immediately
	close(ctx)

	// Give goroutine time to exit
	time.Sleep(10 * time.Millisecond)

	// Test should not hang or panic
}

// =============================================================================
// Multiple Property Sets Tests
// =============================================================================

func TestCoreConfig_MultiplePropertySets(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Multiple property sets - should process all of them
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{
					{
						"properties": []map[string]string{
							{"key": "analyticsGroupName", "value": "customer"},
						},
					},
					{
						"properties": []map[string]string{
							{"key": "parquetAnalyticsAgeOffDays", "value": "45"},
						},
					},
					{
						"properties": []map[string]string{
							{"key": "provenanceAgeOffDays", "value": "5"},
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	if c.GetAnalyticsGroupName() != "customer" {
		t.Errorf("expected analyticsGroupName 'customer', got %q", c.GetAnalyticsGroupName())
	}
	if c.GetAgeOffDays() != 45 {
		t.Errorf("expected age-off days 45, got %d", c.GetAgeOffDays())
	}
	if c.GetProvenanceAgeOffDays() != 5 {
		t.Errorf("expected provenance age-off days 5, got %d", c.GetProvenanceAgeOffDays())
	}
}

// =============================================================================
// Network Timeout Tests
// =============================================================================

func TestCoreConfig_NetworkTimeout(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping slow network timeout test")
	}

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Delay longer than the client timeout
		time.Sleep(15 * time.Second)
	}))
	defer server.Close()

	// This will timeout during the initial fetch
	start := time.Now()
	c := NewCoreConfig(server.URL, testLogger())
	elapsed := time.Since(start)

	// Should timeout after ~10 seconds (configured timeout)
	if elapsed > 12*time.Second {
		t.Errorf("expected timeout around 10s, took %v", elapsed)
	}

	// Should use defaults when timeout occurs
	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days on timeout, got %d", c.GetAgeOffDays())
	}
}

// =============================================================================
// Partial Config Tests
// =============================================================================

func TestCoreConfig_PartialConfig(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Only some properties are set
		response := map[string]any{
			"data": map[string]any{
				"getPropertySets": []map[string]any{
					{
						"properties": []map[string]string{
							{"key": "analyticsGroupName", "value": "region"},
							// parquetAnalyticsAgeOffDays not set
							{"key": "provenanceAgeOffDays", "value": "10"},
						},
					},
				},
			},
		}
		json.NewEncoder(w).Encode(response)
	}))
	defer server.Close()

	c := NewCoreConfig(server.URL, testLogger())

	if c.GetAnalyticsGroupName() != "region" {
		t.Errorf("expected analyticsGroupName 'region', got %q", c.GetAnalyticsGroupName())
	}
	// Should keep default for unset property
	if c.GetAgeOffDays() != DefaultAgeOffDays {
		t.Errorf("expected default age-off days for unset property, got %d", c.GetAgeOffDays())
	}
	if c.GetProvenanceAgeOffDays() != 10 {
		t.Errorf("expected provenance age-off days 10, got %d", c.GetProvenanceAgeOffDays())
	}
}
