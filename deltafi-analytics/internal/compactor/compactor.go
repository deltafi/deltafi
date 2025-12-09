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
 * ABOUTME: Two-stage compaction: raw→preagg (by DID, creation_time partitioned) then preagg→final (event_time partitioned).
 * ABOUTME: Uses smart timestamp checking to only recompact hours with new data.
 */
package compactor

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	_ "github.com/marcboeker/go-duckdb"
)

// Metadata stores distinct values for fast variable queries
type Metadata struct {
	DataSources      []string            `json:"data_sources"`
	FlowNames        []string            `json:"flow_names"`
	AnnotationKeys   []string            `json:"annotation_keys"`
	AnnotationValues map[string][]string `json:"annotation_values"`
	UpdatedAt        time.Time           `json:"updated_at"`
}

type Config struct {
	DataDir          string
	CheckInterval    time.Duration // How often to check for compaction eligibility
	MemoryLimit      string        // DuckDB memory limit (e.g., "1GB")
	ArchiveThreshold time.Duration // How old data must be before archiving (default 72h)
	AgeOffDaysFunc   func() int    // Returns number of days to retain data (0 = disabled)
}

type Compactor struct {
	cfg     Config
	logger  *slog.Logger
	running sync.Mutex
}

func New(cfg Config, logger *slog.Logger) *Compactor {
	if cfg.CheckInterval == 0 {
		cfg.CheckInterval = 1 * time.Minute
	}
	if cfg.MemoryLimit == "" {
		cfg.MemoryLimit = "1GB" // Conservative default to leave room for Go runtime
	}
	if cfg.ArchiveThreshold == 0 {
		cfg.ArchiveThreshold = 72 * time.Hour // 3 days default
	}
	return &Compactor{
		cfg:    cfg,
		logger: logger,
	}
}

// Start begins periodic compaction checks, running immediately then on interval
func (c *Compactor) Start(ctx context.Context) {
	c.logger.Info("compactor started", "check_interval", c.cfg.CheckInterval)

	// Check immediately on startup
	go func() {
		if err := c.CheckAndRun(); err != nil {
			c.logger.Error("initial compaction failed", "error", err)
		}
	}()

	// Then check periodically
	ticker := time.NewTicker(c.cfg.CheckInterval)
	go func() {
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				c.logger.Info("compactor shutting down")
				return
			case <-ticker.C:
				if err := c.CheckAndRun(); err != nil {
					c.logger.Error("compaction failed", "error", err)
				}
			}
		}
	}()
}

// eligibleDates returns all date directories with events for compaction
func (c *Compactor) eligibleDates() ([]string, error) {
	eventsDir := filepath.Join(c.cfg.DataDir, "events")
	entries, err := os.ReadDir(eventsDir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}

	var eligible []string
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		// Skip staging directories
		if strings.HasSuffix(entry.Name(), ".writing") || strings.HasSuffix(entry.Name(), ".deleting") {
			continue
		}
		eligible = append(eligible, entry.Name())
	}

	return eligible, nil
}

// CheckAndRun checks if compaction is needed and runs it
func (c *Compactor) CheckAndRun() error {
	// Skip if already running
	if !c.running.TryLock() {
		c.logger.Debug("compaction already running, skipping")
		return nil
	}
	defer c.running.Unlock()

	// Run age-off before compaction
	if err := c.ageOff(); err != nil {
		c.logger.Error("age-off failed", "error", err)
		// Continue with compaction even if age-off fails
	}

	dates, err := c.eligibleDates()
	if err != nil {
		return err
	}

	if len(dates) == 0 {
		return nil
	}

	c.logger.Debug("compaction check", "eligible_dates", len(dates))
	return c.compact(dates)
}

// compact performs compaction for the given dates using DuckDB
func (c *Compactor) compact(dates []string) error {
	start := time.Now()

	// Create temp directory for DuckDB to spill to disk
	tempDir := filepath.Join(c.cfg.DataDir, ".duckdb_temp")
	if err := os.MkdirAll(tempDir, 0755); err != nil {
		return fmt.Errorf("creating temp dir: %w", err)
	}
	defer os.RemoveAll(tempDir)

	// Open DuckDB with disk-backed temp storage
	dbPath := filepath.Join(tempDir, "compactor.duckdb")
	db, err := sql.Open("duckdb", dbPath)
	if err != nil {
		return fmt.Errorf("opening duckdb: %w", err)
	}
	defer db.Close()

	// Configure memory limits and temp directory for spilling
	db.Exec(fmt.Sprintf("SET memory_limit='%s'", c.cfg.MemoryLimit))
	db.Exec(fmt.Sprintf("SET temp_directory='%s'", tempDir))

	var totalHours int

	for _, date := range dates {
		hours, err := c.compactDate(db, date)
		if err != nil {
			c.logger.Error("failed to compact date", "date", date, "error", err)
			continue
		}
		totalHours += hours
	}

	if totalHours > 0 {
		c.logger.Info("compaction complete",
			"duration", time.Since(start),
			"hours_processed", totalHours,
		)

		// Update global metadata for fast variable queries
		if err := c.updateMetadata(db); err != nil {
			c.logger.Warn("failed to update metadata", "error", err)
		}
	}

	return nil
}

// compactDate processes a single date, only recompacting hours that have changed
func (c *Compactor) compactDate(db *sql.DB, date string) (int, error) {
	// Create output directory for this date
	outputDir := filepath.Join(c.cfg.DataDir, "aggregated", date)
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return 0, fmt.Errorf("creating output dir: %w", err)
	}

	var hoursProcessed int

	for hour := 0; hour < 24; hour++ {
		hourStr := fmt.Sprintf("%02d", hour)

		// Check if this hour needs compaction
		needsCompaction, reason := c.hourNeedsCompaction(date, hourStr)
		if !needsCompaction {
			continue
		}

		c.logger.Debug("hour needs compaction", "date", date, "hour", hourStr, "reason", reason)

		if err := c.compactHour(db, date, hourStr); err != nil {
			c.logger.Warn("hour compaction failed", "date", date, "hour", hourStr, "error", err)
			continue
		}
		hoursProcessed++
	}

	// Clean up empty date directory in events if all hours are archived
	c.cleanupEmptyDateDir(date)

	// Consolidate hourly files into daily file for fully archived dates
	if err := c.consolidateDailyArchive(db, date); err != nil {
		c.logger.Warn("daily consolidation failed", "date", date, "error", err)
	}

	return hoursProcessed, nil
}

// hourNeedsCompaction checks if an hour needs to be recompacted
func (c *Compactor) hourNeedsCompaction(date, hour string) (bool, string) {
	eventsDir := filepath.Join(c.cfg.DataDir, "events", date, hour)
	annoDir := filepath.Join(c.cfg.DataDir, "annotations", date, hour)

	// Check if this date has been archived (daily file exists)
	dailyArchivePath := filepath.Join(c.cfg.DataDir, "aggregated", date+".parquet")
	isArchived := fileExists(dailyArchivePath)

	hasEvents := dirHasParquetFiles(eventsDir)
	hasAnnos := dirHasParquetFiles(annoDir)

	// If archived and no new source files, skip
	if isArchived {
		if !hasEvents && !hasAnnos {
			return false, ""
		}
		// Has late arrivals, needs merge into daily file
		return true, "late_arrivals"
	}

	// Not archived - use hourly directory structure
	aggregatePath := filepath.Join(c.cfg.DataDir, "aggregated", date, hour+".parquet")

	// If no events, skip
	if !hasEvents {
		return false, ""
	}

	// If no aggregate exists, need to compact
	if !fileExists(aggregatePath) {
		return true, "no_aggregate"
	}

	// Get the last processed mtime from metadata (race-safe)
	lastProcessedMtime := c.getLastProcessedMtime(date, hour)

	// Check if events or annotations are newer than last processed
	if dirNewerThan(eventsDir, lastProcessedMtime) {
		return true, "events_newer"
	}
	if dirNewerThan(annoDir, lastProcessedMtime) {
		return true, "annotations_newer"
	}

	return false, ""
}

// compactHour processes a single hour's data through two stages:
// Stage 1: raw → preagg (preserves DID for late annotation joins, creation_time partitioned)
// Stage 2: preagg → final (drops DID, event_time partitioned)
func (c *Compactor) compactHour(db *sql.DB, date, hour string) error {
	// Stage 1 output (preagg)
	preaggDir := filepath.Join(c.cfg.DataDir, "preagg", date)
	if err := os.MkdirAll(preaggDir, 0755); err != nil {
		return fmt.Errorf("creating preagg dir: %w", err)
	}
	preaggPath := filepath.Join(preaggDir, hour+".parquet")

	// Check if this date has been archived (daily file exists)
	dailyArchivePath := filepath.Join(c.cfg.DataDir, "aggregated", date+".parquet")
	isArchived := fileExists(dailyArchivePath)

	// Get event files
	eventsDir := filepath.Join(c.cfg.DataDir, "events", date, hour)
	eventsGlob := filepath.Join(eventsDir, "*.parquet")
	eventFiles, _ := filepath.Glob(eventsGlob)

	// Get annotation files
	annoDir := filepath.Join(c.cfg.DataDir, "annotations", date, hour)
	annoGlob := filepath.Join(annoDir, "*.parquet")
	annoFiles, _ := filepath.Glob(annoGlob)
	hasAnnotations := len(annoFiles) > 0

	if len(eventFiles) == 0 && !isArchived {
		return nil
	}

	eventSize := totalFileSize(eventFiles)
	annoSize := totalFileSize(annoFiles)

	c.logger.Info("compacting hour",
		"date", date,
		"hour", hour,
		"event_files", len(eventFiles),
		"event_size", formatBytes(eventSize),
		"annotation_files", len(annoFiles),
		"annotation_size", formatBytes(annoSize),
		"archived", isArchived,
	)

	// Load events into staging table
	var eventCount int64
	if len(eventFiles) > 0 {
		fileList := "[" + quoteFiles(eventFiles) + "]"
		loadEventsQuery := fmt.Sprintf(`
			CREATE OR REPLACE TABLE events_staging AS
			SELECT * FROM read_parquet(%s)
		`, fileList)

		if _, err := db.Exec(loadEventsQuery); err != nil {
			// Try to find and remove corrupt files
			removed := c.findAndRemoveCorruptFiles(db, eventFiles)
			if removed > 0 {
				// Retry with remaining files
				var validFiles []string
				for _, f := range eventFiles {
					if fileExists(f) {
						validFiles = append(validFiles, f)
					}
				}
				if len(validFiles) == 0 {
					return nil
				}
				fileList = "[" + quoteFiles(validFiles) + "]"
				loadEventsQuery = fmt.Sprintf(`
					CREATE OR REPLACE TABLE events_staging AS
					SELECT * FROM read_parquet(%s)
				`, fileList)
				if _, err := db.Exec(loadEventsQuery); err != nil {
					return fmt.Errorf("loading events after cleanup: %w", err)
				}
			} else {
				return fmt.Errorf("loading events: %w", err)
			}
		}
		db.QueryRow("SELECT COUNT(*) FROM events_staging").Scan(&eventCount)
	} else {
		// No events, create empty staging table
		db.Exec(`CREATE OR REPLACE TABLE events_staging (
			did VARCHAR, event_time TIMESTAMP, data_source VARCHAR,
			event_type VARCHAR, flow_name VARCHAR, action_name VARCHAR,
			cause VARCHAR, ingress_type VARCHAR, bytes BIGINT, file_count BIGINT
		)`)
	}

	// Load annotations for matching DIDs
	if hasAnnotations {
		annoFileList := "[" + quoteFiles(annoFiles) + "]"
		loadAnnotationsQuery := fmt.Sprintf(`
			CREATE OR REPLACE TABLE annotations_staging AS
			SELECT a.* FROM read_parquet(%s) a
			WHERE a.did IN (SELECT DISTINCT did FROM events_staging)
		`, annoFileList)
		if _, err := db.Exec(loadAnnotationsQuery); err != nil {
			c.logger.Warn("loading annotations failed, continuing without", "date", date, "hour", hour, "error", err)
			hasAnnotations = false
		}
	}

	// Stage 1: Aggregate events with annotations, preserving DID for late annotation joins
	var aggregateQuery string
	if hasAnnotations {
		aggregateQuery = `
			CREATE OR REPLACE TABLE preagg_staging AS
			WITH deduped_annotations AS (
				SELECT did, key, LAST(value) as value
				FROM annotations_staging
				GROUP BY did, key
			), pivoted_annotations AS (
				SELECT did, MAP(LIST(key), LIST(value)) AS annotations
				FROM deduped_annotations
				GROUP BY did
			)
			SELECT
				e.did,
				time_bucket(INTERVAL '5 minutes', e.event_time::TIMESTAMP) AS event_time_bucket,
				e.data_source,
				e.event_type,
				e.flow_name,
				e.action_name,
				e.cause,
				e.ingress_type,
				a.annotations,
				COUNT(*) AS event_count,
				SUM(e.bytes) AS total_bytes,
				SUM(e.file_count) AS total_file_count
			FROM events_staging e
			LEFT JOIN pivoted_annotations a ON e.did = a.did
			GROUP BY 1, 2, 3, 4, 5, 6, 7, 8, 9
		`
	} else {
		aggregateQuery = `
			CREATE OR REPLACE TABLE preagg_staging AS
			SELECT
				did,
				time_bucket(INTERVAL '5 minutes', event_time::TIMESTAMP) AS event_time_bucket,
				data_source,
				event_type,
				flow_name,
				action_name,
				cause,
				ingress_type,
				NULL::MAP(VARCHAR, VARCHAR) AS annotations,
				COUNT(*) AS event_count,
				SUM(bytes) AS total_bytes,
				SUM(file_count) AS total_file_count
			FROM events_staging
			GROUP BY 1, 2, 3, 4, 5, 6, 7, 8
		`
	}

	if _, err := db.Exec(aggregateQuery); err != nil {
		return fmt.Errorf("aggregating: %w", err)
	}

	// Check if this hour should be archived (older than threshold)
	shouldArchive := c.isHourOlderThanThreshold(date, hour)

	// If archived, merge with existing preagg file (for late-arriving annotations)
	if isArchived && fileExists(preaggPath) {
		c.logger.Info("merging late arrivals into preagg", "date", date, "hour", hour)
		mergeQuery := fmt.Sprintf(`
			CREATE OR REPLACE TABLE preagg_staging AS
			SELECT
				did,
				event_time_bucket,
				data_source,
				event_type,
				flow_name,
				action_name,
				cause,
				ingress_type,
				annotations,
				SUM(event_count) AS event_count,
				SUM(total_bytes) AS total_bytes,
				SUM(total_file_count) AS total_file_count
			FROM (
				SELECT * FROM preagg_staging
				UNION ALL
				SELECT * FROM read_parquet('%s')
			)
			GROUP BY did, event_time_bucket, data_source, event_type, flow_name, action_name, cause, ingress_type, annotations
		`, preaggPath)
		if _, err := db.Exec(mergeQuery); err != nil {
			c.logger.Warn("preagg merge failed, overwriting", "error", err)
		}
	}

	// Write preagg file atomically (Stage 1 output)
	tmpPath := preaggPath + ".tmp"
	copyQuery := fmt.Sprintf(`
		COPY preagg_staging TO '%s' (FORMAT PARQUET, COMPRESSION SNAPPY)
	`, tmpPath)

	if _, err := db.Exec(copyQuery); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("writing preagg: %w", err)
	}

	if err := os.Rename(tmpPath, preaggPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("renaming preagg temp file: %w", err)
	}

	// Get preagg stats
	var preaggRows int64
	db.QueryRow("SELECT COUNT(*) FROM preagg_staging").Scan(&preaggRows)

	c.logger.Info("stage 1 complete",
		"date", date,
		"hour", hour,
		"input_events", eventCount,
		"preagg_rows", preaggRows,
	)

	// Stage 2: Process preagg into final aggregated files (event_time partitioned)
	if err := c.compactStage2(db, preaggPath); err != nil {
		c.logger.Warn("stage 2 failed", "date", date, "hour", hour, "error", err)
		// Continue with cleanup even if Stage 2 fails - preagg is safe
	}

	// Record the latest source file mtime we processed in per-date metadata
	latestMtime := maxFileMtime(eventFiles, annoFiles)
	if err := c.updateDateMeta(date, hour, latestMtime); err != nil {
		c.logger.Warn("failed to update date metadata", "date", date, "hour", hour, "error", err)
	}

	// Clean up staging tables
	db.Exec("DROP TABLE IF EXISTS events_staging")
	db.Exec("DROP TABLE IF EXISTS annotations_staging")
	db.Exec("DROP TABLE IF EXISTS preagg_staging")

	// Archive: delete source files and preagg when data is old enough or already archived
	if shouldArchive || isArchived {
		if err := c.safeRemoveDir(eventsDir); err != nil {
			c.logger.Warn("failed to remove events dir", "dir", eventsDir, "error", err)
		}
		if err := c.safeRemoveDir(annoDir); err != nil {
			c.logger.Warn("failed to remove annotations dir", "dir", annoDir, "error", err)
		}
		// Also remove preagg file after archival (final aggregates are the source of truth)
		os.Remove(preaggPath)
	}

	return nil
}

// compactStage2 processes a preagg file and writes to event_time-partitioned aggregated files
// The preagg file contains DID-level aggregates; Stage 2 drops DID and partitions by event_time
func (c *Compactor) compactStage2(db *sql.DB, preaggPath string) error {
	// Load preagg into DuckDB
	loadQuery := fmt.Sprintf(`
		CREATE OR REPLACE TABLE stage2_input AS
		SELECT * FROM read_parquet('%s')
	`, preaggPath)
	if _, err := db.Exec(loadQuery); err != nil {
		return fmt.Errorf("loading preagg: %w", err)
	}

	// Find distinct event_time hours in the preagg data
	// Cast to TIMESTAMP to handle both TIMESTAMP and TIMESTAMP WITH TIME ZONE
	rows, err := db.Query(`
		SELECT DISTINCT
			strftime(event_time_bucket::TIMESTAMP, '%Y%m%d') AS date_str,
			strftime(event_time_bucket::TIMESTAMP, '%H') AS hour_str
		FROM stage2_input
		WHERE event_time_bucket IS NOT NULL
		ORDER BY date_str, hour_str
	`)
	if err != nil {
		return fmt.Errorf("finding distinct hours: %w", err)
	}

	var eventTimeHours []struct {
		Date string
		Hour string
	}
	for rows.Next() {
		var date, hour string
		if err := rows.Scan(&date, &hour); err != nil {
			continue
		}
		eventTimeHours = append(eventTimeHours, struct {
			Date string
			Hour string
		}{date, hour})
	}
	rows.Close()

	if len(eventTimeHours) == 0 {
		db.Exec("DROP TABLE IF EXISTS stage2_input")
		return nil
	}

	c.logger.Info("stage 2 processing", "preagg", preaggPath, "event_time_hours", len(eventTimeHours))

	// Process each event_time hour
	for _, eth := range eventTimeHours {
		if err := c.writeAggregatedHour(db, eth.Date, eth.Hour); err != nil {
			c.logger.Warn("failed to write aggregated hour", "date", eth.Date, "hour", eth.Hour, "error", err)
			continue
		}
	}

	db.Exec("DROP TABLE IF EXISTS stage2_input")
	return nil
}

// writeAggregatedHour writes aggregated data for a specific event_time hour
func (c *Compactor) writeAggregatedHour(db *sql.DB, eventDate, eventHour string) error {
	// Create output directory
	outputDir := filepath.Join(c.cfg.DataDir, "aggregated", eventDate)
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return fmt.Errorf("creating aggregated dir: %w", err)
	}
	outputPath := filepath.Join(outputDir, eventHour+".parquet")

	// Aggregate preagg data for this event_time hour (dropping DID)
	hourKey := eventDate + eventHour
	aggregateQuery := fmt.Sprintf(`
		CREATE OR REPLACE TABLE hour_final AS
		SELECT
			event_time_bucket AS bucket,
			data_source,
			event_type,
			flow_name,
			action_name,
			cause,
			ingress_type,
			annotations,
			SUM(event_count) AS event_count,
			SUM(total_bytes) AS total_bytes,
			SUM(total_file_count) AS total_file_count
		FROM stage2_input
		WHERE strftime(event_time_bucket::TIMESTAMP, '%%Y%%m%%d%%H') = '%s'
		GROUP BY 1, 2, 3, 4, 5, 6, 7, 8
	`, hourKey)

	if _, err := db.Exec(aggregateQuery); err != nil {
		return fmt.Errorf("aggregating for %s/%s: %w", eventDate, eventHour, err)
	}

	// For rolling data, we replace the aggregated file (preagg is rebuilt from raw each time)
	// No merge needed - the preagg already contains all data for this creation_time hour

	// Write atomically
	tmpPath := outputPath + ".tmp"
	copyQuery := fmt.Sprintf(`
		COPY hour_final TO '%s' (FORMAT PARQUET, COMPRESSION SNAPPY)
	`, tmpPath)

	if _, err := db.Exec(copyQuery); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("writing aggregated: %w", err)
	}

	if err := os.Rename(tmpPath, outputPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("renaming aggregated: %w", err)
	}

	var rowCount int64
	db.QueryRow("SELECT COUNT(*) FROM hour_final").Scan(&rowCount)
	c.logger.Info("stage 2 complete", "date", eventDate, "hour", eventHour, "rows", rowCount)

	db.Exec("DROP TABLE IF EXISTS hour_final")
	return nil
}

// consolidateDailyArchive consolidates hourly aggregated files into a single daily file
// with hourly bucket resolution (rolled up from 5-min). Only runs for fully archived dates.
func (c *Compactor) consolidateDailyArchive(db *sql.DB, date string) error {
	// Only consolidate dates older than the archive threshold
	if !c.isDateOlderThanThreshold(date) {
		return nil
	}

	aggregatedDir := filepath.Join(c.cfg.DataDir, "aggregated", date)
	dailyFile := filepath.Join(c.cfg.DataDir, "aggregated", date+".parquet")

	// Skip if daily file already exists
	if fileExists(dailyFile) {
		return nil
	}

	// Check if the hourly directory exists
	if _, err := os.Stat(aggregatedDir); os.IsNotExist(err) {
		return nil
	}

	// Find all hourly parquet files
	hourlyGlob := filepath.Join(aggregatedDir, "*.parquet")
	hourlyFiles, err := filepath.Glob(hourlyGlob)
	if err != nil || len(hourlyFiles) == 0 {
		return nil
	}

	c.logger.Info("consolidating daily archive", "date", date, "hourly_files", len(hourlyFiles))

	// Load all hourly files
	loadQuery := fmt.Sprintf(`
		CREATE OR REPLACE TABLE daily_staging AS
		SELECT * FROM read_parquet('%s/*.parquet')
	`, aggregatedDir)
	if _, err := db.Exec(loadQuery); err != nil {
		return fmt.Errorf("loading hourly files: %w", err)
	}

	// Re-aggregate from 5-min buckets to hourly buckets
	aggregateQuery := `
		CREATE OR REPLACE TABLE daily_final AS
		SELECT
			time_bucket(INTERVAL '1 hour', bucket) AS bucket,
			data_source,
			event_type,
			flow_name,
			action_name,
			cause,
			ingress_type,
			annotations,
			SUM(event_count) AS event_count,
			SUM(total_bytes) AS total_bytes,
			SUM(total_file_count) AS total_file_count
		FROM daily_staging
		GROUP BY 1, 2, 3, 4, 5, 6, 7, 8
	`
	if _, err := db.Exec(aggregateQuery); err != nil {
		return fmt.Errorf("aggregating to hourly buckets: %w", err)
	}

	// Write daily file atomically
	tmpPath := dailyFile + ".tmp"
	copyQuery := fmt.Sprintf(`
		COPY daily_final TO '%s' (FORMAT PARQUET, COMPRESSION SNAPPY)
	`, tmpPath)
	if _, err := db.Exec(copyQuery); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("writing daily file: %w", err)
	}

	if err := os.Rename(tmpPath, dailyFile); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("renaming daily temp file: %w", err)
	}

	// Get stats
	var rowCount int64
	db.QueryRow("SELECT COUNT(*) FROM daily_final").Scan(&rowCount)

	// Clean up staging tables
	db.Exec("DROP TABLE IF EXISTS daily_staging")
	db.Exec("DROP TABLE IF EXISTS daily_final")

	// Delete hourly directory (contains all hourly files and markers)
	if err := os.RemoveAll(aggregatedDir); err != nil {
		c.logger.Warn("failed to remove hourly directory", "dir", aggregatedDir, "error", err)
	}

	c.logger.Info("daily archive consolidated", "date", date, "rows", rowCount)
	return nil
}

// cleanupEmptyDateDir removes empty date directories from events/annotations/preagg
func (c *Compactor) cleanupEmptyDateDir(date string) {
	eventsDateDir := filepath.Join(c.cfg.DataDir, "events", date)
	annoDateDir := filepath.Join(c.cfg.DataDir, "annotations", date)
	preaggDateDir := filepath.Join(c.cfg.DataDir, "preagg", date)

	// Check if events date dir is empty (no hour subdirs with files)
	if entries, err := os.ReadDir(eventsDateDir); err == nil {
		isEmpty := true
		for _, entry := range entries {
			if entry.IsDir() && !strings.HasSuffix(entry.Name(), ".deleting") {
				hourDir := filepath.Join(eventsDateDir, entry.Name())
				if dirHasParquetFiles(hourDir) {
					isEmpty = false
					break
				}
			}
		}
		if isEmpty {
			os.RemoveAll(eventsDateDir)
		}
	}

	// Same for annotations
	if entries, err := os.ReadDir(annoDateDir); err == nil {
		isEmpty := true
		for _, entry := range entries {
			if entry.IsDir() && !strings.HasSuffix(entry.Name(), ".deleting") {
				hourDir := filepath.Join(annoDateDir, entry.Name())
				if dirHasParquetFiles(hourDir) {
					isEmpty = false
					break
				}
			}
		}
		if isEmpty {
			os.RemoveAll(annoDateDir)
		}
	}

	// Same for preagg (check for parquet files directly, not subdirs)
	if entries, err := os.ReadDir(preaggDateDir); err == nil {
		hasFiles := false
		for _, entry := range entries {
			if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".parquet") {
				hasFiles = true
				break
			}
		}
		if !hasFiles {
			os.RemoveAll(preaggDateDir)
		}
	}
}

// findAndRemoveCorruptFiles identifies corrupt parquet files using binary search and removes them
func (c *Compactor) findAndRemoveCorruptFiles(db *sql.DB, files []string) int {
	if len(files) == 0 {
		return 0
	}

	removed := 0
	corruptFiles := c.findCorruptFiles(db, files, 0, len(files)-1)

	for _, file := range corruptFiles {
		c.logger.Warn("removing corrupt parquet file", "file", file)
		if err := os.Remove(file); err != nil {
			if !os.IsNotExist(err) {
				c.logger.Error("failed to remove corrupt file", "file", file, "error", err)
			}
		} else {
			removed++
		}
	}

	return removed
}

// findCorruptFiles recursively searches for corrupt files using binary search
func (c *Compactor) findCorruptFiles(db *sql.DB, files []string, low, high int) []string {
	if low > high {
		return nil
	}

	if low == high {
		query := fmt.Sprintf("SELECT COUNT(*) FROM read_parquet('%s')", files[low])
		var count int64
		if err := db.QueryRow(query).Scan(&count); err != nil {
			c.logger.Debug("file read failed", "file", files[low], "error", err)
			return []string{files[low]}
		}
		return nil
	}

	mid := (low + high) / 2
	var corruptFiles []string

	// Test low..mid range
	lowFiles := files[low : mid+1]
	queryLow := "SELECT COUNT(*) FROM read_parquet([" + quoteFiles(lowFiles) + "])"
	var count int64
	if err := db.QueryRow(queryLow).Scan(&count); err != nil {
		corruptFiles = append(corruptFiles, c.findCorruptFiles(db, files, low, mid)...)
	}

	// Test mid+1..high range
	if mid+1 <= high {
		highFiles := files[mid+1 : high+1]
		queryHigh := "SELECT COUNT(*) FROM read_parquet([" + quoteFiles(highFiles) + "])"
		if err := db.QueryRow(queryHigh).Scan(&count); err != nil {
			corruptFiles = append(corruptFiles, c.findCorruptFiles(db, files, mid+1, high)...)
		}
	}

	return corruptFiles
}

func quoteFiles(files []string) string {
	result := ""
	for i, f := range files {
		if i > 0 {
			result += ", "
		}
		result += "'" + f + "'"
	}
	return result
}

// isHourOlderThanThreshold checks if a specific hour is older than the archive threshold
func (c *Compactor) isHourOlderThanThreshold(dateStr, hourStr string) bool {
	t, err := time.Parse("2006010215", dateStr+hourStr)
	if err != nil {
		return false
	}
	threshold := time.Now().Add(-c.cfg.ArchiveThreshold)
	return t.Before(threshold)
}

// isDateOlderThanThreshold checks if a date is older than the archive threshold (end of day)
func (c *Compactor) isDateOlderThanThreshold(dateStr string) bool {
	t, err := time.Parse("20060102", dateStr)
	if err != nil {
		return false
	}
	// Check if end of day (23:59:59) is older than threshold
	endOfDay := t.Add(24*time.Hour - time.Second)
	threshold := time.Now().Add(-c.cfg.ArchiveThreshold)
	return endOfDay.Before(threshold)
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

// dirHasParquetFiles checks if directory has any .parquet files
func dirHasParquetFiles(dir string) bool {
	pattern := filepath.Join(dir, "*.parquet")
	files, _ := filepath.Glob(pattern)
	return len(files) > 0
}

// dirNewerThan checks if any file in directory is newer than given time
func dirNewerThan(dir string, t time.Time) bool {
	pattern := filepath.Join(dir, "*.parquet")
	files, _ := filepath.Glob(pattern)
	for _, f := range files {
		if info, err := os.Stat(f); err == nil {
			if info.ModTime().After(t) {
				return true
			}
		}
	}
	return false
}

// totalFileSize returns total size of files in bytes
func totalFileSize(files []string) int64 {
	var total int64
	for _, f := range files {
		if info, err := os.Stat(f); err == nil {
			total += info.Size()
		}
	}
	return total
}

// formatBytes formats bytes as human-readable string
func formatBytes(bytes int64) string {
	const unit = 1024
	if bytes < unit {
		return fmt.Sprintf("%d B", bytes)
	}
	div, exp := int64(unit), 0
	for n := bytes / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(bytes)/float64(div), "KMGTPE"[exp])
}

// ageOff removes data directories older than the configured retention period
func (c *Compactor) ageOff() error {
	if c.cfg.AgeOffDaysFunc == nil {
		return nil
	}

	days := c.cfg.AgeOffDaysFunc()
	if days <= 0 {
		return nil
	}

	cutoff := time.Now().AddDate(0, 0, -days)
	cutoffDate := cutoff.Format("20060102")

	// Directories to age off: events, annotations, preagg (date/hour structure)
	// and aggregated (date structure for both hourly dirs and daily files)
	dateDirs := []string{"events", "annotations", "preagg"}
	for _, dir := range dateDirs {
		basePath := filepath.Join(c.cfg.DataDir, dir)
		entries, err := os.ReadDir(basePath)
		if err != nil {
			if os.IsNotExist(err) {
				continue
			}
			return fmt.Errorf("reading %s: %w", dir, err)
		}

		for _, entry := range entries {
			if !entry.IsDir() {
				continue
			}
			// Directory name is YYYYMMDD
			if entry.Name() < cutoffDate {
				path := filepath.Join(basePath, entry.Name())
				c.logger.Info("aging off old data", "path", path, "cutoff", cutoffDate)
				if err := os.RemoveAll(path); err != nil {
					c.logger.Error("failed to remove old directory", "path", path, "error", err)
				}
			}
		}
	}

	// Handle aggregated directory - has hourly subdirs (YYYYMMDD/HH/) and daily files (YYYYMMDD.parquet)
	aggregatedPath := filepath.Join(c.cfg.DataDir, "aggregated")
	entries, err := os.ReadDir(aggregatedPath)
	if err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("reading aggregated: %w", err)
	}
	for _, entry := range entries {
		name := entry.Name()
		var dateStr string
		if entry.IsDir() {
			// Hourly directory: YYYYMMDD
			dateStr = name
		} else if strings.HasSuffix(name, ".parquet") {
			// Daily file: YYYYMMDD.parquet
			dateStr = strings.TrimSuffix(name, ".parquet")
		} else {
			continue
		}

		if len(dateStr) == 8 && dateStr < cutoffDate {
			path := filepath.Join(aggregatedPath, name)
			c.logger.Info("aging off old aggregated data", "path", path, "cutoff", cutoffDate)
			if err := os.RemoveAll(path); err != nil {
				c.logger.Error("failed to remove old aggregated data", "path", path, "error", err)
			}
		}
	}

	return nil
}

// safeRemoveDir safely removes a directory by:
// 1. Renaming to .deleting suffix (atomic move out of active path)
// 2. Verifying no .tmp files exist (indicating active writes)
// 3. Deleting the renamed directory
func (c *Compactor) safeRemoveDir(dir string) error {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return nil // Already gone
	}

	deletingPath := dir + ".deleting"

	// Clean up any leftover .deleting directory from previous run
	os.RemoveAll(deletingPath)

	// Atomically move directory out of active path
	if err := os.Rename(dir, deletingPath); err != nil {
		if os.IsNotExist(err) {
			return nil // Directory was removed by another process
		}
		return fmt.Errorf("renaming dir for deletion: %w", err)
	}

	// Check for active writes (.tmp files indicate in-progress writes)
	// Stale tmp files (older than 1 minute) are cleaned up automatically
	hasActiveWrites := false
	staleThreshold := time.Now().Add(-1 * time.Minute)
	filepath.Walk(deletingPath, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if strings.HasSuffix(path, ".tmp") {
			if info.ModTime().Before(staleThreshold) {
				// Stale tmp file from crashed/killed process - clean it up
				c.logger.Info("removing stale tmp file", "path", path, "age", time.Since(info.ModTime()))
				os.Remove(path)
			} else {
				// Recent tmp file - active write in progress
				hasActiveWrites = true
			}
		}
		return nil
	})

	if hasActiveWrites {
		// Restore directory - writes are in progress
		c.logger.Warn("active writes detected, restoring directory", "dir", dir)
		if err := os.Rename(deletingPath, dir); err != nil {
			c.logger.Error("failed to restore directory", "dir", dir, "error", err)
		}
		return fmt.Errorf("active writes detected in %s", dir)
	}

	// Safe to delete
	if err := os.RemoveAll(deletingPath); err != nil {
		return fmt.Errorf("removing dir: %w", err)
	}

	return nil
}

const metadataValueLimit = 500 // Max values per annotation key in metadata.json

// updateMetadata scans all aggregated parquet files and updates metadata.json
func (c *Compactor) updateMetadata(db *sql.DB) error {
	// New glob pattern for hourly files: aggregated/*/*.parquet
	aggregatedGlob := filepath.Join(c.cfg.DataDir, "aggregated", "*", "*.parquet")
	files, _ := filepath.Glob(aggregatedGlob)
	if len(files) == 0 {
		return nil
	}

	meta := Metadata{
		AnnotationValues: make(map[string][]string),
		UpdatedAt:        time.Now(),
	}

	// Get distinct data sources
	dataSources, err := c.queryDistinct(db, "data_source", aggregatedGlob)
	if err != nil {
		c.logger.Warn("failed to get data sources for metadata", "error", err)
	} else {
		meta.DataSources = dataSources
	}

	// Get distinct flow names
	flowNames, err := c.queryDistinct(db, "flow_name", aggregatedGlob)
	if err != nil {
		c.logger.Warn("failed to get flow names for metadata", "error", err)
	} else {
		meta.FlowNames = flowNames
	}

	// Get distinct annotation keys
	annotationKeys, err := c.queryAnnotationKeys(db, aggregatedGlob)
	if err != nil {
		c.logger.Warn("failed to get annotation keys for metadata", "error", err)
	} else {
		meta.AnnotationKeys = annotationKeys
	}

	// Get distinct values for each annotation key (with limit)
	for _, key := range meta.AnnotationKeys {
		values, err := c.queryAnnotationValues(db, aggregatedGlob, key)
		if err != nil {
			c.logger.Warn("failed to get annotation values for metadata", "key", key, "error", err)
			continue
		}
		if len(values) > metadataValueLimit {
			values = values[:metadataValueLimit]
			c.logger.Debug("truncated annotation values in metadata", "key", key, "limit", metadataValueLimit)
		}
		meta.AnnotationValues[key] = values
	}

	// Write metadata file atomically
	metadataPath := filepath.Join(c.cfg.DataDir, "aggregated", "metadata.json")
	tmpPath := metadataPath + ".tmp"

	data, err := json.MarshalIndent(meta, "", "  ")
	if err != nil {
		return fmt.Errorf("marshaling metadata: %w", err)
	}

	if err := os.WriteFile(tmpPath, data, 0644); err != nil {
		return fmt.Errorf("writing metadata: %w", err)
	}

	if err := os.Rename(tmpPath, metadataPath); err != nil {
		os.Remove(tmpPath)
		return fmt.Errorf("renaming metadata: %w", err)
	}

	c.logger.Info("metadata updated",
		"data_sources", len(meta.DataSources),
		"flow_names", len(meta.FlowNames),
		"annotation_keys", len(meta.AnnotationKeys),
	)

	return nil
}

// queryDistinct returns distinct values for a field from aggregated parquet files
func (c *Compactor) queryDistinct(db *sql.DB, field, glob string) ([]string, error) {
	query := fmt.Sprintf("SELECT DISTINCT %s FROM read_parquet('%s') WHERE %s IS NOT NULL AND %s != '' ORDER BY %s",
		field, glob, field, field, field)

	rows, err := db.Query(query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var values []string
	for rows.Next() {
		var val string
		if err := rows.Scan(&val); err != nil {
			continue
		}
		values = append(values, val)
	}
	return values, nil
}

// queryAnnotationKeys returns distinct annotation keys from aggregated parquet files
func (c *Compactor) queryAnnotationKeys(db *sql.DB, glob string) ([]string, error) {
	query := fmt.Sprintf("SELECT DISTINCT unnest(map_keys(annotations)) AS key FROM read_parquet('%s') WHERE annotations IS NOT NULL ORDER BY key", glob)

	rows, err := db.Query(query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var keys []string
	for rows.Next() {
		var key string
		if err := rows.Scan(&key); err != nil {
			continue
		}
		if key != "" {
			keys = append(keys, key)
		}
	}
	sort.Strings(keys)
	return keys, nil
}

// queryAnnotationValues returns distinct values for an annotation key from aggregated parquet files
func (c *Compactor) queryAnnotationValues(db *sql.DB, glob, key string) ([]string, error) {
	query := fmt.Sprintf("SELECT DISTINCT annotations['%s'][1] AS value FROM read_parquet('%s') WHERE annotations IS NOT NULL AND annotations['%s'] IS NOT NULL ORDER BY value",
		key, glob, key)

	rows, err := db.Query(query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var values []string
	for rows.Next() {
		var val string
		if err := rows.Scan(&val); err != nil {
			continue
		}
		if val != "" {
			values = append(values, val)
		}
	}
	sort.Strings(values)
	return values, nil
}

// DateMeta stores per-hour compaction metadata for a date
type DateMeta struct {
	// HourMtimes maps hour ("00"-"23") to the unix timestamp of the latest source file processed
	HourMtimes map[string]int64 `json:"hour_mtimes"`
}

// getLastProcessedMtime returns the mtime of the last processed source files for an hour
// Falls back to aggregate file mtime if no metadata exists (backwards compatibility)
func (c *Compactor) getLastProcessedMtime(date, hour string) time.Time {
	metaPath := filepath.Join(c.cfg.DataDir, "aggregated", date, "meta.json")
	data, err := os.ReadFile(metaPath)
	if err != nil {
		// No metadata file - fall back to aggregate mtime for backwards compatibility
		aggregatePath := filepath.Join(c.cfg.DataDir, "aggregated", date, hour+".parquet")
		if info, err := os.Stat(aggregatePath); err == nil {
			return info.ModTime()
		}
		return time.Time{} // Zero time - will trigger compaction
	}

	var meta DateMeta
	if err := json.Unmarshal(data, &meta); err != nil {
		return time.Time{}
	}

	if mtime, ok := meta.HourMtimes[hour]; ok {
		return time.Unix(0, mtime)
	}
	return time.Time{}
}

// updateDateMeta updates the per-date metadata file with the latest processed mtime for an hour
func (c *Compactor) updateDateMeta(date, hour string, mtime time.Time) error {
	metaPath := filepath.Join(c.cfg.DataDir, "aggregated", date, "meta.json")

	// Read existing metadata or create new
	var meta DateMeta
	if data, err := os.ReadFile(metaPath); err == nil {
		json.Unmarshal(data, &meta)
	}
	if meta.HourMtimes == nil {
		meta.HourMtimes = make(map[string]int64)
	}

	// Update the hour's mtime
	meta.HourMtimes[hour] = mtime.UnixNano()

	// Write atomically
	data, err := json.Marshal(meta)
	if err != nil {
		return err
	}

	tmpPath := metaPath + ".tmp"
	if err := os.WriteFile(tmpPath, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmpPath, metaPath)
}

// maxFileMtime returns the maximum mtime from all files in the provided lists
func maxFileMtime(fileLists ...[]string) time.Time {
	var maxTime time.Time
	for _, files := range fileLists {
		for _, f := range files {
			if info, err := os.Stat(f); err == nil {
				if info.ModTime().After(maxTime) {
					maxTime = info.ModTime()
				}
			}
		}
	}
	return maxTime
}
