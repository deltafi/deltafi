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
 * ABOUTME: Tests for the analytics query service.
 * ABOUTME: Verifies query building, filtering, and data retrieval from parquet files.
 */
package query

import (
	"log/slog"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/parquet-go/parquet-go"

	"deltafi.org/deltafi-analytics/internal/schema"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

// mockConfigProvider implements ConfigProvider for tests
type mockConfigProvider struct {
	groupName string
}

func (m *mockConfigProvider) GetAnalyticsGroupName() string {
	return m.groupName
}

func testConfigProvider() *mockConfigProvider {
	return &mockConfigProvider{groupName: ""}
}

func setupTestData(t *testing.T, dataDir string) {
	t.Helper()

	// Create aggregated directory with hourly structure (aggregated/YYYYMMDD/HH.parquet)
	aggregatedDir := filepath.Join(dataDir, "aggregated", "20251125")
	if err := os.MkdirAll(aggregatedDir, 0755); err != nil {
		t.Fatal(err)
	}

	// Create aggregated events (pre-compacted with annotations)
	// Include IngressType for filter testing
	bucket := time.Date(2025, 11, 25, 10, 0, 0, 0, time.UTC)
	events := []schema.AggregatedEvent{
		{Bucket: bucket, DataSource: "source-a", EventType: "INGRESS", FlowName: "flow-1", ActionName: "ingest", IngressType: "DATA_SOURCE", Annotations: map[string]string{"customer": "acme", "region": "us-east"}, EventCount: 1, TotalBytes: 1000, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "source-a", EventType: "INGRESS", FlowName: "flow-1", ActionName: "ingest", IngressType: "DATA_SOURCE", Annotations: map[string]string{"customer": "globex"}, EventCount: 1, TotalBytes: 2000, TotalFileCount: 2},
		{Bucket: bucket, DataSource: "source-b", EventType: "EGRESS", FlowName: "flow-2", ActionName: "egress", IngressType: "CHILD", Annotations: map[string]string{"customer": "acme"}, EventCount: 1, TotalBytes: 500, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "source-a", EventType: "ERROR", FlowName: "flow-1", ActionName: "transform", IngressType: "DATA_SOURCE", Annotations: map[string]string{"customer": "acme"}, EventCount: 1, TotalBytes: 0, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "source-b", EventType: "FILTER", FlowName: "flow-2", ActionName: "filter", IngressType: "CHILD", EventCount: 1, TotalBytes: 0, TotalFileCount: 1}, // no annotations
		{Bucket: bucket, DataSource: "source-c", EventType: "INGRESS", FlowName: "flow-3", ActionName: "ingest", IngressType: "SURVEY", Annotations: map[string]string{"env": "prod"}, EventCount: 1, TotalBytes: 100, TotalFileCount: 1},
	}

	// Hourly file: 10.parquet (for 10:00 hour)
	aggregatedFile := filepath.Join(aggregatedDir, "10.parquet")
	f, err := os.Create(aggregatedFile)
	if err != nil {
		t.Fatal(err)
	}
	writer := parquet.NewGenericWriter[schema.AggregatedEvent](f, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(events); err != nil {
		t.Fatal(err)
	}
	writer.Close()
	f.Close()
}

func TestNewService(t *testing.T) {
	dataDir := t.TempDir()

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatalf("NewService failed: %v", err)
	}
	defer svc.Close()

	if svc.DataDir() != dataDir {
		t.Errorf("DataDir() = %s, want %s", svc.DataDir(), dataDir)
	}
}

func TestHasData(t *testing.T) {
	dataDir := t.TempDir()

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	// Empty directory should have no data
	if svc.HasData() {
		t.Error("HasData() should return false for empty directory")
	}

	// Add test data
	setupTestData(t, dataDir)

	if !svc.HasData() {
		t.Error("HasData() should return true after adding data")
	}
}

func TestGetDataStats(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	stats := svc.GetDataStats()

	aggregatedFiles, ok := stats["aggregated_files"].(int)
	if !ok || aggregatedFiles != 1 {
		t.Errorf("aggregated_files = %v, want 1", stats["aggregated_files"])
	}
}

func TestGetDistinctValues(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	values, err := svc.GetDistinctValues("data_source", timeFrom, timeTo, true, nil) // filtered=true to test parquet scan
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 3 {
		t.Errorf("got %d data sources, want 3: %v", len(values), values)
	}

	// Check that all sources are present
	expected := map[string]bool{"source-a": false, "source-b": false, "source-c": false}
	for _, v := range values {
		expected[v] = true
	}
	for k, found := range expected {
		if !found {
			t.Errorf("missing expected data source: %s", k)
		}
	}
}

func TestGetDistinctValues_FilterByIngressType(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to DATA_SOURCE ingress type - should only get source-a
	values, err := svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"DATA_SOURCE"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 1 || values[0] != "source-a" {
		t.Errorf("expected [source-a] for DATA_SOURCE filter, got %v", values)
	}

	// Filter to CHILD ingress type - should only get source-b
	values, err = svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"CHILD"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 1 || values[0] != "source-b" {
		t.Errorf("expected [source-b] for CHILD filter, got %v", values)
	}

	// Filter to SURVEY ingress type - should only get source-c
	values, err = svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"SURVEY"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 1 || values[0] != "source-c" {
		t.Errorf("expected [source-c] for SURVEY filter, got %v", values)
	}

	// Filter to multiple ingress types
	values, err = svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"DATA_SOURCE", "CHILD"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 2 {
		t.Errorf("expected 2 data sources for DATA_SOURCE+CHILD filter, got %v", values)
	}
}

func TestGetDistinctValues_AllIgnored(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// "All" and "$__all" should be ignored (returns all data sources)
	values, err := svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"All"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 3 {
		t.Errorf("expected 3 data sources when 'All' is passed, got %v", values)
	}

	values, err = svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"$__all"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 3 {
		t.Errorf("expected 3 data sources when '$__all' is passed, got %v", values)
	}

	// Empty string should also be treated as All (Grafana sends this)
	values, err = svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{""})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 3 {
		t.Errorf("expected 3 data sources when empty string is passed, got %v", values)
	}
}

func TestGetDistinctValues_GrafanaMultiValueFormat(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Grafana multi-value format {a,b} should be expanded and used as filter
	// {DATA_SOURCE,CHILD} should filter to source-a and source-b (not source-c which is SURVEY)
	values, err := svc.GetDistinctValues("data_source", timeFrom, timeTo, true, []string{"{DATA_SOURCE,CHILD}"})
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 2 {
		t.Errorf("expected 2 data sources for {DATA_SOURCE,CHILD} filter, got %v", values)
	}

	// Verify source-c (SURVEY) is not included
	for _, v := range values {
		if v == "source-c" {
			t.Errorf("source-c should not be included when filtering to DATA_SOURCE,CHILD: got %v", values)
		}
	}
}

func TestGetAnnotationKeys(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, false, true, nil, nil) // forGroup=false, filtered=true
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// Should have customer, region, env + "None" option = 4
	if len(keys) != 4 {
		t.Errorf("got %d annotation keys, want 4 (None, customer, env, region): %v", len(keys), keys)
	}

	// "None" should be first when forGroup=false
	if keys[0] != "None" {
		t.Errorf("expected 'None' first when forGroup=false, got %v", keys)
	}
}

func TestGetAnnotationKeys_ForGroupTrue(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	// Use a config provider with a configured group name that exists in the data
	configWithGroup := &mockConfigProvider{groupName: "customer"}
	svc, err := NewService(dataDir, configWithGroup, testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, true, true, nil, nil) // forGroup=true
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// "customer" should be first (configured key), then "None", then others
	if len(keys) < 2 {
		t.Fatalf("expected at least 2 keys, got %d: %v", len(keys), keys)
	}
	if keys[0] != "customer" {
		t.Errorf("expected 'customer' first when forGroup=true and configured, got %v", keys)
	}
	if keys[1] != "None" {
		t.Errorf("expected 'None' second, got %v", keys)
	}
}

func TestGetAnnotationKeys_ForGroupTrue_KeyNotInData(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	// Use a config provider with a configured group name that does NOT exist in the data
	configWithGroup := &mockConfigProvider{groupName: "nonexistent"}
	svc, err := NewService(dataDir, configWithGroup, testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, true, true, nil, nil) // forGroup=true
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// "None" should be first since configured key doesn't exist in data
	if len(keys) < 1 {
		t.Fatalf("expected at least 1 key, got %d", len(keys))
	}
	if keys[0] != "None" {
		t.Errorf("expected 'None' first when configured key not in data, got %v", keys)
	}
}

func TestGetAnnotationKeys_ForGroupTrue_EmptyConfig(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	// Empty config (no group name configured)
	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, true, true, nil, nil) // forGroup=true
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// "None" should be first since no key is configured
	if len(keys) < 1 {
		t.Fatalf("expected at least 1 key, got %d", len(keys))
	}
	if keys[0] != "None" {
		t.Errorf("expected 'None' first when no group name configured, got %v", keys)
	}
}

func TestGetAnnotationKeys_FilterByDataSource(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to source-a: should get customer and region (not env which is only on source-c)
	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, false, true, []string{"source-a"}, nil)
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// Should have None, customer, region (not env)
	hasCustomer := false
	hasRegion := false
	hasEnv := false
	for _, k := range keys {
		switch k {
		case "customer":
			hasCustomer = true
		case "region":
			hasRegion = true
		case "env":
			hasEnv = true
		}
	}

	if !hasCustomer || !hasRegion {
		t.Errorf("expected customer and region for source-a filter, got %v", keys)
	}
	if hasEnv {
		t.Errorf("did not expect 'env' for source-a filter (it's only on source-c), got %v", keys)
	}
}

func TestGetAnnotationKeys_FilterByIngressType(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to SURVEY ingress type: should only get env (from source-c)
	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, false, true, nil, []string{"SURVEY"})
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// Should have None and env only
	if len(keys) != 2 {
		t.Errorf("expected 2 keys (None, env) for SURVEY filter, got %v", keys)
	}

	hasEnv := false
	for _, k := range keys {
		if k == "env" {
			hasEnv = true
		}
	}
	if !hasEnv {
		t.Errorf("expected 'env' for SURVEY filter, got %v", keys)
	}
}

func TestGetAnnotationKeys_FilterByBoth(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to source-a AND DATA_SOURCE: should get customer and region
	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, false, true, []string{"source-a"}, []string{"DATA_SOURCE"})
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// Should have None, customer, region
	if len(keys) != 3 {
		t.Errorf("expected 3 keys (None, customer, region) for combined filter, got %v", keys)
	}
}

func TestGetAnnotationKeys_NoData(t *testing.T) {
	dataDir := t.TempDir()
	// Don't set up test data - empty directory

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, true, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// Should return at least ["None"] even with no data
	if len(keys) != 1 || keys[0] != "None" {
		t.Errorf("expected ['None'] with no data, got %v", keys)
	}
}

func TestGetAnnotationValues(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	values, err := svc.GetAnnotationValues("customer", timeFrom, timeTo, true, nil, nil) // filtered=true to test parquet scan
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	if len(values) != 2 {
		t.Errorf("got %d customer values, want 2 (acme, globex)", len(values))
	}
}

func TestGetAnnotationValues_FilterByDataSource(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to source-b: should only get acme (source-b has customer=acme)
	values, err := svc.GetAnnotationValues("customer", timeFrom, timeTo, true, []string{"source-b"}, nil)
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	if len(values) != 1 || values[0] != "acme" {
		t.Errorf("expected [acme] for source-b filter, got %v", values)
	}
}

func TestGetAnnotationValues_FilterByIngressType(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to SURVEY ingress type: should only get prod (from source-c which has env=prod)
	values, err := svc.GetAnnotationValues("env", timeFrom, timeTo, true, nil, []string{"SURVEY"})
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	if len(values) != 1 || values[0] != "prod" {
		t.Errorf("expected [prod] for SURVEY filter, got %v", values)
	}

	// Filter to DATA_SOURCE ingress type: should not find env at all (it's only on SURVEY)
	values, err = svc.GetAnnotationValues("env", timeFrom, timeTo, true, nil, []string{"DATA_SOURCE"})
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	if len(values) != 0 {
		t.Errorf("expected no values for env with DATA_SOURCE filter, got %v", values)
	}
}

func TestGetAnnotationValues_FilterByBoth(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Filter to source-a AND DATA_SOURCE: should get acme and globex
	values, err := svc.GetAnnotationValues("customer", timeFrom, timeTo, true, []string{"source-a"}, []string{"DATA_SOURCE"})
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	if len(values) != 2 {
		t.Errorf("expected 2 values (acme, globex) for combined filter, got %v", values)
	}
}

func TestQueryAnalytics_Basic(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000, // 5 minutes
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	if len(results) == 0 {
		t.Error("QueryAnalytics returned no results")
	}

	// Verify we have data from both sources
	foundSourceA := false
	foundSourceB := false
	for _, row := range results {
		if row.DataSource == "source-a" {
			foundSourceA = true
		}
		if row.DataSource == "source-b" {
			foundSourceB = true
		}
	}

	if !foundSourceA {
		t.Error("missing results for source-a")
	}
	if !foundSourceB {
		t.Error("missing results for source-b")
	}
}

func TestQueryAnalytics_FilterByDataSource(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:    time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:      time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs:  300000,
		DataSources: []string{"source-a"},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	for _, row := range results {
		if row.DataSource != "source-a" {
			t.Errorf("got data source %s, want only source-a", row.DataSource)
		}
	}
}

func TestQueryAnalytics_FilterByEventType(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		EventType:  "ERRORS",
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should only have error events
	totalErrors := int64(0)
	for _, row := range results {
		totalErrors += row.ErrorFiles
		if row.IngressFiles > 0 || row.EgressFiles > 0 || row.FilterFiles > 0 {
			t.Error("expected only error events when EventType=ERRORS")
		}
	}

	if totalErrors == 0 {
		t.Error("expected at least one error file")
	}
}

func TestQueryAnalytics_WithGroupByAnnotation(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:          time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:            time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs:        300000,
		GroupByAnnotation: "customer",
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should have annotation values including "not present"
	foundAcme := false
	foundGlobex := false
	foundNotPresent := false

	for _, row := range results {
		switch row.AnnotationValue {
		case "acme":
			foundAcme = true
		case "globex":
			foundGlobex = true
		case "not present":
			foundNotPresent = true
		}
	}

	if !foundAcme {
		t.Error("expected to find customer=acme")
	}
	if !foundGlobex {
		t.Error("expected to find customer=globex")
	}
	if !foundNotPresent {
		t.Error("expected to find 'not present' for events without customer annotation")
	}
}

func TestQueryAnalytics_FilterByFlowName(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		FlowNames:  []string{"flow-1"},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// FlowNames filter is applied in the query WHERE clause
	// We just verify the query succeeded and returned results
	if len(results) == 0 {
		t.Error("QueryAnalytics with FlowNames filter returned no results")
	}
}

func TestQueryAnalytics_FilterByMultipleAnnotations(t *testing.T) {
	dataDir := t.TempDir()
	setupTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	// Filter to only events where customer=acme
	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"acme"},
		},
		GroupByAnnotation: "region",
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	if len(results) == 0 {
		t.Error("expected results for customer=acme filter")
	}

	// Verify we got data - results should be filtered to only acme customers
	// did-1 (acme), did-3 (acme), did-4 (acme) should be included
	// did-2 (globex), did-5 (no customer) should be excluded
	totalIngress := int64(0)
	totalEgress := int64(0)
	totalErrors := int64(0)
	for _, row := range results {
		totalIngress += row.IngressFiles
		totalEgress += row.EgressFiles
		totalErrors += row.ErrorFiles
	}

	// did-1: INGRESS (1 file), did-3: EGRESS (1 file), did-4: ERROR (1 file)
	if totalIngress != 1 {
		t.Errorf("expected 1 ingress file (did-1 only), got %d", totalIngress)
	}
	if totalEgress != 1 {
		t.Errorf("expected 1 egress file (did-3), got %d", totalEgress)
	}
	if totalErrors != 1 {
		t.Errorf("expected 1 error file (did-4), got %d", totalErrors)
	}
}

func TestContainsAll(t *testing.T) {
	tests := []struct {
		values []string
		want   bool
	}{
		{[]string{"All"}, true},
		{[]string{"'All'"}, true},
		{[]string{"foo", "All", "bar"}, true},
		{[]string{"foo", "bar"}, false},
		{[]string{}, false},
		// Grafana $__all format
		{[]string{"$__all"}, true},
		// Empty string (Grafana sends this when All is selected on some variables)
		{[]string{""}, true},
		// Multi-value format should NOT be treated as All - it's actual filter values
		{[]string{"{CHILD,DATA_SOURCE,SURVEY}"}, false},
		{[]string{"{a,b}"}, false},
		// Actual filter values
		{[]string{"DATA_SOURCE"}, false},
		{[]string{"CHILD", "SURVEY"}, false},
	}

	for _, tt := range tests {
		got := containsAll(tt.values)
		if got != tt.want {
			t.Errorf("containsAll(%v) = %v, want %v", tt.values, got, tt.want)
		}
	}
}

func TestNormalizeFilterValues(t *testing.T) {
	tests := []struct {
		input []string
		want  []string
	}{
		// Empty and All markers are filtered out
		{[]string{""}, nil},
		{[]string{"All"}, nil},
		{[]string{"$__all"}, nil},
		// Regular values pass through
		{[]string{"DATA_SOURCE"}, []string{"DATA_SOURCE"}},
		{[]string{"CHILD", "SURVEY"}, []string{"CHILD", "SURVEY"}},
		// Multi-value format is expanded
		{[]string{"{CHILD,DATA_SOURCE}"}, []string{"CHILD", "DATA_SOURCE"}},
		{[]string{"{a,b,c}"}, []string{"a", "b", "c"}},
		// Mixed input
		{[]string{"", "{a,b}", "c"}, []string{"a", "b", "c"}},
	}

	for _, tt := range tests {
		got := normalizeFilterValues(tt.input)
		if len(got) != len(tt.want) {
			t.Errorf("normalizeFilterValues(%v) = %v, want %v", tt.input, got, tt.want)
			continue
		}
		for i := range got {
			if got[i] != tt.want[i] {
				t.Errorf("normalizeFilterValues(%v) = %v, want %v", tt.input, got, tt.want)
				break
			}
		}
	}
}

func TestQuoteStrings(t *testing.T) {
	input := []string{"foo", "'bar'", "\"baz\""}
	want := []string{"'foo'", "'bar'", "'baz'"}

	got := quoteStrings(input)

	for i, v := range got {
		if v != want[i] {
			t.Errorf("quoteStrings[%d] = %s, want %s", i, v, want[i])
		}
	}
}

// =============================================================================
// MAP/Annotation Access Tests
// These tests specifically verify that annotation values are correctly extracted
// as full strings, not individual characters. They exercise the annotations['key']
// access pattern in DuckDB queries.
// =============================================================================

// setupAnnotationTestData creates test data with diverse annotation patterns
func setupAnnotationTestData(t *testing.T, dataDir string) {
	t.Helper()

	aggregatedDir := filepath.Join(dataDir, "aggregated", "20251125")
	if err := os.MkdirAll(aggregatedDir, 0755); err != nil {
		t.Fatal(err)
	}

	bucket := time.Date(2025, 11, 25, 10, 0, 0, 0, time.UTC)

	// Create events with various annotation patterns to test MAP access
	events := []schema.AggregatedEvent{
		// Events with multiple annotations
		{Bucket: bucket, DataSource: "src-alpha", EventType: "INGRESS", FlowName: "flow-1", ActionName: "ingest", IngressType: "DATA_SOURCE",
			Annotations: map[string]string{"customer": "acme-corp", "region": "us-east-1", "env": "production"},
			EventCount: 1, TotalBytes: 1000, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "src-alpha", EventType: "EGRESS", FlowName: "flow-1", ActionName: "egress", IngressType: "DATA_SOURCE",
			Annotations: map[string]string{"customer": "acme-corp", "region": "us-west-2"},
			EventCount: 1, TotalBytes: 900, TotalFileCount: 1},

		// Events with different customer
		{Bucket: bucket, DataSource: "src-beta", EventType: "INGRESS", FlowName: "flow-2", ActionName: "ingest", IngressType: "CHILD",
			Annotations: map[string]string{"customer": "globex-inc", "region": "eu-central-1"},
			EventCount: 1, TotalBytes: 2000, TotalFileCount: 2},
		{Bucket: bucket, DataSource: "src-beta", EventType: "ERROR", FlowName: "flow-2", ActionName: "transform", IngressType: "CHILD",
			Annotations: map[string]string{"customer": "globex-inc"},
			EventCount: 1, TotalBytes: 0, TotalFileCount: 1},

		// Events with long annotation values (test that we get full strings, not chars)
		{Bucket: bucket, DataSource: "src-gamma", EventType: "INGRESS", FlowName: "flow-3", ActionName: "ingest", IngressType: "SURVEY",
			Annotations: map[string]string{"customer": "wayne-enterprises-international", "department": "research-and-development"},
			EventCount: 1, TotalBytes: 500, TotalFileCount: 1},

		// Events with NO annotations (test "not present" handling)
		{Bucket: bucket, DataSource: "src-delta", EventType: "INGRESS", FlowName: "flow-4", ActionName: "ingest", IngressType: "DATA_SOURCE",
			Annotations: nil,
			EventCount: 1, TotalBytes: 100, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "src-delta", EventType: "FILTER", FlowName: "flow-4", ActionName: "filter", IngressType: "DATA_SOURCE",
			EventCount: 1, TotalBytes: 0, TotalFileCount: 1},

		// Events with single-char annotation values (edge case)
		{Bucket: bucket, DataSource: "src-epsilon", EventType: "INGRESS", FlowName: "flow-5", ActionName: "ingest", IngressType: "DATA_SOURCE",
			Annotations: map[string]string{"tier": "A", "priority": "1"},
			EventCount: 1, TotalBytes: 50, TotalFileCount: 1},

		// Events with special characters in annotation values
		{Bucket: bucket, DataSource: "src-zeta", EventType: "INGRESS", FlowName: "flow-6", ActionName: "ingest", IngressType: "DATA_SOURCE",
			Annotations: map[string]string{"customer": "o'reilly-media", "tag": "test-value"},
			EventCount: 1, TotalBytes: 75, TotalFileCount: 1},
	}

	aggregatedFile := filepath.Join(aggregatedDir, "10.parquet")
	f, err := os.Create(aggregatedFile)
	if err != nil {
		t.Fatal(err)
	}
	writer := parquet.NewGenericWriter[schema.AggregatedEvent](f, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(events); err != nil {
		t.Fatal(err)
	}
	writer.Close()
	f.Close()
}

func TestAnnotationValueExtraction_FullStrings(t *testing.T) {
	// This test verifies that annotation values are returned as complete strings,
	// not individual characters. This is critical for DuckDB MAP access compatibility.
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Get annotation values for "customer" key
	values, err := svc.GetAnnotationValues("customer", timeFrom, timeTo, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	// Verify we get complete customer names, not just first characters
	expectedCustomers := map[string]bool{
		"acme-corp":                      false,
		"globex-inc":                     false,
		"wayne-enterprises-international": false,
		"o'reilly-media":                 false,
	}

	for _, v := range values {
		if _, ok := expectedCustomers[v]; ok {
			expectedCustomers[v] = true
		} else {
			// Check if we got a single character (the bug we're testing for)
			if len(v) == 1 {
				t.Errorf("Got single character '%s' instead of full annotation value - MAP access bug!", v)
			}
		}
	}

	for customer, found := range expectedCustomers {
		if !found {
			t.Errorf("Missing expected customer value: %s", customer)
		}
	}
}

func TestAnnotationValueExtraction_LongValues(t *testing.T) {
	// Specifically test that long annotation values are fully extracted
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	values, err := svc.GetAnnotationValues("department", timeFrom, timeTo, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	// Should get "research-and-development", not "r"
	if len(values) != 1 {
		t.Fatalf("Expected 1 department value, got %d: %v", len(values), values)
	}
	if values[0] != "research-and-development" {
		t.Errorf("Expected 'research-and-development', got '%s' (length %d)", values[0], len(values[0]))
	}
}

func TestAnnotationValueExtraction_SingleCharValues(t *testing.T) {
	// Test that single-character annotation values work correctly
	// This distinguishes between "correctly got single char" vs "bug truncated to single char"
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// tier has value "A" and priority has value "1"
	tierValues, err := svc.GetAnnotationValues("tier", timeFrom, timeTo, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationValues for tier failed: %v", err)
	}
	if len(tierValues) != 1 || tierValues[0] != "A" {
		t.Errorf("Expected tier=['A'], got %v", tierValues)
	}

	priorityValues, err := svc.GetAnnotationValues("priority", timeFrom, timeTo, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationValues for priority failed: %v", err)
	}
	if len(priorityValues) != 1 || priorityValues[0] != "1" {
		t.Errorf("Expected priority=['1'], got %v", priorityValues)
	}
}

func TestQueryAnalytics_GroupByAnnotation_FullValues(t *testing.T) {
	// Verify that GroupByAnnotation returns complete annotation values in results
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:          time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:            time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs:        300000,
		GroupByAnnotation: "customer",
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Collect all unique annotation values from results
	annotationValues := make(map[string]bool)
	for _, row := range results {
		if row.AnnotationValue != "" {
			annotationValues[row.AnnotationValue] = true
		}
	}

	// Verify we have complete customer names
	expectedValues := []string{"acme-corp", "globex-inc", "wayne-enterprises-international", "o'reilly-media", "not present"}
	for _, expected := range expectedValues {
		if !annotationValues[expected] {
			t.Errorf("Missing expected annotation value in results: %s", expected)
		}
	}

	// Verify we DON'T have single-character truncated values (unless they're legitimate like "A")
	for val := range annotationValues {
		if len(val) == 1 && val != "A" && val != "1" {
			t.Errorf("Suspicious single-character annotation value '%s' - possible MAP access bug", val)
		}
	}
}

func TestQueryAnalytics_DataSourceWithAnnotation_FullValues(t *testing.T) {
	// Test the combined "data_source - annotation_value" format
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:          time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:            time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs:        300000,
		GroupByAnnotation: "customer",
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// The DataSource field should be "source - customer_value" format
	foundExpected := map[string]bool{
		"src-alpha - acme-corp":                      false,
		"src-beta - globex-inc":                      false,
		"src-gamma - wayne-enterprises-international": false,
		"src-delta - not present":                    false,
	}

	for _, row := range results {
		if _, ok := foundExpected[row.DataSource]; ok {
			foundExpected[row.DataSource] = true
		}
	}

	for expected, found := range foundExpected {
		if !found {
			t.Errorf("Missing expected data_source format: %s", expected)
		}
	}
}

func TestQueryAnalytics_FilterByAnnotation_ExactMatch(t *testing.T) {
	// Test filtering by exact annotation value
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"acme-corp"},
		},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should only have results from src-alpha (acme-corp customer)
	for _, row := range results {
		if row.DataSource != "src-alpha" {
			t.Errorf("Filter by customer=acme-corp should only return src-alpha, got %s", row.DataSource)
		}
	}

	// Verify we have both INGRESS and EGRESS from src-alpha
	var totalIngress, totalEgress int64
	for _, row := range results {
		totalIngress += row.IngressFiles
		totalEgress += row.EgressFiles
	}
	if totalIngress != 1 {
		t.Errorf("Expected 1 ingress file for acme-corp, got %d", totalIngress)
	}
	if totalEgress != 1 {
		t.Errorf("Expected 1 egress file for acme-corp, got %d", totalEgress)
	}
}

func TestQueryAnalytics_FilterByAnnotation_MultipleValues(t *testing.T) {
	// Test filtering by multiple annotation values (IN clause)
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"acme-corp", "globex-inc"},
		},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should have results from src-alpha AND src-beta
	foundAlpha := false
	foundBeta := false
	for _, row := range results {
		if row.DataSource == "src-alpha" {
			foundAlpha = true
		}
		if row.DataSource == "src-beta" {
			foundBeta = true
		}
		// Should NOT have gamma (wayne-enterprises), delta (no annotations), etc.
		if row.DataSource == "src-gamma" || row.DataSource == "src-delta" {
			t.Errorf("Unexpected data source in filtered results: %s", row.DataSource)
		}
	}

	if !foundAlpha {
		t.Error("Missing src-alpha (acme-corp) in results")
	}
	if !foundBeta {
		t.Error("Missing src-beta (globex-inc) in results")
	}
}

func TestQueryAnalytics_FilterByAnnotation_NotPresent(t *testing.T) {
	// Test filtering for events WITHOUT a specific annotation
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"not present"},
		},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should have results from sources that DON'T have a customer annotation:
	// - src-delta (no annotations at all)
	// - src-epsilon (has tier/priority but no customer)
	allowedSources := map[string]bool{
		"src-delta":   true,
		"src-epsilon": true,
	}
	for _, row := range results {
		if !allowedSources[row.DataSource] {
			t.Errorf("Filter by customer='not present' should only return sources without customer, got %s", row.DataSource)
		}
	}

	// Verify we have results from both expected sources
	foundDelta := false
	foundEpsilon := false
	for _, row := range results {
		if row.DataSource == "src-delta" {
			foundDelta = true
		}
		if row.DataSource == "src-epsilon" {
			foundEpsilon = true
		}
	}
	if !foundDelta {
		t.Error("Missing src-delta (no annotations) in 'not present' filter results")
	}
	if !foundEpsilon {
		t.Error("Missing src-epsilon (has other annotations but no customer) in 'not present' filter results")
	}
}

func TestQueryAnalytics_FilterByAnnotation_NotPresentPlusValues(t *testing.T) {
	// Test filtering for "not present" OR specific value
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"not present", "acme-corp"},
		},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should have results from src-alpha (acme-corp) AND src-delta (no customer)
	foundAlpha := false
	foundDelta := false
	for _, row := range results {
		if row.DataSource == "src-alpha" {
			foundAlpha = true
		}
		if row.DataSource == "src-delta" {
			foundDelta = true
		}
	}

	if !foundAlpha {
		t.Error("Missing src-alpha (acme-corp) in results")
	}
	if !foundDelta {
		t.Error("Missing src-delta (no customer annotation) in results")
	}
}

func TestQueryAnalytics_FilterByMultipleAnnotationKeys(t *testing.T) {
	// Test filtering by multiple annotation keys simultaneously
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"acme-corp"},
			"region":   {"us-east-1"},
		},
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should only match the first event (src-alpha, INGRESS, customer=acme-corp, region=us-east-1)
	// The EGRESS event has region=us-west-2 so it shouldn't match
	var totalIngress, totalEgress int64
	for _, row := range results {
		totalIngress += row.IngressFiles
		totalEgress += row.EgressFiles
	}

	if totalIngress != 1 {
		t.Errorf("Expected 1 ingress file matching both filters, got %d", totalIngress)
	}
	if totalEgress != 0 {
		t.Errorf("Expected 0 egress files (us-west-2 doesn't match us-east-1), got %d", totalEgress)
	}
}

func TestEventAnalysis_GroupByAnnotation(t *testing.T) {
	// Test QueryEventAnalysis with annotation-based groupBy
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := EventAnalysisRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		GroupBy:    "customer", // Group by annotation key
	}

	results, err := svc.QueryEventAnalysis(req, "ERROR")
	if err != nil {
		t.Fatalf("QueryEventAnalysis failed: %v", err)
	}

	// Should have error grouped by customer
	foundGlobex := false
	for _, row := range results {
		if row.GroupValue == "globex-inc" {
			foundGlobex = true
			if row.Count != 1 {
				t.Errorf("Expected 1 error for globex-inc, got %d", row.Count)
			}
		}
		// Verify we don't get truncated values
		if len(row.GroupValue) == 1 && row.GroupValue != "A" && row.GroupValue != "1" {
			t.Errorf("Suspicious single-char group value '%s' - possible MAP access bug", row.GroupValue)
		}
	}

	if !foundGlobex {
		t.Error("Missing globex-inc in error analysis results")
	}
}

func TestEventAnalysis_GroupByStandardField(t *testing.T) {
	// Test QueryEventAnalysis with standard field groupBy (not annotation)
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := EventAnalysisRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		GroupBy:    "data_source",
	}

	results, err := svc.QueryEventAnalysis(req, "INGRESS")
	if err != nil {
		t.Fatalf("QueryEventAnalysis failed: %v", err)
	}

	// Should have ingress events grouped by data_source
	foundSources := make(map[string]int64)
	for _, row := range results {
		foundSources[row.GroupValue] += row.Count
	}

	// Verify we have full data source names
	expectedSources := []string{"src-alpha", "src-beta", "src-gamma", "src-delta", "src-epsilon", "src-zeta"}
	for _, expected := range expectedSources {
		if _, ok := foundSources[expected]; !ok {
			t.Errorf("Missing data source %s in results", expected)
		}
	}
}

func TestEventAnalysis_FilterByAnnotation(t *testing.T) {
	// Test QueryEventAnalysis with annotation filter
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := EventAnalysisRequest{
		TimeFrom:   time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:     time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs: 300000,
		Annotations: map[string][]string{
			"customer": {"globex-inc"},
		},
		GroupBy: "action_name",
	}

	results, err := svc.QueryEventAnalysis(req, "ERROR")
	if err != nil {
		t.Fatalf("QueryEventAnalysis failed: %v", err)
	}

	// Should only have the transform error from globex-inc
	if len(results) != 1 {
		t.Fatalf("Expected 1 result for globex-inc errors, got %d", len(results))
	}
	if results[0].GroupValue != "transform" {
		t.Errorf("Expected action_name='transform', got '%s'", results[0].GroupValue)
	}
}

// =============================================================================
// Provenance Annotation Tests
// =============================================================================

func setupProvenanceTestData(t *testing.T, dataDir string) {
	t.Helper()

	// Create provenance directories
	rawDir := filepath.Join(dataDir, "provenance", "raw", "20251125", "10")
	if err := os.MkdirAll(rawDir, 0755); err != nil {
		t.Fatal(err)
	}

	created := time.Date(2025, 11, 25, 10, 0, 0, 0, time.UTC)
	completed := time.Date(2025, 11, 25, 10, 5, 0, 0, time.UTC)

	records := []schema.Provenance{
		{DID: "did-1", SystemName: "system-a", DataSource: "source-1", Filename: "file1.txt",
			Transforms: []string{"transform-1"}, DataSink: "sink-1", FinalState: "COMPLETE",
			Created: created, Completed: completed,
			Annotations: map[string]string{"customer": "acme-corp", "project": "alpha"}},
		{DID: "did-2", SystemName: "system-a", DataSource: "source-1", Filename: "file2.txt",
			Transforms: []string{"transform-1", "transform-2"}, DataSink: "sink-2", FinalState: "ERROR",
			Created: created, Completed: completed,
			Annotations: map[string]string{"customer": "globex-inc", "project": "beta"}},
		{DID: "did-3", SystemName: "system-b", DataSource: "source-2", Filename: "file3.txt",
			Transforms: []string{}, FinalState: "FILTERED",
			Created: created, Completed: completed,
			Annotations: map[string]string{"customer": "acme-corp"}},
		{DID: "did-4", SystemName: "system-b", DataSource: "source-2", Filename: "file4.txt",
			Transforms: []string{}, FinalState: "COMPLETE",
			Created: created, Completed: completed,
			Annotations: nil}, // No annotations
	}

	rawFile := filepath.Join(rawDir, "provenance.parquet")
	f, err := os.Create(rawFile)
	if err != nil {
		t.Fatal(err)
	}
	writer := parquet.NewGenericWriter[schema.Provenance](f, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(records); err != nil {
		t.Fatal(err)
	}
	writer.Close()
	f.Close()
}

func TestProvenanceStats_FilterByAnnotation(t *testing.T) {
	dataDir := t.TempDir()
	setupProvenanceTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := ProvenanceStatsRequest{
		AnnotationKey:   "customer",
		AnnotationValue: "acme-corp",
	}

	stats, err := svc.GetProvenanceStats(req)
	if err != nil {
		t.Fatalf("GetProvenanceStats failed: %v", err)
	}

	// Should have 2 records with customer=acme-corp (did-1 and did-3)
	if stats.TotalRecords != 2 {
		t.Errorf("Expected 2 records for customer=acme-corp, got %d", stats.TotalRecords)
	}
}

func TestQueryProvenance_FilterByAnnotation(t *testing.T) {
	dataDir := t.TempDir()
	setupProvenanceTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := ProvenanceQueryRequest{
		AnnotationKey:   "project",
		AnnotationValue: "beta",
		Limit:           100,
	}

	results, err := svc.QueryProvenance(req)
	if err != nil {
		t.Fatalf("QueryProvenance failed: %v", err)
	}

	// Should have 1 record with project=beta (did-2)
	if len(results) != 1 {
		t.Fatalf("Expected 1 record for project=beta, got %d", len(results))
	}
	if results[0].DID != "did-2" {
		t.Errorf("Expected did-2, got %s", results[0].DID)
	}
	if results[0].Annotations["customer"] != "globex-inc" {
		t.Errorf("Expected customer=globex-inc, got %s", results[0].Annotations["customer"])
	}
}

func TestQueryProvenance_AnnotationsReturned(t *testing.T) {
	// Verify that annotation values in results are complete strings
	dataDir := t.TempDir()
	setupProvenanceTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := ProvenanceQueryRequest{
		Limit: 100,
	}

	results, err := svc.QueryProvenance(req)
	if err != nil {
		t.Fatalf("QueryProvenance failed: %v", err)
	}

	// Verify annotation values are complete
	for _, rec := range results {
		if rec.Annotations == nil {
			continue
		}
		for key, val := range rec.Annotations {
			// Check for suspiciously short values (possible truncation)
			if key == "customer" && len(val) == 1 {
				t.Errorf("Annotation customer value '%s' appears truncated (DID: %s)", val, rec.DID)
			}
			if key == "project" && len(val) == 1 {
				t.Errorf("Annotation project value '%s' appears truncated (DID: %s)", val, rec.DID)
			}
		}
	}
}

// =============================================================================
// Edge Case Tests
// =============================================================================

func TestAnnotationFilter_SpecialCharacters(t *testing.T) {
	// Test that annotation values with special characters are handled correctly
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	// Get all customer values
	values, err := svc.GetAnnotationValues("customer", timeFrom, timeTo, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	// Should include "o'reilly-media" with the apostrophe
	found := false
	for _, v := range values {
		if v == "o'reilly-media" {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("Missing customer value with special character: o'reilly-media. Got: %v", values)
	}
}

func TestAnnotationFilter_EmptyAnnotationsMap(t *testing.T) {
	// Test handling of events with nil/empty annotations
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	req := AnalyticsRequest{
		TimeFrom:          time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC),
		TimeTo:            time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC),
		IntervalMs:        300000,
		GroupByAnnotation: "customer",
	}

	results, err := svc.QueryAnalytics(req)
	if err != nil {
		t.Fatalf("QueryAnalytics failed: %v", err)
	}

	// Should have "not present" for src-delta which has no annotations
	foundNotPresent := false
	for _, row := range results {
		if row.DataSource == "src-delta - not present" && row.AnnotationValue == "not present" {
			foundNotPresent = true
			break
		}
	}

	if !foundNotPresent {
		t.Error("Expected 'not present' annotation value for events without annotations")
	}
}

func TestAnnotationKeys_AllKeysExtracted(t *testing.T) {
	// Verify that all unique annotation keys are properly extracted
	dataDir := t.TempDir()
	setupAnnotationTestData(t, dataDir)

	svc, err := NewService(dataDir, testConfigProvider(), testLogger())
	if err != nil {
		t.Fatal(err)
	}
	defer svc.Close()

	timeFrom := time.Date(2025, 11, 25, 0, 0, 0, 0, time.UTC)
	timeTo := time.Date(2025, 11, 26, 0, 0, 0, 0, time.UTC)

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, false, true, nil, nil)
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	expectedKeys := []string{"customer", "region", "env", "department", "tier", "priority", "tag"}
	for _, expected := range expectedKeys {
		found := false
		for _, k := range keys {
			if k == expected {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("Missing expected annotation key: %s", expected)
		}
	}
}
