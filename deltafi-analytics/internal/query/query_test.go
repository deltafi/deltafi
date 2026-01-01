/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
