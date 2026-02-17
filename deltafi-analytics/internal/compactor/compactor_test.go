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
 * ABOUTME: Tests for the compaction pipeline including two-stage aggregation and age-off.
 * ABOUTME: Verifies event/annotation merging, archive consolidation, and corrupt file handling.
 */
package compactor

import (
	"database/sql"
	"encoding/json"
	"fmt"
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

// =============================================================================
// Constructor and Config Tests
// =============================================================================

func TestNew_Defaults(t *testing.T) {
	cfg := Config{DataDir: "/tmp/test"}
	c := New(cfg, testLogger())

	if c.cfg.CheckInterval != 1*time.Minute {
		t.Errorf("expected default CheckInterval 1m, got %v", c.cfg.CheckInterval)
	}
	if c.cfg.MemoryLimit != "4GB" {
		t.Errorf("expected default MemoryLimit 4GB, got %s", c.cfg.MemoryLimit)
	}
	if c.cfg.ArchiveThreshold != 72*time.Hour {
		t.Errorf("expected default ArchiveThreshold 72h, got %v", c.cfg.ArchiveThreshold)
	}
}

func TestNew_CustomConfig(t *testing.T) {
	cfg := Config{
		DataDir:          "/tmp/test",
		CheckInterval:    5 * time.Minute,
		MemoryLimit:      "2GB",
		ArchiveThreshold: 24 * time.Hour,
	}
	c := New(cfg, testLogger())

	if c.cfg.CheckInterval != 5*time.Minute {
		t.Errorf("expected CheckInterval 5m, got %v", c.cfg.CheckInterval)
	}
	if c.cfg.MemoryLimit != "2GB" {
		t.Errorf("expected MemoryLimit 2GB, got %s", c.cfg.MemoryLimit)
	}
	if c.cfg.ArchiveThreshold != 24*time.Hour {
		t.Errorf("expected ArchiveThreshold 24h, got %v", c.cfg.ArchiveThreshold)
	}
}

// =============================================================================
// Helper Function Tests
// =============================================================================

func TestFileExists(t *testing.T) {
	tmpDir := t.TempDir()

	// Non-existent file
	if fileExists(filepath.Join(tmpDir, "nonexistent")) {
		t.Error("fileExists should return false for non-existent file")
	}

	// Existing file
	existingFile := filepath.Join(tmpDir, "exists.txt")
	if err := os.WriteFile(existingFile, []byte("test"), 0644); err != nil {
		t.Fatal(err)
	}
	if !fileExists(existingFile) {
		t.Error("fileExists should return true for existing file")
	}
}

func TestDirHasParquetFiles(t *testing.T) {
	tmpDir := t.TempDir()

	// Empty directory
	if dirHasParquetFiles(tmpDir) {
		t.Error("empty directory should not have parquet files")
	}

	// Non-parquet file
	if err := os.WriteFile(filepath.Join(tmpDir, "test.txt"), []byte("test"), 0644); err != nil {
		t.Fatal(err)
	}
	if dirHasParquetFiles(tmpDir) {
		t.Error("directory with only txt files should not have parquet files")
	}

	// Parquet file
	if err := os.WriteFile(filepath.Join(tmpDir, "test.parquet"), []byte("test"), 0644); err != nil {
		t.Fatal(err)
	}
	if !dirHasParquetFiles(tmpDir) {
		t.Error("directory with parquet file should return true")
	}
}

func TestDirNewerThan(t *testing.T) {
	tmpDir := t.TempDir()
	threshold := time.Now().Add(-1 * time.Hour)

	// No parquet files
	if dirNewerThan(tmpDir, threshold) {
		t.Error("empty directory should not be newer than threshold")
	}

	// Create file (will have current time)
	parquetFile := filepath.Join(tmpDir, "test.parquet")
	if err := os.WriteFile(parquetFile, []byte("test"), 0644); err != nil {
		t.Fatal(err)
	}

	if !dirNewerThan(tmpDir, threshold) {
		t.Error("directory with new parquet file should be newer than 1 hour ago")
	}

	// Use future threshold
	futureThreshold := time.Now().Add(1 * time.Hour)
	if dirNewerThan(tmpDir, futureThreshold) {
		t.Error("directory should not be newer than future threshold")
	}
}

func TestFormatBytes(t *testing.T) {
	tests := []struct {
		bytes int64
		want  string
	}{
		{0, "0 B"},
		{100, "100 B"},
		{1023, "1023 B"},
		{1024, "1.0 KB"},
		{1536, "1.5 KB"},
		{1048576, "1.0 MB"},
		{1073741824, "1.0 GB"},
	}

	for _, tt := range tests {
		got := formatBytes(tt.bytes)
		if got != tt.want {
			t.Errorf("formatBytes(%d) = %s, want %s", tt.bytes, got, tt.want)
		}
	}
}

func TestQuoteFiles(t *testing.T) {
	tests := []struct {
		files []string
		want  string
	}{
		{[]string{}, ""},
		{[]string{"/a/b.parquet"}, "'/a/b.parquet'"},
		{[]string{"/a.parquet", "/b.parquet"}, "'/a.parquet', '/b.parquet'"},
	}

	for _, tt := range tests {
		got := quoteFiles(tt.files)
		if got != tt.want {
			t.Errorf("quoteFiles(%v) = %s, want %s", tt.files, got, tt.want)
		}
	}
}

func TestTotalFileSize(t *testing.T) {
	tmpDir := t.TempDir()

	// Create files with known sizes
	file1 := filepath.Join(tmpDir, "a.parquet")
	file2 := filepath.Join(tmpDir, "b.parquet")
	if err := os.WriteFile(file1, make([]byte, 100), 0644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(file2, make([]byte, 200), 0644); err != nil {
		t.Fatal(err)
	}

	total := totalFileSize([]string{file1, file2})
	if total != 300 {
		t.Errorf("expected total size 300, got %d", total)
	}

	// Include non-existent file - should be skipped
	total = totalFileSize([]string{file1, filepath.Join(tmpDir, "nonexistent"), file2})
	if total != 300 {
		t.Errorf("expected total size 300 (skipping nonexistent), got %d", total)
	}
}

func TestMaxFileMtime(t *testing.T) {
	tmpDir := t.TempDir()

	// Empty lists
	mtime := maxFileMtime([]string{}, []string{})
	if !mtime.IsZero() {
		t.Errorf("expected zero time for empty lists, got %v", mtime)
	}

	// Create files
	file1 := filepath.Join(tmpDir, "a.parquet")
	file2 := filepath.Join(tmpDir, "b.parquet")
	if err := os.WriteFile(file1, []byte("a"), 0644); err != nil {
		t.Fatal(err)
	}
	time.Sleep(10 * time.Millisecond)
	if err := os.WriteFile(file2, []byte("b"), 0644); err != nil {
		t.Fatal(err)
	}

	// file2 should be newer
	mtime = maxFileMtime([]string{file1}, []string{file2})
	info, _ := os.Stat(file2)
	if !mtime.Equal(info.ModTime()) {
		t.Errorf("expected mtime of newer file, got %v vs %v", mtime, info.ModTime())
	}
}

func TestIsHourOlderThanThreshold(t *testing.T) {
	c := New(Config{
		DataDir:          "/tmp",
		ArchiveThreshold: 24 * time.Hour,
	}, testLogger())

	// Very old hour (should be older than threshold)
	if !c.isHourOlderThanThreshold("20200101", "00") {
		t.Error("2020-01-01 00:00 should be older than 24h threshold")
	}

	// Current hour (should NOT be older)
	now := time.Now()
	dateStr := now.Format("20060102")
	hourStr := now.Format("15")
	if c.isHourOlderThanThreshold(dateStr, hourStr) {
		t.Error("current hour should not be older than threshold")
	}

	// Invalid date format
	if c.isHourOlderThanThreshold("invalid", "00") {
		t.Error("invalid date should return false")
	}
}

func TestIsDateOlderThanThreshold(t *testing.T) {
	c := New(Config{
		DataDir:          "/tmp",
		ArchiveThreshold: 24 * time.Hour,
	}, testLogger())

	// Very old date
	if !c.isDateOlderThanThreshold("20200101") {
		t.Error("2020-01-01 should be older than 24h threshold")
	}

	// Today
	today := time.Now().Format("20060102")
	if c.isDateOlderThanThreshold(today) {
		t.Error("today should not be older than threshold")
	}

	// Invalid date format
	if c.isDateOlderThanThreshold("invalid") {
		t.Error("invalid date should return false")
	}
}

// =============================================================================
// Eligible Dates Tests
// =============================================================================

func TestEligibleDates_EmptyDir(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	dates, err := c.eligibleDates()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(dates) != 0 {
		t.Errorf("expected no eligible dates, got %v", dates)
	}
}

func TestEligibleDates_WithDates(t *testing.T) {
	tmpDir := t.TempDir()
	eventsDir := filepath.Join(tmpDir, "events")

	// Create date directories
	os.MkdirAll(filepath.Join(eventsDir, "20251125"), 0755)
	os.MkdirAll(filepath.Join(eventsDir, "20251126"), 0755)
	// Create staging directory (should be skipped)
	os.MkdirAll(filepath.Join(eventsDir, "20251127.writing"), 0755)
	os.MkdirAll(filepath.Join(eventsDir, "20251128.deleting"), 0755)
	// Create a file (should be skipped)
	os.WriteFile(filepath.Join(eventsDir, "file.txt"), []byte("test"), 0644)

	c := New(Config{DataDir: tmpDir}, testLogger())
	dates, err := c.eligibleDates()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(dates) != 2 {
		t.Errorf("expected 2 eligible dates, got %v", dates)
	}
}

// =============================================================================
// Hour Needs Compaction Tests
// =============================================================================

func TestHourNeedsCompaction_NoEvents(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// No events directory
	needs, reason := c.hourNeedsCompaction("20251125", "10")
	if needs {
		t.Errorf("should not need compaction with no events, reason: %s", reason)
	}
}

func TestHourNeedsCompaction_NoAggregate(t *testing.T) {
	tmpDir := t.TempDir()
	eventsDir := filepath.Join(tmpDir, "events", "20251125", "10")
	os.MkdirAll(eventsDir, 0755)
	os.WriteFile(filepath.Join(eventsDir, "events.parquet"), []byte("test"), 0644)

	c := New(Config{DataDir: tmpDir}, testLogger())

	needs, reason := c.hourNeedsCompaction("20251125", "10")
	if !needs {
		t.Error("should need compaction when no aggregate exists")
	}
	if reason != "no_aggregate" {
		t.Errorf("expected reason 'no_aggregate', got '%s'", reason)
	}
}

func TestHourNeedsCompaction_EventsNewer(t *testing.T) {
	tmpDir := t.TempDir()

	// Create events directory with a parquet file
	eventsDir := filepath.Join(tmpDir, "events", "20251125", "10")
	os.MkdirAll(eventsDir, 0755)
	os.WriteFile(filepath.Join(eventsDir, "events.parquet"), []byte("test"), 0644)

	// Create older aggregate
	aggregatedDir := filepath.Join(tmpDir, "aggregated", "20251125")
	os.MkdirAll(aggregatedDir, 0755)
	aggregatedFile := filepath.Join(aggregatedDir, "10.parquet")
	os.WriteFile(aggregatedFile, []byte("test"), 0644)

	// Set aggregate file time to past
	past := time.Now().Add(-1 * time.Hour)
	os.Chtimes(aggregatedFile, past, past)

	c := New(Config{DataDir: tmpDir}, testLogger())

	needs, reason := c.hourNeedsCompaction("20251125", "10")
	if !needs {
		t.Error("should need compaction when events are newer")
	}
	if reason != "events_newer" {
		t.Errorf("expected reason 'events_newer', got '%s'", reason)
	}
}

func TestHourNeedsCompaction_AnnotationsNewer(t *testing.T) {
	tmpDir := t.TempDir()

	// Create events (older)
	eventsDir := filepath.Join(tmpDir, "events", "20251125", "10")
	os.MkdirAll(eventsDir, 0755)
	eventsFile := filepath.Join(eventsDir, "events.parquet")
	os.WriteFile(eventsFile, []byte("test"), 0644)
	past := time.Now().Add(-2 * time.Hour)
	os.Chtimes(eventsFile, past, past)

	// Create aggregate (older than annotations)
	aggregatedDir := filepath.Join(tmpDir, "aggregated", "20251125")
	os.MkdirAll(aggregatedDir, 0755)
	aggregatedFile := filepath.Join(aggregatedDir, "10.parquet")
	os.WriteFile(aggregatedFile, []byte("test"), 0644)
	os.Chtimes(aggregatedFile, past, past)

	// Create newer annotations
	annoDir := filepath.Join(tmpDir, "annotations", "20251125", "10")
	os.MkdirAll(annoDir, 0755)
	os.WriteFile(filepath.Join(annoDir, "annotations.parquet"), []byte("test"), 0644)

	c := New(Config{DataDir: tmpDir}, testLogger())

	needs, reason := c.hourNeedsCompaction("20251125", "10")
	if !needs {
		t.Error("should need compaction when annotations are newer")
	}
	if reason != "annotations_newer" {
		t.Errorf("expected reason 'annotations_newer', got '%s'", reason)
	}
}

func TestHourNeedsCompaction_LateArrivalsAfterArchive(t *testing.T) {
	tmpDir := t.TempDir()

	// Create daily archive file
	os.MkdirAll(filepath.Join(tmpDir, "aggregated"), 0755)
	os.WriteFile(filepath.Join(tmpDir, "aggregated", "20251125.parquet"), []byte("daily"), 0644)

	// Create late-arriving events
	eventsDir := filepath.Join(tmpDir, "events", "20251125", "10")
	os.MkdirAll(eventsDir, 0755)
	os.WriteFile(filepath.Join(eventsDir, "late.parquet"), []byte("late"), 0644)

	c := New(Config{DataDir: tmpDir}, testLogger())

	needs, reason := c.hourNeedsCompaction("20251125", "10")
	if !needs {
		t.Error("should need compaction for late arrivals")
	}
	if reason != "late_arrivals" {
		t.Errorf("expected reason 'late_arrivals', got '%s'", reason)
	}
}

// =============================================================================
// Date Metadata Tests
// =============================================================================

func TestDateMeta_ReadWrite(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// Create aggregated directory
	aggregatedDir := filepath.Join(tmpDir, "aggregated", "20251125")
	os.MkdirAll(aggregatedDir, 0755)

	// Write metadata
	testTime := time.Now()
	err := c.updateDateMeta("20251125", "10", testTime)
	if err != nil {
		t.Fatalf("failed to write metadata: %v", err)
	}

	// Read metadata
	readTime := c.getLastProcessedMtime("20251125", "10")
	if readTime.UnixNano() != testTime.UnixNano() {
		t.Errorf("expected %v, got %v", testTime.UnixNano(), readTime.UnixNano())
	}

	// Update another hour
	testTime2 := testTime.Add(1 * time.Hour)
	err = c.updateDateMeta("20251125", "11", testTime2)
	if err != nil {
		t.Fatalf("failed to write metadata: %v", err)
	}

	// Verify both hours are present
	readTime = c.getLastProcessedMtime("20251125", "10")
	if readTime.UnixNano() != testTime.UnixNano() {
		t.Error("hour 10 metadata was overwritten")
	}
	readTime2 := c.getLastProcessedMtime("20251125", "11")
	if readTime2.UnixNano() != testTime2.UnixNano() {
		t.Error("hour 11 metadata not written correctly")
	}
}

func TestDateMeta_FallbackToAggregateMtime(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// Create aggregate file without metadata
	aggregatedDir := filepath.Join(tmpDir, "aggregated", "20251125")
	os.MkdirAll(aggregatedDir, 0755)
	aggregatedFile := filepath.Join(aggregatedDir, "10.parquet")
	os.WriteFile(aggregatedFile, []byte("test"), 0644)

	// Should fall back to aggregate mtime
	readTime := c.getLastProcessedMtime("20251125", "10")
	info, _ := os.Stat(aggregatedFile)
	if !readTime.Equal(info.ModTime()) {
		t.Error("should fall back to aggregate file mtime when no metadata exists")
	}
}

func TestDateMeta_ZeroTimeWhenNoData(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	readTime := c.getLastProcessedMtime("20251125", "10")
	if !readTime.IsZero() {
		t.Error("should return zero time when no metadata or aggregate exists")
	}
}

// =============================================================================
// Safe Remove Dir Tests
// =============================================================================

func TestSafeRemoveDir_NonExistent(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	err := c.safeRemoveDir(filepath.Join(tmpDir, "nonexistent"))
	if err != nil {
		t.Errorf("removing non-existent dir should not error: %v", err)
	}
}

func TestSafeRemoveDir_EmptyDir(t *testing.T) {
	tmpDir := t.TempDir()
	targetDir := filepath.Join(tmpDir, "target")
	os.MkdirAll(targetDir, 0755)

	c := New(Config{DataDir: tmpDir}, testLogger())
	err := c.safeRemoveDir(targetDir)
	if err != nil {
		t.Fatalf("failed to remove empty dir: %v", err)
	}

	if fileExists(targetDir) {
		t.Error("directory should be removed")
	}
	if fileExists(targetDir + ".deleting") {
		t.Error(".deleting directory should be cleaned up")
	}
}

func TestSafeRemoveDir_WithFiles(t *testing.T) {
	tmpDir := t.TempDir()
	targetDir := filepath.Join(tmpDir, "target")
	os.MkdirAll(targetDir, 0755)
	os.WriteFile(filepath.Join(targetDir, "file.parquet"), []byte("data"), 0644)

	c := New(Config{DataDir: tmpDir}, testLogger())
	err := c.safeRemoveDir(targetDir)
	if err != nil {
		t.Fatalf("failed to remove dir with files: %v", err)
	}

	if fileExists(targetDir) {
		t.Error("directory should be removed")
	}
}

func TestSafeRemoveDir_ActiveWrites(t *testing.T) {
	tmpDir := t.TempDir()
	targetDir := filepath.Join(tmpDir, "target")
	os.MkdirAll(targetDir, 0755)

	// Create a recent .tmp file (active write)
	tmpFile := filepath.Join(targetDir, "active.tmp")
	os.WriteFile(tmpFile, []byte("writing"), 0644)

	c := New(Config{DataDir: tmpDir}, testLogger())
	err := c.safeRemoveDir(targetDir)
	if err == nil {
		t.Error("should error when active writes detected")
	}

	// Directory should be restored
	if !fileExists(targetDir) {
		t.Error("directory should be restored after detecting active writes")
	}
}

func TestSafeRemoveDir_StaleTmpFiles(t *testing.T) {
	tmpDir := t.TempDir()
	targetDir := filepath.Join(tmpDir, "target")
	os.MkdirAll(targetDir, 0755)

	// Create an old .tmp file (stale)
	tmpFile := filepath.Join(targetDir, "stale.tmp")
	os.WriteFile(tmpFile, []byte("stale"), 0644)
	// Set to 2 minutes ago (older than 1 minute threshold)
	oldTime := time.Now().Add(-2 * time.Minute)
	os.Chtimes(tmpFile, oldTime, oldTime)

	c := New(Config{DataDir: tmpDir}, testLogger())
	err := c.safeRemoveDir(targetDir)
	if err != nil {
		t.Fatalf("should succeed with only stale tmp files: %v", err)
	}

	if fileExists(targetDir) {
		t.Error("directory should be removed")
	}
}

// =============================================================================
// Age-Off Tests
// =============================================================================

func TestAgeOff_RemovesOldData(t *testing.T) {
	tmpDir := t.TempDir()

	// Create old date directory (30+ days ago)
	oldDate := time.Now().AddDate(0, 0, -35).Format("20060102")
	recentDate := time.Now().AddDate(0, 0, -5).Format("20060102")

	for _, dir := range []string{"events", "annotations", "preagg"} {
		os.MkdirAll(filepath.Join(tmpDir, dir, oldDate, "00"), 0755)
		os.MkdirAll(filepath.Join(tmpDir, dir, recentDate, "00"), 0755)
	}

	// Create aggregated files
	os.MkdirAll(filepath.Join(tmpDir, "aggregated", oldDate), 0755)
	os.MkdirAll(filepath.Join(tmpDir, "aggregated", recentDate), 0755)
	os.WriteFile(filepath.Join(tmpDir, "aggregated", oldDate+".parquet"), []byte("old"), 0644)
	os.WriteFile(filepath.Join(tmpDir, "aggregated", recentDate+".parquet"), []byte("recent"), 0644)

	c := New(Config{
		DataDir:        tmpDir,
		AgeOffDaysFunc: func() int { return 30 },
	}, testLogger())

	err := c.ageOff()
	if err != nil {
		t.Fatalf("age-off failed: %v", err)
	}

	// Old data should be removed
	for _, dir := range []string{"events", "annotations", "preagg"} {
		if fileExists(filepath.Join(tmpDir, dir, oldDate)) {
			t.Errorf("old %s directory should be removed", dir)
		}
		if !fileExists(filepath.Join(tmpDir, dir, recentDate)) {
			t.Errorf("recent %s directory should remain", dir)
		}
	}

	// Aggregated old data should be removed
	if fileExists(filepath.Join(tmpDir, "aggregated", oldDate)) {
		t.Error("old aggregated directory should be removed")
	}
	if fileExists(filepath.Join(tmpDir, "aggregated", oldDate+".parquet")) {
		t.Error("old aggregated daily file should be removed")
	}
	if !fileExists(filepath.Join(tmpDir, "aggregated", recentDate+".parquet")) {
		t.Error("recent aggregated daily file should remain")
	}
}

func TestAgeOff_DisabledWhenZero(t *testing.T) {
	tmpDir := t.TempDir()

	oldDate := time.Now().AddDate(0, 0, -100).Format("20060102")
	os.MkdirAll(filepath.Join(tmpDir, "events", oldDate), 0755)

	c := New(Config{
		DataDir:        tmpDir,
		AgeOffDaysFunc: func() int { return 0 }, // Disabled
	}, testLogger())

	err := c.ageOff()
	if err != nil {
		t.Fatalf("age-off failed: %v", err)
	}

	// Old data should NOT be removed when age-off is disabled
	if !fileExists(filepath.Join(tmpDir, "events", oldDate)) {
		t.Error("data should remain when age-off is disabled")
	}
}

func TestAgeOff_ProvenanceData(t *testing.T) {
	tmpDir := t.TempDir()

	oldDate := time.Now().AddDate(0, 0, -10).Format("20060102")
	recentDate := time.Now().AddDate(0, 0, -1).Format("20060102")

	// Create provenance raw directories
	os.MkdirAll(filepath.Join(tmpDir, "provenance", "raw", oldDate, "00"), 0755)
	os.MkdirAll(filepath.Join(tmpDir, "provenance", "raw", recentDate, "00"), 0755)

	// Create provenance compacted directories
	os.MkdirAll(filepath.Join(tmpDir, "provenance", "compacted", "system-a", oldDate), 0755)
	os.MkdirAll(filepath.Join(tmpDir, "provenance", "compacted", "system-a", recentDate), 0755)

	c := New(Config{
		DataDir:                  tmpDir,
		ProvenanceAgeOffDaysFunc: func() int { return 3 },
	}, testLogger())

	err := c.ageOff()
	if err != nil {
		t.Fatalf("age-off failed: %v", err)
	}

	// Old provenance should be removed
	if fileExists(filepath.Join(tmpDir, "provenance", "raw", oldDate)) {
		t.Error("old provenance raw should be removed")
	}
	if fileExists(filepath.Join(tmpDir, "provenance", "compacted", "system-a", oldDate)) {
		t.Error("old provenance compacted should be removed")
	}

	// Recent provenance should remain
	if !fileExists(filepath.Join(tmpDir, "provenance", "raw", recentDate)) {
		t.Error("recent provenance raw should remain")
	}
	if !fileExists(filepath.Join(tmpDir, "provenance", "compacted", "system-a", recentDate)) {
		t.Error("recent provenance compacted should remain")
	}
}

// =============================================================================
// Cleanup Empty Dir Tests
// =============================================================================

func TestCleanupEmptyDateDir(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	date := "20251125"

	// Create empty date directories
	eventsDir := filepath.Join(tmpDir, "events", date)
	annoDir := filepath.Join(tmpDir, "annotations", date)
	preaggDir := filepath.Join(tmpDir, "preagg", date)
	os.MkdirAll(filepath.Join(eventsDir, "10"), 0755)
	os.MkdirAll(filepath.Join(annoDir, "10"), 0755)
	os.MkdirAll(preaggDir, 0755)

	c.cleanupEmptyDateDir(date)

	// All should be removed since they're empty
	if fileExists(eventsDir) {
		t.Error("empty events date dir should be removed")
	}
	if fileExists(annoDir) {
		t.Error("empty annotations date dir should be removed")
	}
	if fileExists(preaggDir) {
		t.Error("empty preagg date dir should be removed")
	}
}

func TestCleanupEmptyDateDir_KeepsNonEmpty(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	date := "20251125"

	// Create date directory with a file
	eventsDir := filepath.Join(tmpDir, "events", date)
	hourDir := filepath.Join(eventsDir, "10")
	os.MkdirAll(hourDir, 0755)
	os.WriteFile(filepath.Join(hourDir, "data.parquet"), []byte("data"), 0644)

	c.cleanupEmptyDateDir(date)

	// Should remain since it has files
	if !fileExists(eventsDir) {
		t.Error("non-empty events date dir should remain")
	}
}

// =============================================================================
// Compaction Integration Tests
// =============================================================================

func writeTestEvents(t *testing.T, path string, events []schema.Event) {
	t.Helper()
	dir := filepath.Dir(path)
	os.MkdirAll(dir, 0755)

	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("failed to create file: %v", err)
	}
	defer f.Close()

	writer := parquet.NewGenericWriter[schema.Event](f, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(events); err != nil {
		t.Fatalf("failed to write events: %v", err)
	}
	writer.Close()
}

func writeTestAnnotations(t *testing.T, path string, annotations []schema.Annotation) {
	t.Helper()
	dir := filepath.Dir(path)
	os.MkdirAll(dir, 0755)

	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("failed to create file: %v", err)
	}
	defer f.Close()

	writer := parquet.NewGenericWriter[schema.Annotation](f, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(annotations); err != nil {
		t.Fatalf("failed to write annotations: %v", err)
	}
	writer.Close()
}

func writeTestProvenance(t *testing.T, path string, records []schema.Provenance) {
	t.Helper()
	dir := filepath.Dir(path)
	os.MkdirAll(dir, 0755)

	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("failed to create file: %v", err)
	}
	defer f.Close()

	writer := parquet.NewGenericWriter[schema.Provenance](f, parquet.Compression(&parquet.Snappy))
	if _, err := writer.Write(records); err != nil {
		t.Fatalf("failed to write provenance: %v", err)
	}
	writer.Close()
}

func TestCompactHour_BasicCompaction(t *testing.T) {
	tmpDir := t.TempDir()

	// Use a date in the future to avoid archiving
	now := time.Now().UTC()
	futureDate := now.AddDate(0, 0, 1)
	date := futureDate.Format("20060102")
	hour := "10"
	eventTime := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 30, 0, 0, time.UTC)
	ingestTime := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 31, 0, 0, time.UTC)

	// Create test events with all required fields
	events := []schema.Event{
		{DID: "did-1", DataSource: "source-a", EventType: "INGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 1000, FileCount: 1},
		{DID: "did-2", DataSource: "source-a", EventType: "INGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 2000, FileCount: 2},
		{DID: "did-3", DataSource: "source-b", EventType: "EGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 500, FileCount: 1},
	}
	writeTestEvents(t, filepath.Join(tmpDir, "events", date, hour, "events.parquet"), events)

	// Create test annotations with UpdateTime
	updateTime := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 32, 0, 0, time.UTC)
	annotations := []schema.Annotation{
		{DID: "did-1", Key: "customer", Value: "acme", UpdateTime: updateTime},
		{DID: "did-2", Key: "customer", Value: "globex", UpdateTime: updateTime},
	}
	writeTestAnnotations(t, filepath.Join(tmpDir, "annotations", date, hour, "annotations.parquet"), annotations)

	// Create compactor with very short archive threshold to test non-archive path
	c := New(Config{
		DataDir:          tmpDir,
		ArchiveThreshold: 1000 * time.Hour, // Won't archive
	}, testLogger())

	// Open DuckDB for compaction
	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	dbPath := filepath.Join(tempDir, "test.duckdb")
	db, err := sql.Open("duckdb", dbPath)
	if err != nil {
		t.Fatalf("failed to open duckdb: %v", err)
	}
	defer db.Close()
	db.Exec(fmt.Sprintf("SET memory_limit='%s'", c.cfg.MemoryLimit))
	db.Exec(fmt.Sprintf("SET temp_directory='%s'", tempDir))
	db.Exec("SET TimeZone='UTC'")

	// Run compaction
	err = c.compactHour(db, date, hour)
	if err != nil {
		t.Fatalf("compactHour failed: %v", err)
	}

	// Verify preagg file was created (not archived since date is recent)
	preaggPath := filepath.Join(tmpDir, "preagg", date, hour+".parquet")
	if !fileExists(preaggPath) {
		t.Errorf("preagg file should be created at %s", preaggPath)
	}

	// Verify aggregated file was created
	aggregatedPath := filepath.Join(tmpDir, "aggregated", date, hour+".parquet")
	if !fileExists(aggregatedPath) {
		t.Errorf("aggregated file should be created at %s", aggregatedPath)
	}

	// Verify metadata was updated
	mtime := c.getLastProcessedMtime(date, hour)
	if mtime.IsZero() {
		t.Error("metadata should be updated after compaction")
	}
}

func TestCompactHour_WithArchive(t *testing.T) {
	tmpDir := t.TempDir()

	// Use old date that will be archived
	date := "20200101"
	hour := "10"
	eventTime := time.Date(2020, 1, 1, 10, 30, 0, 0, time.UTC)
	ingestTime := time.Date(2020, 1, 1, 10, 31, 0, 0, time.UTC)

	// Create test events with all required fields
	events := []schema.Event{
		{DID: "did-1", DataSource: "source-a", EventType: "INGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 1000, FileCount: 1},
	}
	writeTestEvents(t, filepath.Join(tmpDir, "events", date, hour, "events.parquet"), events)

	c := New(Config{
		DataDir:          tmpDir,
		ArchiveThreshold: 1 * time.Hour, // Will archive old data
	}, testLogger())

	// Open DuckDB
	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	dbPath := filepath.Join(tempDir, "test.duckdb")
	db, err := sql.Open("duckdb", dbPath)
	if err != nil {
		t.Fatalf("failed to open duckdb: %v", err)
	}
	defer db.Close()
	db.Exec(fmt.Sprintf("SET memory_limit='%s'", c.cfg.MemoryLimit))
	db.Exec("SET TimeZone='UTC'")

	// Run compaction
	err = c.compactHour(db, date, hour)
	if err != nil {
		t.Fatalf("compactHour failed: %v", err)
	}

	// Verify source files were cleaned up (archived)
	eventsDir := filepath.Join(tmpDir, "events", date, hour)
	if fileExists(eventsDir) {
		t.Error("events directory should be removed after archive")
	}

	// Verify preagg was cleaned up
	preaggPath := filepath.Join(tmpDir, "preagg", date, hour+".parquet")
	if fileExists(preaggPath) {
		t.Error("preagg file should be removed after archive")
	}

	// Aggregated file should exist (at hour based on event_time)
	aggregatedPath := filepath.Join(tmpDir, "aggregated", date, hour+".parquet")
	if !fileExists(aggregatedPath) {
		t.Error("aggregated file should exist")
	}
}

func TestCompactProvenance_Basic(t *testing.T) {
	tmpDir := t.TempDir()

	// Use an old date/hour that won't be skipped
	date := "20200101"
	hour := "10"
	created := time.Date(2020, 1, 1, 10, 0, 0, 0, time.UTC)
	completed := time.Date(2020, 1, 1, 10, 5, 0, 0, time.UTC)

	// Create test provenance records
	records := []schema.Provenance{
		{DID: "did-1", SystemName: "system-a", DataSource: "source-1", Filename: "file1.txt",
			FinalState: "COMPLETE", Created: created, Completed: completed},
		{DID: "did-2", SystemName: "system-a", DataSource: "source-1", Filename: "file2.txt",
			FinalState: "ERROR", Created: created, Completed: completed},
		{DID: "did-3", SystemName: "system-b", DataSource: "source-2", Filename: "file3.txt",
			FinalState: "FILTERED", Created: created, Completed: completed},
	}

	writeTestProvenance(t, filepath.Join(tmpDir, "provenance", "raw", date, hour, "prov.parquet"), records)

	c := New(Config{DataDir: tmpDir}, testLogger())

	err := c.compactProvenance(true) // Include current hour
	if err != nil {
		t.Fatalf("compactProvenance failed: %v", err)
	}

	// Verify compacted files exist for each system
	systemAPath := filepath.Join(tmpDir, "provenance", "compacted", "system-a", date, hour+".parquet")
	systemBPath := filepath.Join(tmpDir, "provenance", "compacted", "system-b", date, hour+".parquet")

	if !fileExists(systemAPath) {
		t.Error("system-a compacted provenance should exist")
	}
	if !fileExists(systemBPath) {
		t.Error("system-b compacted provenance should exist")
	}

	// Raw files should be cleaned up
	rawDir := filepath.Join(tmpDir, "provenance", "raw", date, hour)
	if fileExists(rawDir) {
		t.Error("raw provenance directory should be cleaned up")
	}
}

func TestCheckAndRun_SkipsWhenRunning(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// Acquire the lock
	c.running.Lock()

	// Should return nil without doing anything
	err := c.CheckAndRun()
	if err != nil {
		t.Errorf("should not error when skipping: %v", err)
	}

	// Release lock
	c.running.Unlock()
}

func TestConsolidateDailyArchive(t *testing.T) {
	tmpDir := t.TempDir()

	// Use an old date that will be archived
	date := "20200101"
	bucket := time.Date(2020, 1, 1, 10, 0, 0, 0, time.UTC)

	// Create hourly aggregated files
	aggregatedDir := filepath.Join(tmpDir, "aggregated", date)
	os.MkdirAll(aggregatedDir, 0755)

	for hour := range 3 {
		hourBucket := bucket.Add(time.Duration(hour) * time.Hour)
		events := []schema.AggregatedEvent{
			{Bucket: hourBucket, DataSource: "source-a", EventType: "INGRESS",
				EventCount: 10, TotalBytes: 1000, TotalFileCount: 10},
		}

		hourFile := filepath.Join(aggregatedDir, fmt.Sprintf("%02d.parquet", hour))
		f, _ := os.Create(hourFile)
		writer := parquet.NewGenericWriter[schema.AggregatedEvent](f, parquet.Compression(&parquet.Snappy))
		writer.Write(events)
		writer.Close()
		f.Close()
	}

	c := New(Config{
		DataDir:          tmpDir,
		ArchiveThreshold: 1 * time.Hour, // Ensures old date is archived
	}, testLogger())

	// Open DuckDB
	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	dbPath := filepath.Join(tempDir, "test.duckdb")
	db, err := sql.Open("duckdb", dbPath)
	if err != nil {
		t.Fatalf("failed to open duckdb: %v", err)
	}
	defer db.Close()
	db.Exec(fmt.Sprintf("SET memory_limit='%s'", c.cfg.MemoryLimit))

	err = c.consolidateDailyArchive(db, date)
	if err != nil {
		t.Fatalf("consolidateDailyArchive failed: %v", err)
	}

	// Daily file should be created
	dailyPath := filepath.Join(tmpDir, "aggregated", date+".parquet")
	if !fileExists(dailyPath) {
		t.Error("daily archive file should be created")
	}

	// Hourly directory should be removed
	if fileExists(aggregatedDir) {
		t.Error("hourly directory should be removed after consolidation")
	}
}

func TestConsolidateDailyArchive_SkipsRecentDate(t *testing.T) {
	tmpDir := t.TempDir()
	today := time.Now().Format("20060102")

	// Create hourly files for today
	aggregatedDir := filepath.Join(tmpDir, "aggregated", today)
	os.MkdirAll(aggregatedDir, 0755)
	os.WriteFile(filepath.Join(aggregatedDir, "10.parquet"), []byte("data"), 0644)

	c := New(Config{
		DataDir:          tmpDir,
		ArchiveThreshold: 72 * time.Hour, // Today won't be archived
	}, testLogger())

	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	db, _ := sql.Open("duckdb", filepath.Join(tempDir, "test.duckdb"))
	defer db.Close()

	err := c.consolidateDailyArchive(db, today)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Daily file should NOT be created for recent date
	dailyPath := filepath.Join(tmpDir, "aggregated", today+".parquet")
	if fileExists(dailyPath) {
		t.Error("daily file should not be created for recent date")
	}

	// Hourly directory should remain
	if !fileExists(aggregatedDir) {
		t.Error("hourly directory should remain for recent date")
	}
}

// =============================================================================
// Corrupt File Detection Tests
// =============================================================================

func TestFindAndRemoveCorruptFiles_NoCorruption(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// Create valid parquet files
	events := []schema.Event{
		{DID: "did-1", DataSource: "source-a", EventType: "INGRESS", Bytes: 100, FileCount: 1},
	}
	file1 := filepath.Join(tmpDir, "valid1.parquet")
	file2 := filepath.Join(tmpDir, "valid2.parquet")
	writeTestEvents(t, file1, events)
	writeTestEvents(t, file2, events)

	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	db, _ := sql.Open("duckdb", filepath.Join(tempDir, "test.duckdb"))
	defer db.Close()

	removed := c.findAndRemoveCorruptFiles(db, []string{file1, file2})
	if removed != 0 {
		t.Errorf("expected 0 files removed, got %d", removed)
	}

	// Files should still exist
	if !fileExists(file1) || !fileExists(file2) {
		t.Error("valid files should not be removed")
	}
}

func TestFindAndRemoveCorruptFiles_WithCorruption(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// Create one valid and one corrupt file
	events := []schema.Event{
		{DID: "did-1", DataSource: "source-a", EventType: "INGRESS", Bytes: 100, FileCount: 1},
	}
	validFile := filepath.Join(tmpDir, "valid.parquet")
	corruptFile := filepath.Join(tmpDir, "corrupt.parquet")
	writeTestEvents(t, validFile, events)
	os.WriteFile(corruptFile, []byte("not a parquet file"), 0644)

	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	db, _ := sql.Open("duckdb", filepath.Join(tempDir, "test.duckdb"))
	defer db.Close()

	removed := c.findAndRemoveCorruptFiles(db, []string{validFile, corruptFile})
	if removed != 1 {
		t.Errorf("expected 1 file removed, got %d", removed)
	}

	// Valid file should remain
	if !fileExists(validFile) {
		t.Error("valid file should remain")
	}

	// Corrupt file should be removed
	if fileExists(corruptFile) {
		t.Error("corrupt file should be removed")
	}
}

func TestFindCorruptFiles_BinarySearch(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	// Create multiple files with one corrupt in the middle
	events := []schema.Event{
		{DID: "did-1", DataSource: "source-a", EventType: "INGRESS", Bytes: 100, FileCount: 1},
	}

	files := make([]string, 5)
	for i := range 5 {
		files[i] = filepath.Join(tmpDir, fmt.Sprintf("file%d.parquet", i))
		if i == 2 {
			// Make file 2 corrupt
			os.WriteFile(files[i], []byte("corrupt"), 0644)
		} else {
			writeTestEvents(t, files[i], events)
		}
	}

	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	db, _ := sql.Open("duckdb", filepath.Join(tempDir, "test.duckdb"))
	defer db.Close()

	corrupt := c.findCorruptFiles(db, files, 0, len(files)-1)
	if len(corrupt) != 1 {
		t.Errorf("expected 1 corrupt file, got %d: %v", len(corrupt), corrupt)
	}
	if len(corrupt) == 1 && corrupt[0] != files[2] {
		t.Errorf("expected %s to be corrupt, got %s", files[2], corrupt[0])
	}
}

// =============================================================================
// Stage 2 Tests
// =============================================================================

func TestCompactStage2_EventTimePartitioning(t *testing.T) {
	tmpDir := t.TempDir()

	// Use a date in the future to avoid timezone issues
	now := time.Now().UTC()
	futureDate := now.AddDate(0, 0, 1)
	date := futureDate.Format("20060102")

	// Create preagg file with events spanning multiple hours
	preaggDir := filepath.Join(tmpDir, "preagg", date)
	os.MkdirAll(preaggDir, 0755)
	preaggPath := filepath.Join(preaggDir, "10.parquet")

	// Events with different event_time hours
	bucket1 := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 0, 0, 0, time.UTC)
	bucket2 := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 11, 0, 0, 0, time.UTC) // Different hour!

	preaggEvents := []schema.PreAggregatedEvent{
		{DID: "did-1", EventTimeBucket: bucket1, DataSource: "source-a", EventType: "INGRESS",
			EventCount: 5, TotalBytes: 1000, TotalFileCount: 5},
		{DID: "did-2", EventTimeBucket: bucket2, DataSource: "source-a", EventType: "INGRESS",
			EventCount: 3, TotalBytes: 500, TotalFileCount: 3},
	}

	f, _ := os.Create(preaggPath)
	writer := parquet.NewGenericWriter[schema.PreAggregatedEvent](f, parquet.Compression(&parquet.Snappy))
	writer.Write(preaggEvents)
	writer.Close()
	f.Close()

	c := New(Config{DataDir: tmpDir}, testLogger())

	tempDir := filepath.Join(tmpDir, ".duckdb_temp")
	os.MkdirAll(tempDir, 0755)
	db, _ := sql.Open("duckdb", filepath.Join(tempDir, "test.duckdb"))
	defer db.Close()
	db.Exec(fmt.Sprintf("SET memory_limit='%s'", c.cfg.MemoryLimit))
	db.Exec("SET TimeZone='UTC'")

	err := c.compactStage2(db, preaggPath)
	if err != nil {
		t.Fatalf("compactStage2 failed: %v", err)
	}

	// Should create two aggregated files - one for hour 10 and one for hour 11
	agg10 := filepath.Join(tmpDir, "aggregated", date, "10.parquet")
	agg11 := filepath.Join(tmpDir, "aggregated", date, "11.parquet")

	if !fileExists(agg10) {
		t.Error("aggregated file for hour 10 should be created")
	}
	if !fileExists(agg11) {
		t.Error("aggregated file for hour 11 should be created")
	}
}

// =============================================================================
// Full Pipeline Integration Test
// =============================================================================

func TestFullCompactionPipeline(t *testing.T) {
	tmpDir := t.TempDir()

	// Use a date in the future to avoid archiving
	now := time.Now().UTC()
	futureDate := now.AddDate(0, 0, 1)
	date := futureDate.Format("20060102")
	hour := "10"
	eventTime := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 30, 0, 0, time.UTC)
	ingestTime := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 31, 0, 0, time.UTC)

	// Create events with all required fields
	events := []schema.Event{
		{DID: "did-1", DataSource: "source-a", EventType: "INGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 1000, FileCount: 1, FlowName: "flow-1"},
		{DID: "did-2", DataSource: "source-a", EventType: "INGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 2000, FileCount: 2, FlowName: "flow-1"},
		{DID: "did-3", DataSource: "source-b", EventType: "EGRESS", EventTime: eventTime, IngestTime: ingestTime, Bytes: 500, FileCount: 1, FlowName: "flow-2"},
		{DID: "did-4", DataSource: "source-a", EventType: "ERROR", EventTime: eventTime, IngestTime: ingestTime, Bytes: 0, FileCount: 1, FlowName: "flow-1", Cause: "validation failed"},
	}
	writeTestEvents(t, filepath.Join(tmpDir, "events", date, hour, "batch1.parquet"), events)

	// Create annotations with UpdateTime
	updateTime := time.Date(futureDate.Year(), futureDate.Month(), futureDate.Day(), 10, 32, 0, 0, time.UTC)
	annotations := []schema.Annotation{
		{DID: "did-1", Key: "customer", Value: "acme", UpdateTime: updateTime},
		{DID: "did-1", Key: "region", Value: "us-east", UpdateTime: updateTime},
		{DID: "did-2", Key: "customer", Value: "globex", UpdateTime: updateTime},
		{DID: "did-3", Key: "customer", Value: "acme", UpdateTime: updateTime},
	}
	writeTestAnnotations(t, filepath.Join(tmpDir, "annotations", date, hour, "annotations.parquet"), annotations)

	c := New(Config{
		DataDir:          tmpDir,
		ArchiveThreshold: 1000 * time.Hour, // Won't archive
	}, testLogger())

	// Run full compaction
	err := c.CheckAndRun()
	if err != nil {
		t.Fatalf("CheckAndRun failed: %v", err)
	}

	// Find aggregated file (hour may vary based on local timezone)
	aggregatedDir := filepath.Join(tmpDir, "aggregated", date)
	aggFiles, _ := filepath.Glob(filepath.Join(aggregatedDir, "*.parquet"))
	if len(aggFiles) == 0 {
		t.Fatal("no aggregated files found")
	}
	aggregatedPath := aggFiles[0]

	// Read back and verify aggregation
	f, _ := os.Open(aggregatedPath)
	defer f.Close()
	stat, _ := f.Stat()
	pf, _ := parquet.OpenFile(f, stat.Size())
	reader := parquet.NewGenericReader[schema.AggregatedEvent](pf)
	defer reader.Close()

	aggEvents := make([]schema.AggregatedEvent, reader.NumRows())
	n, _ := reader.Read(aggEvents)

	if n == 0 {
		t.Error("should have aggregated events")
	}

	// Verify annotations were joined
	foundAnnotations := false
	for _, e := range aggEvents[:n] {
		if e.Annotations != nil && len(e.Annotations) > 0 {
			foundAnnotations = true
			break
		}
	}
	if !foundAnnotations {
		t.Error("aggregated events should have annotations")
	}
}

// =============================================================================
// Metadata JSON Tests
// =============================================================================

func TestDateMeta_JSONFormat(t *testing.T) {
	tmpDir := t.TempDir()
	c := New(Config{DataDir: tmpDir}, testLogger())

	date := "20251125"
	aggregatedDir := filepath.Join(tmpDir, "aggregated", date)
	os.MkdirAll(aggregatedDir, 0755)

	// Write metadata for multiple hours
	times := map[string]time.Time{
		"00": time.Date(2025, 11, 25, 0, 30, 0, 0, time.UTC),
		"12": time.Date(2025, 11, 25, 12, 30, 0, 0, time.UTC),
		"23": time.Date(2025, 11, 25, 23, 30, 0, 0, time.UTC),
	}

	for hour, ts := range times {
		if err := c.updateDateMeta(date, hour, ts); err != nil {
			t.Fatalf("failed to update metadata: %v", err)
		}
	}

	// Read raw JSON to verify format
	metaPath := filepath.Join(aggregatedDir, "meta.json")
	data, err := os.ReadFile(metaPath)
	if err != nil {
		t.Fatalf("failed to read metadata file: %v", err)
	}

	var meta DateMeta
	if err := json.Unmarshal(data, &meta); err != nil {
		t.Fatalf("failed to parse metadata JSON: %v", err)
	}

	if len(meta.HourMtimes) != 3 {
		t.Errorf("expected 3 hour entries, got %d", len(meta.HourMtimes))
	}

	// Verify each hour
	for hour, expected := range times {
		if mtime, ok := meta.HourMtimes[hour]; !ok {
			t.Errorf("missing hour %s in metadata", hour)
		} else if mtime != expected.UnixNano() {
			t.Errorf("hour %s: expected %v, got %v", hour, expected.UnixNano(), mtime)
		}
	}
}
