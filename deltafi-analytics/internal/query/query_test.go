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
	bucket := time.Date(2025, 11, 25, 10, 0, 0, 0, time.UTC)
	events := []schema.AggregatedEvent{
		{Bucket: bucket, DataSource: "source-a", EventType: "INGRESS", FlowName: "flow-1", ActionName: "ingest", Annotations: map[string]string{"customer": "acme", "region": "us-east"}, EventCount: 1, TotalBytes: 1000, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "source-a", EventType: "INGRESS", FlowName: "flow-1", ActionName: "ingest", Annotations: map[string]string{"customer": "globex"}, EventCount: 1, TotalBytes: 2000, TotalFileCount: 2},
		{Bucket: bucket, DataSource: "source-b", EventType: "EGRESS", FlowName: "flow-2", ActionName: "egress", Annotations: map[string]string{"customer": "acme"}, EventCount: 1, TotalBytes: 500, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "source-a", EventType: "ERROR", FlowName: "flow-1", ActionName: "transform", Cause: "validation failed", Annotations: map[string]string{"customer": "acme"}, EventCount: 1, TotalBytes: 0, TotalFileCount: 1},
		{Bucket: bucket, DataSource: "source-b", EventType: "FILTER", FlowName: "flow-2", ActionName: "filter", Cause: "duplicate", EventCount: 1, TotalBytes: 0, TotalFileCount: 1}, // no annotations
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

	values, err := svc.GetDistinctValues("data_source", timeFrom, timeTo, true) // filtered=true to test parquet scan
	if err != nil {
		t.Fatalf("GetDistinctValues failed: %v", err)
	}

	if len(values) != 2 {
		t.Errorf("got %d data sources, want 2", len(values))
	}

	// Check that both sources are present
	hasSourceA := false
	hasSourceB := false
	for _, v := range values {
		if v == "source-a" {
			hasSourceA = true
		}
		if v == "source-b" {
			hasSourceB = true
		}
	}
	if !hasSourceA || !hasSourceB {
		t.Errorf("missing expected data sources: got %v", values)
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

	keys, err := svc.GetAnnotationKeys(timeFrom, timeTo, false, true) // forGroup=false, filtered=true
	if err != nil {
		t.Fatalf("GetAnnotationKeys failed: %v", err)
	}

	// Should have customer, region + "None" option = 3
	if len(keys) != 3 {
		t.Errorf("got %d annotation keys, want 3 (None, customer, region)", len(keys))
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

	values, err := svc.GetAnnotationValues("customer", timeFrom, timeTo, true) // filtered=true to test parquet scan
	if err != nil {
		t.Fatalf("GetAnnotationValues failed: %v", err)
	}

	if len(values) != 2 {
		t.Errorf("got %d customer values, want 2 (acme, globex)", len(values))
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
	}

	for _, tt := range tests {
		got := containsAll(tt.values)
		if got != tt.want {
			t.Errorf("containsAll(%v) = %v, want %v", tt.values, got, tt.want)
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
