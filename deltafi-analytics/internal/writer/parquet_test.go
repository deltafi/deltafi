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
 * ABOUTME: Tests for the Parquet writer.
 * ABOUTME: Verifies file creation for events and annotations in hourly directories.
 */
package writer

import (
	"io"
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

func TestParquetWriter_WriteEvents(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "parquet-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	writer, err := New(Config{OutputDir: tmpDir}, testLogger())
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	now := time.Now()
	events := []schema.Event{
		{
			DID:          "did-1",
			DataSource:   "test-source",
			EventType:    "INGRESS",
			EventTime:    now,
			IngestTime:   now,
			CreationTime: now,
			Bytes:        1024,
			FileCount:    1,
		},
		{
			DID:          "did-2",
			DataSource:   "test-source",
			EventType:    "EGRESS",
			EventTime:    now,
			IngestTime:   now,
			CreationTime: now,
			Bytes:        2048,
			FileCount:    1,
			FlowName:     "test-flow",
		},
	}

	if err := writer.WriteEvents(events); err != nil {
		t.Fatalf("failed to write events: %v", err)
	}

	// Verify file was created in today's date/hour directory
	today := time.Now().UTC().Format("20060102")
	hour := time.Now().UTC().Format("15")
	files, err := filepath.Glob(filepath.Join(tmpDir, "events", today, hour, "*.parquet"))
	if err != nil {
		t.Fatalf("failed to glob files: %v", err)
	}
	if len(files) != 1 {
		t.Fatalf("expected 1 events parquet file, got %d", len(files))
	}

	// Read back and verify
	file, err := os.Open(files[0])
	if err != nil {
		t.Fatalf("failed to open parquet file: %v", err)
	}
	defer file.Close()

	stat, _ := file.Stat()
	pf, err := parquet.OpenFile(file, stat.Size())
	if err != nil {
		t.Fatalf("failed to parse parquet file: %v", err)
	}

	reader := parquet.NewGenericReader[schema.Event](pf)
	defer reader.Close()

	readEvents := make([]schema.Event, reader.NumRows())
	n, err := reader.Read(readEvents)
	if err != nil && err != io.EOF {
		t.Fatalf("failed to read events: %v", err)
	}
	if n != 2 {
		t.Fatalf("expected 2 events, got %d", n)
	}

	if readEvents[0].DID != "did-1" {
		t.Errorf("expected DID 'did-1', got '%s'", readEvents[0].DID)
	}
	if readEvents[1].Bytes != 2048 {
		t.Errorf("expected bytes 2048, got %d", readEvents[1].Bytes)
	}
}

func TestParquetWriter_WriteAnnotations(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "parquet-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	writer, err := New(Config{OutputDir: tmpDir}, testLogger())
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	now := time.Now()
	annotations := []schema.Annotation{
		{DID: "did-1", Key: "customer", Value: "acme", UpdateTime: now, CreationTime: now},
		{DID: "did-1", Key: "region", Value: "us-east", UpdateTime: now, CreationTime: now},
		{DID: "did-2", Key: "customer", Value: "initech", UpdateTime: now, CreationTime: now},
	}

	if err := writer.WriteAnnotations(annotations); err != nil {
		t.Fatalf("failed to write annotations: %v", err)
	}

	today := time.Now().UTC().Format("20060102")
	hour := time.Now().UTC().Format("15")
	files, err := filepath.Glob(filepath.Join(tmpDir, "annotations", today, hour, "*.parquet"))
	if err != nil {
		t.Fatalf("failed to glob files: %v", err)
	}
	if len(files) != 1 {
		t.Fatalf("expected 1 annotations parquet file, got %d", len(files))
	}

	// Read back and verify
	file, err := os.Open(files[0])
	if err != nil {
		t.Fatalf("failed to open parquet file: %v", err)
	}
	defer file.Close()

	stat, _ := file.Stat()
	pf, err := parquet.OpenFile(file, stat.Size())
	if err != nil {
		t.Fatalf("failed to parse parquet file: %v", err)
	}

	reader := parquet.NewGenericReader[schema.Annotation](pf)
	defer reader.Close()

	readAnnotations := make([]schema.Annotation, reader.NumRows())
	n, err := reader.Read(readAnnotations)
	if err != nil && err != io.EOF {
		t.Fatalf("failed to read annotations: %v", err)
	}
	if n != 3 {
		t.Fatalf("expected 3 annotations, got %d", n)
	}
}

func TestParquetWriter_WriteEmpty(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "parquet-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	writer, err := New(Config{OutputDir: tmpDir}, testLogger())
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	// Writing empty slices should succeed without creating files
	if err := writer.WriteEvents([]schema.Event{}); err != nil {
		t.Fatalf("writing empty events should not error: %v", err)
	}
	if err := writer.WriteAnnotations([]schema.Annotation{}); err != nil {
		t.Fatalf("writing empty annotations should not error: %v", err)
	}

	// No date directories should be created for empty writes
	today := time.Now().UTC().Format("20060102")
	eventsDir := filepath.Join(tmpDir, "events", today)
	if _, err := os.Stat(eventsDir); !os.IsNotExist(err) {
		t.Errorf("expected no events date directory for empty writes")
	}
}

func TestParquetWriter_Directories(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "parquet-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	writer, err := New(Config{OutputDir: tmpDir}, testLogger())
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	expectedEventsDir := filepath.Join(tmpDir, "events")
	expectedAnnotationsDir := filepath.Join(tmpDir, "annotations")
	expectedAggregatedDir := filepath.Join(tmpDir, "aggregated")

	if writer.EventsDir() != expectedEventsDir {
		t.Errorf("expected events dir '%s', got '%s'", expectedEventsDir, writer.EventsDir())
	}
	if writer.AnnotationsDir() != expectedAnnotationsDir {
		t.Errorf("expected annotations dir '%s', got '%s'", expectedAnnotationsDir, writer.AnnotationsDir())
	}
	if writer.AggregatedDir() != expectedAggregatedDir {
		t.Errorf("expected aggregated dir '%s', got '%s'", expectedAggregatedDir, writer.AggregatedDir())
	}

	// Verify directories were created
	for _, dir := range []string{expectedEventsDir, expectedAnnotationsDir, expectedAggregatedDir} {
		if _, err := os.Stat(dir); os.IsNotExist(err) {
			t.Errorf("expected directory %s to exist", dir)
		}
	}
}

func TestParquetWriter_CreateDir(t *testing.T) {
	tmpDir, err := os.MkdirTemp("", "parquet-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	// Use a nested path that doesn't exist
	nestedDir := filepath.Join(tmpDir, "nested", "path", "analytics")

	writer, err := New(Config{OutputDir: nestedDir}, testLogger())
	if err != nil {
		t.Fatalf("failed to create writer with nested path: %v", err)
	}

	if writer.OutputDir() != nestedDir {
		t.Errorf("expected output dir '%s', got '%s'", nestedDir, writer.OutputDir())
	}

	// Verify subdirectories were created
	for _, subdir := range []string{"events", "annotations", "aggregated"} {
		path := filepath.Join(nestedDir, subdir)
		if _, err := os.Stat(path); os.IsNotExist(err) {
			t.Errorf("expected directory %s to be created", path)
		}
	}
}
