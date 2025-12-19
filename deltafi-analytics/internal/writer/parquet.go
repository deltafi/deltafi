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
 * ABOUTME: Writes analytics events and annotations to Parquet files.
 * ABOUTME: Uses hourly directory structure (YYYYMMDD/HH) for memory-efficient compaction.
 */
package writer

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"time"

	"github.com/parquet-go/parquet-go"

	"deltafi.org/deltafi-analytics/internal/schema"
)

// ParquetWriter writes analytics events and annotations to Parquet files
type ParquetWriter struct {
	outputDir string
	logger    *slog.Logger
}

// Config holds writer configuration
type Config struct {
	OutputDir string
}

// New creates a new ParquetWriter
func New(cfg Config, logger *slog.Logger) (*ParquetWriter, error) {
	if cfg.OutputDir == "" {
		cfg.OutputDir = "/data/analytics"
	}

	// Create subdirectories
	for _, subdir := range []string{"events", "annotations", "aggregated", "provenance/raw", "provenance/compacted"} {
		if err := os.MkdirAll(filepath.Join(cfg.OutputDir, subdir), 0755); err != nil {
			return nil, fmt.Errorf("failed to create %s directory: %w", subdir, err)
		}
	}

	return &ParquetWriter{
		outputDir: cfg.OutputDir,
		logger:    logger,
	}, nil
}

// WriteEvents writes a batch of events to Parquet files, partitioned by DeltaFile creation time.
// Directory structure: events/YYYYMMDD/HH/filename.parquet
// This enables the compactor to process hour-by-hour for memory efficiency.
func (w *ParquetWriter) WriteEvents(events []schema.Event) error {
	if len(events) == 0 {
		return nil
	}

	// Group events by their CreationTime date and hour (DeltaFile creation time)
	// Key format: "YYYYMMDD/HH"
	eventsByDateHour := make(map[string][]schema.Event)
	for _, e := range events {
		dateHourKey := e.CreationTime.UTC().Format("20060102/15")
		eventsByDateHour[dateHourKey] = append(eventsByDateHour[dateHourKey], e)
	}

	// Write a separate file for each date/hour
	now := time.Now().UTC()
	for dateHourKey, hourEvents := range eventsByDateHour {
		if err := w.writeEventsForDateHour(dateHourKey, hourEvents, now); err != nil {
			return err
		}
	}

	return nil
}

// writeEventsForDateHour writes events for a single date/hour partition
func (w *ParquetWriter) writeEventsForDateHour(dateHourKey string, events []schema.Event, now time.Time) error {
	// dateHourKey format: "YYYYMMDD/HH"
	hourDir := filepath.Join(w.outputDir, "events", dateHourKey)
	if err := os.MkdirAll(hourDir, 0755); err != nil {
		return fmt.Errorf("failed to create events hour directory: %w", err)
	}

	filename := fmt.Sprintf("%s_%d.parquet", now.Format("150405"), now.UnixNano()%1000000)
	finalPath := filepath.Join(hourDir, filename)
	tmpPath := filepath.Join(hourDir, "."+filename+".tmp")

	w.logger.Info("writing events parquet file", "path", finalPath, "count", len(events))

	// Write to temp file first
	file, err := os.Create(tmpPath)
	if err != nil {
		return fmt.Errorf("failed to create temp file: %w", err)
	}

	writer := parquet.NewGenericWriter[schema.Event](file,
		parquet.Compression(&parquet.Snappy),
	)

	if _, err := writer.Write(events); err != nil {
		file.Close()
		os.Remove(tmpPath)
		return fmt.Errorf("failed to write events: %w", err)
	}

	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpPath)
		return fmt.Errorf("failed to close writer: %w", err)
	}

	if err := file.Close(); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to close file: %w", err)
	}

	// Atomic rename to final path
	if err := os.Rename(tmpPath, finalPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to rename temp file: %w", err)
	}

	w.logger.Info("events parquet file written", "path", finalPath, "count", len(events))
	return nil
}

// WriteAnnotations writes a batch of annotations to Parquet files, partitioned by creationTime date and hour.
// Directory structure: annotations/YYYYMMDD/HH/filename.parquet
// This enables the compactor to load only hour-specific annotation files when processing event chunks.
func (w *ParquetWriter) WriteAnnotations(annotations []schema.Annotation) error {
	if len(annotations) == 0 {
		return nil
	}

	// Group annotations by their CreationTime date and hour
	// Key format: "YYYYMMDD/HH"
	annotationsByDateHour := make(map[string][]schema.Annotation)
	for _, a := range annotations {
		// Use CreationTime for partitioning, fall back to UpdateTime if not set
		partitionTime := a.CreationTime
		if partitionTime.IsZero() {
			partitionTime = a.UpdateTime
		}
		dateHourKey := partitionTime.UTC().Format("20060102/15")
		annotationsByDateHour[dateHourKey] = append(annotationsByDateHour[dateHourKey], a)
	}

	// Write a separate file for each date/hour
	now := time.Now().UTC()
	for dateHourKey, hourAnnotations := range annotationsByDateHour {
		if err := w.writeAnnotationsForDateHour(dateHourKey, hourAnnotations, now); err != nil {
			return err
		}
	}

	return nil
}

// writeAnnotationsForDateHour writes annotations for a single date/hour partition
func (w *ParquetWriter) writeAnnotationsForDateHour(dateHourKey string, annotations []schema.Annotation, now time.Time) error {
	// dateHourKey format: "YYYYMMDD/HH"
	hourDir := filepath.Join(w.outputDir, "annotations", dateHourKey)
	if err := os.MkdirAll(hourDir, 0755); err != nil {
		return fmt.Errorf("failed to create annotations hour directory: %w", err)
	}

	filename := fmt.Sprintf("%s_%d.parquet", now.Format("150405"), now.UnixNano()%1000000)
	finalPath := filepath.Join(hourDir, filename)
	tmpPath := filepath.Join(hourDir, "."+filename+".tmp")

	w.logger.Info("writing annotations parquet file", "path", finalPath, "count", len(annotations))

	// Write to temp file first
	file, err := os.Create(tmpPath)
	if err != nil {
		return fmt.Errorf("failed to create temp file: %w", err)
	}

	writer := parquet.NewGenericWriter[schema.Annotation](file,
		parquet.Compression(&parquet.Snappy),
	)

	if _, err := writer.Write(annotations); err != nil {
		file.Close()
		os.Remove(tmpPath)
		return fmt.Errorf("failed to write annotations: %w", err)
	}

	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpPath)
		return fmt.Errorf("failed to close writer: %w", err)
	}

	if err := file.Close(); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to close file: %w", err)
	}

	// Atomic rename to final path
	if err := os.Rename(tmpPath, finalPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to rename temp file: %w", err)
	}

	w.logger.Info("annotations parquet file written", "path", finalPath, "count", len(annotations))
	return nil
}

// OutputDir returns the configured output directory
func (w *ParquetWriter) OutputDir() string {
	return w.outputDir
}

// EventsDir returns the events directory
func (w *ParquetWriter) EventsDir() string {
	return filepath.Join(w.outputDir, "events")
}

// AnnotationsDir returns the annotations directory
func (w *ParquetWriter) AnnotationsDir() string {
	return filepath.Join(w.outputDir, "annotations")
}

// AggregatedDir returns the aggregated directory
func (w *ParquetWriter) AggregatedDir() string {
	return filepath.Join(w.outputDir, "aggregated")
}

// ProvenanceRawDir returns the provenance raw directory
func (w *ParquetWriter) ProvenanceRawDir() string {
	return filepath.Join(w.outputDir, "provenance", "raw")
}

// ProvenanceCompactedDir returns the provenance compacted directory
func (w *ParquetWriter) ProvenanceCompactedDir() string {
	return filepath.Join(w.outputDir, "provenance", "compacted")
}

// WriteProvenance writes a batch of provenance records to Parquet files, partitioned by completed time.
// Directory structure: provenance/raw/YYYYMMDD/HH/filename.parquet
func (w *ParquetWriter) WriteProvenance(records []schema.Provenance) error {
	if len(records) == 0 {
		return nil
	}

	// Group records by their Completed time date and hour
	// Key format: "YYYYMMDD/HH"
	recordsByDateHour := make(map[string][]schema.Provenance)
	for _, r := range records {
		dateHourKey := r.Completed.UTC().Format("20060102/15")
		recordsByDateHour[dateHourKey] = append(recordsByDateHour[dateHourKey], r)
	}

	// Write a separate file for each date/hour
	now := time.Now().UTC()
	for dateHourKey, hourRecords := range recordsByDateHour {
		if err := w.writeProvenanceForDateHour(dateHourKey, hourRecords, now); err != nil {
			return err
		}
	}

	return nil
}

// writeProvenanceForDateHour writes provenance records for a single date/hour partition
func (w *ParquetWriter) writeProvenanceForDateHour(dateHourKey string, records []schema.Provenance, now time.Time) error {
	// dateHourKey format: "YYYYMMDD/HH"
	hourDir := filepath.Join(w.outputDir, "provenance", "raw", dateHourKey)
	if err := os.MkdirAll(hourDir, 0755); err != nil {
		return fmt.Errorf("failed to create provenance hour directory: %w", err)
	}

	filename := fmt.Sprintf("%s_%d.parquet", now.Format("150405"), now.UnixNano()%1000000)
	finalPath := filepath.Join(hourDir, filename)
	tmpPath := filepath.Join(hourDir, "."+filename+".tmp")

	w.logger.Info("writing provenance parquet file", "path", finalPath, "count", len(records))

	// Write to temp file first
	file, err := os.Create(tmpPath)
	if err != nil {
		return fmt.Errorf("failed to create temp file: %w", err)
	}

	writer := parquet.NewGenericWriter[schema.Provenance](file,
		parquet.Compression(&parquet.Snappy),
	)

	if _, err := writer.Write(records); err != nil {
		file.Close()
		os.Remove(tmpPath)
		return fmt.Errorf("failed to write provenance: %w", err)
	}

	if err := writer.Close(); err != nil {
		file.Close()
		os.Remove(tmpPath)
		return fmt.Errorf("failed to close writer: %w", err)
	}

	if err := file.Close(); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to close file: %w", err)
	}

	// Atomic rename to final path
	if err := os.Rename(tmpPath, finalPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("failed to rename temp file: %w", err)
	}

	w.logger.Info("provenance parquet file written", "path", finalPath, "count", len(records))
	return nil
}
