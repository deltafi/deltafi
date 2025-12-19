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
// ABOUTME: DuckDB-based query service for analytics data
// ABOUTME: Provides analytics query interface over Parquet files for Grafana dashboards

package query

import (
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

// Metadata stores distinct values for fast variable queries (mirrors compactor.Metadata)
type Metadata struct {
	DataSources      []string            `json:"data_sources"`
	FlowNames        []string            `json:"flow_names"`
	AnnotationKeys   []string            `json:"annotation_keys"`
	AnnotationValues map[string][]string `json:"annotation_values"`
	UpdatedAt        time.Time           `json:"updated_at"`
}

// LoadMetadata reads the pre-computed metadata file for fast variable queries
func (s *Service) LoadMetadata() (*Metadata, error) {
	metadataPath := filepath.Join(s.dataDir, "aggregated", "metadata.json")
	data, err := os.ReadFile(metadataPath)
	if err != nil {
		return nil, err
	}
	var meta Metadata
	if err := json.Unmarshal(data, &meta); err != nil {
		return nil, err
	}
	return &meta, nil
}

// ConfigProvider provides access to core configuration
type ConfigProvider interface {
	GetAnalyticsGroupName() string
}

type Service struct {
	db         *sql.DB
	dataDir    string
	logger     *slog.Logger
	coreConfig ConfigProvider
	mu         sync.RWMutex

	// Cache for annotation queries (reduces redundant parquet scans)
	cacheMu             sync.RWMutex
	annotationKeysCache *cacheEntry
	annotationValCache  map[string]*cacheEntry // keyed by "annotationKey|timeFrom|timeTo"
}

type cacheEntry struct {
	data    []string
	expires time.Time
}

const cacheTTL = 30 * time.Second

func NewService(dataDir string, coreConfig ConfigProvider, logger *slog.Logger) (*Service, error) {
	db, err := sql.Open("duckdb", "")
	if err != nil {
		return nil, fmt.Errorf("failed to open duckdb: %w", err)
	}

	return &Service{
		db:                 db,
		dataDir:            dataDir,
		coreConfig:         coreConfig,
		logger:             logger,
		annotationValCache: make(map[string]*cacheEntry),
	}, nil
}

func (s *Service) Close() error {
	return s.db.Close()
}

// AnalyticsRequest represents parameters for analytics queries
type AnalyticsRequest struct {
	TimeFrom          time.Time           `json:"timeFrom"`
	TimeTo            time.Time           `json:"timeTo"`
	IntervalMs        int64               `json:"intervalMs"`
	DataSources       []string            `json:"dataSources"`
	FlowNames         []string            `json:"flowNames"`
	Annotations       map[string][]string `json:"annotations"`       // Filter by multiple annotations (key -> allowed values)
	GroupByAnnotation string              `json:"groupByAnnotation"` // Which annotation to use for grouping in results
	IngressTypes      []string            `json:"ingressTypes"`
	EventType         string              `json:"eventType"` // "ALL", "ERRORS", "FILTERS"
}

// EventAnalysisRequest represents parameters for error/filter analysis queries
type EventAnalysisRequest struct {
	TimeFrom    time.Time           `json:"timeFrom"`
	TimeTo      time.Time           `json:"timeTo"`
	IntervalMs  int64               `json:"intervalMs"`
	DataSources []string            `json:"dataSources"`
	Annotations map[string][]string `json:"annotations"` // Filter by annotations
	GroupBy     string              `json:"groupBy"`     // data_source, cause, action_name, flow_name, or annotation key
}

// EventAnalysisRow represents a single error/filter analysis result row
type EventAnalysisRow struct {
	Time       int64  `json:"time"`        // Epoch milliseconds for Grafana compatibility
	GroupValue string `json:"group_value"` // Value of the groupBy field
	Count      int64  `json:"count"`       // Number of events
}

// AnalyticsRow represents a single analytics result row
type AnalyticsRow struct {
	Time            int64  `json:"time"` // Epoch milliseconds for Grafana compatibility
	DataSource      string `json:"data_source"`
	AnnotationValue string `json:"annotation_value"`
	IngressBytes    int64  `json:"ingress_bytes"`
	IngressFiles    int64  `json:"ingress_files"`
	EgressBytes     int64  `json:"egress_bytes"`
	EgressFiles     int64  `json:"egress_files"`
	ErrorFiles      int64  `json:"error_files"`
	FilterFiles     int64  `json:"filter_files"`
}

// QueryAnalytics executes an analytics query across both aggregated and recent data
func (s *Service) QueryAnalytics(req AnalyticsRequest) ([]AnalyticsRow, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	// Build the query to UNION aggregated and recent data
	query := s.buildAnalyticsQuery(req)

	s.logger.Debug("executing analytics query", "query", query)

	rows, err := s.db.Query(query)
	if err != nil {
		return nil, fmt.Errorf("analytics query failed: %w", err)
	}
	defer rows.Close()

	var results []AnalyticsRow
	for rows.Next() {
		var row AnalyticsRow
		var timeVal interface{}
		var annotationValue sql.NullString

		if err := rows.Scan(
			&timeVal, &row.DataSource, &annotationValue,
			&row.IngressBytes, &row.IngressFiles,
			&row.EgressBytes, &row.EgressFiles,
			&row.ErrorFiles, &row.FilterFiles,
		); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		row.AnnotationValue = annotationValue.String

		// Handle time conversion - return epoch milliseconds for Grafana
		switch t := timeVal.(type) {
		case time.Time:
			row.Time = t.UnixMilli()
		case string:
			if parsed, err := time.Parse("2006-01-02 15:04:05", t); err == nil {
				row.Time = parsed.UnixMilli()
			}
		}

		results = append(results, row)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("row iteration error: %w", err)
	}

	return results, nil
}

// buildAnalyticsQuery constructs the SQL query for analytics (aggregated files only)
func (s *Service) buildAnalyticsQuery(req AnalyticsRequest) string {
	intervalSeconds := req.IntervalMs / 1000
	if intervalSeconds < 300 {
		intervalSeconds = 300 // Minimum 5 minutes
	}

	// Format time range for SQL
	timeFrom := req.TimeFrom.Format("2006-01-02 15:04:05")
	timeTo := req.TimeTo.Format("2006-01-02 15:04:05")

	// Event type filter (for errors/filters dashboards)
	eventTypeFilter := ""
	if req.EventType == "ERRORS" {
		eventTypeFilter = "event_type = 'ERROR'"
	} else if req.EventType == "FILTERS" {
		eventTypeFilter = "event_type = 'FILTER'"
	}

	// Get aggregated files for the time range
	aggregatedFiles := s.getAggregatedFilesForRange(req.TimeFrom, req.TimeTo)

	s.logger.Debug("query file selection",
		"time_from", req.TimeFrom,
		"time_to", req.TimeTo,
		"aggregated_files", len(aggregatedFiles),
		"file_paths", aggregatedFiles,
	)

	if len(aggregatedFiles) == 0 {
		// Return empty result query
		return `SELECT
			TIMESTAMP '2000-01-01' AS bucket,
			'' AS data_source, '' AS annotation_value,
			0::BIGINT AS ingress_bytes, 0::BIGINT AS ingress_files,
			0::BIGINT AS egress_bytes, 0::BIGINT AS egress_files,
			0::BIGINT AS error_files, 0::BIGINT AS filter_files
			WHERE false`
	}

	return s.buildAggregatedQuery(aggregatedFiles, timeFrom, timeTo, intervalSeconds, req.DataSources, req.FlowNames, req.Annotations, req.GroupByAnnotation, eventTypeFilter)
}

// buildAggregatedQuery builds query for compacted aggregated parquet files
func (s *Service) buildAggregatedQuery(files []string, timeFrom, timeTo string, intervalSeconds int64, dataSources, flowNames []string, annotations map[string][]string, groupByAnnotation, eventTypeFilter string) string {
	// Aggregated files have: bucket, data_source, event_type, flow_name, action_name, cause, annotations (MAP), event_count, total_bytes, total_file_count
	// When groupByAnnotation is set, combine data_source with annotation value
	dataSourceSelect := s.buildDataSourceSelect(groupByAnnotation)
	annotationSelect := s.buildAnnotationSelect(groupByAnnotation)

	// Build file list for DuckDB
	fileList := formatFileList(files)

	filters := []string{
		fmt.Sprintf("bucket >= '%s'", timeFrom),
		fmt.Sprintf("bucket < '%s'", timeTo),
	}
	if eventTypeFilter != "" {
		filters = append(filters, eventTypeFilter)
	}
	if len(dataSources) > 0 && !containsAll(dataSources) {
		quoted := quoteStrings(dataSources)
		filters = append(filters, fmt.Sprintf("data_source IN (%s)", strings.Join(quoted, ", ")))
	}
	if len(flowNames) > 0 && !containsAll(flowNames) {
		quoted := quoteStrings(flowNames)
		filters = append(filters, fmt.Sprintf("flow_name IN (%s)", strings.Join(quoted, ", ")))
	}

	// Add multi-annotation filters (expand Grafana multi-value format)
	expandedAnnotations := expandAnnotationFilters(annotations)
	for key, values := range expandedAnnotations {
		if len(values) > 0 {
			// Check if "not present" is in the values
			hasNotPresent := false
			var regularValues []string
			for _, v := range values {
				if v == "not present" {
					hasNotPresent = true
				} else {
					regularValues = append(regularValues, v)
				}
			}

			if hasNotPresent && len(regularValues) == 0 {
				// Only "not present" selected - filter for NULL annotations
				filters = append(filters, fmt.Sprintf("(annotations['%s'] IS NULL OR annotations['%s'][1] IS NULL)", key, key))
			} else if hasNotPresent {
				// "not present" plus other values - use OR
				quoted := quoteStrings(regularValues)
				filters = append(filters, fmt.Sprintf("(annotations['%s'] IS NULL OR annotations['%s'][1] IS NULL OR annotations['%s'][1] IN (%s))", key, key, key, strings.Join(quoted, ", ")))
			} else {
				// Regular filter - annotation must be present with matching value
				quoted := quoteStrings(values)
				filters = append(filters, fmt.Sprintf("annotations['%s'][1] IN (%s)", key, strings.Join(quoted, ", ")))
			}
		}
	}

	return fmt.Sprintf(`
		SELECT
			time_bucket(INTERVAL '%d seconds', bucket::TIMESTAMP) AS bucket,
			%s AS data_source,
			%s AS annotation_value,
			CAST(SUM(CASE WHEN event_type = 'INGRESS' THEN total_bytes ELSE 0 END) AS BIGINT) AS ingress_bytes,
			CAST(SUM(CASE WHEN event_type = 'INGRESS' THEN total_file_count ELSE 0 END) AS BIGINT) AS ingress_files,
			CAST(SUM(CASE WHEN event_type = 'EGRESS' THEN total_bytes ELSE 0 END) AS BIGINT) AS egress_bytes,
			CAST(SUM(CASE WHEN event_type = 'EGRESS' THEN total_file_count ELSE 0 END) AS BIGINT) AS egress_files,
			CAST(SUM(CASE WHEN event_type = 'ERROR' THEN event_count ELSE 0 END) AS BIGINT) AS error_files,
			CAST(SUM(CASE WHEN event_type = 'FILTER' THEN event_count ELSE 0 END) AS BIGINT) AS filter_files
		FROM read_parquet([%s])
		WHERE %s
		GROUP BY 1, 2, 3
	`, intervalSeconds, dataSourceSelect, annotationSelect, fileList, strings.Join(filters, " AND "))
}


// normalizeAnnotationKey handles Grafana special values for annotation keys
// Returns empty string if key is "All", "None", or in multi-value format
func normalizeAnnotationKey(key string) string {
	if key == "" || key == "$__all" || key == "None" {
		return ""
	}
	// Multi-value format {a,b,c} means "All" was selected - can't group by multiple keys
	if len(key) > 2 && key[0] == '{' && key[len(key)-1] == '}' {
		return ""
	}
	return key
}

// buildAnnotationSelect builds the annotation value select for aggregated data
func (s *Service) buildAnnotationSelect(annotationKey string) string {
	key := normalizeAnnotationKey(annotationKey)
	if key == "" {
		return "CAST('' AS VARCHAR)"
	}
	// Access annotation from MAP column - [1] extracts first element since DuckDB MAP access returns a list
	return fmt.Sprintf("COALESCE(annotations['%s'][1], 'not present')", key)
}

// buildDataSourceSelect builds data_source select for aggregated data
// When groupByAnnotation is set, combines data_source with annotation value
func (s *Service) buildDataSourceSelect(annotationKey string) string {
	key := normalizeAnnotationKey(annotationKey)
	if key == "" {
		return "data_source"
	}
	// Combine data_source with annotation value: "DataSource - AnnotationValue" or "DataSource - not present"
	return fmt.Sprintf("data_source || ' - ' || COALESCE(annotations['%s'][1], 'not present')", key)
}

func (s *Service) hasParquetFiles(glob string) bool {
	matches, err := filepath.Glob(glob)
	return err == nil && len(matches) > 0
}

// getAggregatedFilesForRange returns aggregated parquet files for dates within the time range
// Rolling data: YYYYMMDD/HH.parquet (hourly files with 5-min buckets)
// Archived data: YYYYMMDD.parquet (daily files with hourly buckets)
func (s *Service) getAggregatedFilesForRange(timeFrom, timeTo time.Time) []string {
	aggregatedDir := filepath.Join(s.dataDir, "aggregated")
	dates := getDateRange(timeFrom, timeTo)

	var files []string
	for _, date := range dates {
		// Check for daily file first (archived data)
		dailyFile := filepath.Join(aggregatedDir, date+".parquet")
		if _, err := os.Stat(dailyFile); err == nil {
			files = append(files, dailyFile)
			continue
		}

		// Fall back to hourly files (rolling data)
		pattern := filepath.Join(aggregatedDir, date, "*.parquet")
		matches, err := filepath.Glob(pattern)
		if err == nil {
			files = append(files, matches...)
		}
	}
	return files
}

// getAllAggregatedFiles returns all aggregated parquet files (for queries without date filtering)
// Uses filepath.Glob which evaluates fresh each call, avoiding DuckDB caching issues
// Includes both hourly (aggregated/YYYYMMDD/*.parquet) and daily (aggregated/YYYYMMDD.parquet) files
func (s *Service) getAllAggregatedFiles() []string {
	aggregatedDir := filepath.Join(s.dataDir, "aggregated")

	// Get hourly files: aggregated/*/*.parquet
	hourlyPattern := filepath.Join(aggregatedDir, "*", "*.parquet")
	hourlyFiles, _ := filepath.Glob(hourlyPattern)

	// Get daily files: aggregated/*.parquet (exclude metadata.json)
	dailyPattern := filepath.Join(aggregatedDir, "*.parquet")
	dailyFiles, _ := filepath.Glob(dailyPattern)

	files := append(hourlyFiles, dailyFiles...)
	if len(files) == 0 {
		return nil
	}
	return files
}

// getDateRange returns a list of date strings (YYYYMMDD) from timeFrom to timeTo inclusive
func getDateRange(timeFrom, timeTo time.Time) []string {
	var dates []string

	// Truncate to start of day
	current := time.Date(timeFrom.Year(), timeFrom.Month(), timeFrom.Day(), 0, 0, 0, 0, timeFrom.Location())
	end := time.Date(timeTo.Year(), timeTo.Month(), timeTo.Day(), 0, 0, 0, 0, timeTo.Location())

	for !current.After(end) {
		dates = append(dates, current.Format("20060102"))
		current = current.AddDate(0, 0, 1)
	}
	return dates
}

// formatFileList formats a slice of file paths for DuckDB's read_parquet function
func formatFileList(files []string) string {
	if len(files) == 0 {
		return "''"
	}
	quoted := make([]string, len(files))
	for i, f := range files {
		quoted[i] = "'" + f + "'"
	}
	return strings.Join(quoted, ", ")
}

func containsAll(values []string) bool {
	for _, v := range values {
		if v == "All" || v == "'All'" || v == "$__all" {
			return true
		}
	}
	return false
}

// parseGrafanaMultiValue parses Grafana's multi-value format {a,b,c} into individual values
// Returns nil if not in multi-value format
func parseGrafanaMultiValue(s string) []string {
	if len(s) > 2 && s[0] == '{' && s[len(s)-1] == '}' {
		return strings.Split(s[1:len(s)-1], ",")
	}
	return nil
}

// expandAnnotationFilters expands Grafana multi-value format into proper annotation filters
// Returns a map with expanded keys, or nil if filters should be skipped entirely
// Important: When key is in multi-value format {a,b,c}, we skip that filter entirely
// because we can't meaningfully filter by multiple keys at once
func expandAnnotationFilters(annotations map[string][]string) map[string][]string {
	if len(annotations) == 0 {
		return nil
	}

	result := make(map[string][]string)
	for key, values := range annotations {
		// Skip if key is empty or $__all
		if key == "" || key == "$__all" {
			continue
		}

		// Skip if key is in multi-value format {a,b,c} - this means "All" was selected
		// or multiple keys were selected, which we can't filter by meaningfully
		if parseGrafanaMultiValue(key) != nil {
			continue
		}

		// Skip if values indicate "All"
		if containsAll(values) {
			continue
		}

		// Parse multi-value values format (values can be multi-select)
		expandedValues := make([]string, 0)
		for _, v := range values {
			if multiVals := parseGrafanaMultiValue(v); multiVals != nil {
				expandedValues = append(expandedValues, multiVals...)
			} else {
				expandedValues = append(expandedValues, v)
			}
		}

		// Only add if we have values to filter by
		if len(expandedValues) > 0 {
			result[key] = expandedValues
		}
	}

	if len(result) == 0 {
		return nil
	}
	return result
}

func quoteStrings(values []string) []string {
	quoted := make([]string, len(values))
	for i, v := range values {
		// Remove existing quotes and re-quote
		v = strings.Trim(v, "'\"")
		quoted[i] = "'" + v + "'"
	}
	return quoted
}

// GetDistinctValues returns distinct values for a field from aggregated data
// When filtered=false (default), uses pre-computed metadata for instant response
// When filtered=true, scans parquet files for time-range-specific values
func (s *Service) GetDistinctValues(field string, timeFrom, timeTo time.Time, filtered bool) ([]string, error) {
	// Try metadata first (instant response)
	if !filtered {
		if meta, err := s.LoadMetadata(); err == nil {
			switch field {
			case "data_source":
				s.logger.Debug("using metadata for data_source values", "count", len(meta.DataSources))
				return meta.DataSources, nil
			case "flow_name":
				s.logger.Debug("using metadata for flow_name values", "count", len(meta.FlowNames))
				return meta.FlowNames, nil
			}
		}
		// Fall through to parquet scan if metadata unavailable
	}

	// Check cache for filtered queries
	cacheKey := fmt.Sprintf("field|%s|%s|%s", field, timeFrom.Format("20060102"), timeTo.Format("20060102"))
	s.cacheMu.RLock()
	if entry, ok := s.annotationValCache[cacheKey]; ok && time.Now().Before(entry.expires) {
		data := entry.data
		s.cacheMu.RUnlock()
		s.logger.Debug("distinct values cache hit", "key", cacheKey)
		return data, nil
	}
	s.cacheMu.RUnlock()

	s.mu.RLock()
	defer s.mu.RUnlock()

	files := s.getAllAggregatedFiles()
	if len(files) == 0 {
		return nil, nil
	}

	timeFromStr := timeFrom.Format("2006-01-02 15:04:05")
	timeToStr := timeTo.Format("2006-01-02 15:04:05")

	query := fmt.Sprintf(
		"SELECT DISTINCT %s AS value FROM read_parquet([%s]) WHERE bucket >= '%s' AND bucket < '%s' ORDER BY value",
		field, formatFileList(files), timeFromStr, timeToStr,
	)

	rows, err := s.db.Query(query)
	if err != nil {
		return nil, fmt.Errorf("distinct values query failed: %w", err)
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

	// Cache result
	s.cacheMu.Lock()
	s.annotationValCache[cacheKey] = &cacheEntry{data: values, expires: time.Now().Add(cacheTTL)}
	s.cacheMu.Unlock()
	s.logger.Debug("distinct values cached", "key", cacheKey, "count", len(values))

	return values, nil
}

// GetAnnotationKeys returns distinct annotation keys from aggregated data
// Returns keys in order: [configured_key (if exists), "None", other_keys...]
// "None" is always included and means "don't group by annotation"
// forGroup controls ordering: if true, puts analyticsGroupName first (for groupByAnnotation dropdown)
// if false, uses alphabetical order with "None" first (for annotation key filter dropdowns)
// When filtered=false (default), uses pre-computed metadata for instant response
// When filtered=true, scans parquet files for time-range-specific values
func (s *Service) GetAnnotationKeys(timeFrom, timeTo time.Time, forGroup bool, filtered bool) ([]string, error) {
	// Try metadata first (instant response)
	if !filtered {
		if meta, err := s.LoadMetadata(); err == nil {
			return s.buildAnnotationKeysResult(meta.AnnotationKeys, forGroup), nil
		}
		// Fall through to parquet scan if metadata unavailable
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	files := s.getAllAggregatedFiles()
	if len(files) == 0 {
		return []string{"None"}, nil
	}

	timeFromStr := timeFrom.Format("2006-01-02 15:04:05")
	timeToStr := timeTo.Format("2006-01-02 15:04:05")

	query := fmt.Sprintf(
		"SELECT DISTINCT unnest(map_keys(annotations)) AS key FROM read_parquet([%s]) WHERE bucket >= '%s' AND bucket < '%s' AND annotations IS NOT NULL ORDER BY key",
		formatFileList(files), timeFromStr, timeToStr,
	)

	rows, err := s.db.Query(query)
	if err != nil {
		s.logger.Warn("annotation keys query failed", "error", err)
		return []string{"None"}, nil
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

	return s.buildAnnotationKeysResult(keys, forGroup), nil
}

// buildAnnotationKeysResult orders keys based on forGroup setting
func (s *Service) buildAnnotationKeysResult(keys []string, forGroup bool) []string {
	keySet := make(map[string]bool)
	for _, k := range keys {
		keySet[k] = true
	}

	var result []string
	configuredKey := ""
	if forGroup && s.coreConfig != nil {
		configuredKey = s.coreConfig.GetAnalyticsGroupName()
	}

	// If forGroup and configured key exists in data, put it first
	if configuredKey != "" && keySet[configuredKey] {
		result = append(result, configuredKey)
		delete(keySet, configuredKey)
	}

	// Always add "None" option
	result = append(result, "None")

	// Add remaining keys in sorted order
	var otherKeys []string
	for k := range keySet {
		otherKeys = append(otherKeys, k)
	}
	sort.Strings(otherKeys)
	result = append(result, otherKeys...)

	return result
}

// GetAnnotationValues returns distinct values for a specific annotation key from aggregated data
// When filtered=false (default), uses pre-computed metadata for instant response
// When filtered=true, scans parquet files for time-range-specific values
func (s *Service) GetAnnotationValues(key string, timeFrom, timeTo time.Time, filtered bool) ([]string, error) {
	// Try metadata first (instant response)
	if !filtered {
		if meta, err := s.LoadMetadata(); err == nil {
			if values, ok := meta.AnnotationValues[key]; ok {
				s.logger.Debug("using metadata for annotation values", "key", key, "count", len(values))
				return values, nil
			}
			// Key not in metadata, return empty
			return nil, nil
		}
		// Fall through to parquet scan if metadata unavailable
	}

	// Check cache for filtered queries
	cacheKey := fmt.Sprintf("vals|%s|%s|%s", key, timeFrom.Format("20060102"), timeTo.Format("20060102"))
	s.cacheMu.RLock()
	if entry, ok := s.annotationValCache[cacheKey]; ok && time.Now().Before(entry.expires) {
		data := entry.data
		s.cacheMu.RUnlock()
		s.logger.Debug("annotation values cache hit", "key", cacheKey)
		return data, nil
	}
	s.cacheMu.RUnlock()

	s.mu.RLock()
	defer s.mu.RUnlock()

	files := s.getAllAggregatedFiles()
	if len(files) == 0 {
		return nil, nil
	}

	timeFromStr := timeFrom.Format("2006-01-02 15:04:05")
	timeToStr := timeTo.Format("2006-01-02 15:04:05")

	query := fmt.Sprintf(
		"SELECT DISTINCT annotations['%s'][1] AS value FROM read_parquet([%s]) WHERE bucket >= '%s' AND bucket < '%s' AND annotations IS NOT NULL AND annotations['%s'] IS NOT NULL ORDER BY value",
		key, formatFileList(files), timeFromStr, timeToStr, key,
	)

	rows, err := s.db.Query(query)
	if err != nil {
		s.logger.Warn("annotation values query failed", "error", err)
		return nil, nil
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

	// Cache result
	s.cacheMu.Lock()
	s.annotationValCache[cacheKey] = &cacheEntry{data: values, expires: time.Now().Add(cacheTTL)}
	s.cacheMu.Unlock()
	s.logger.Debug("annotation values cached", "key", cacheKey, "count", len(values))

	return values, nil
}

// GetAllAnnotationValues returns all distinct annotation values from aggregated data
func (s *Service) GetAllAnnotationValues(timeFrom, timeTo time.Time) ([]string, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	files := s.getAllAggregatedFiles()
	if len(files) == 0 {
		return nil, nil
	}

	timeFromStr := timeFrom.Format("2006-01-02 15:04:05")
	timeToStr := timeTo.Format("2006-01-02 15:04:05")

	query := fmt.Sprintf(
		"SELECT DISTINCT CAST(unnest(map_values(annotations)) AS VARCHAR) AS value FROM read_parquet([%s]) WHERE bucket >= '%s' AND bucket < '%s' AND annotations IS NOT NULL ORDER BY value",
		formatFileList(files), timeFromStr, timeToStr,
	)

	rows, err := s.db.Query(query)
	if err != nil {
		s.logger.Warn("all annotation values query failed", "error", err)
		return nil, nil
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

	return values, nil
}

// GetAnnotationValuesForKeys returns distinct values for multiple annotation keys
func (s *Service) GetAnnotationValuesForKeys(keys []string, timeFrom, timeTo time.Time, filtered bool) ([]string, error) {
	if len(keys) == 0 {
		return nil, nil
	}

	// Collect values from all keys and dedupe
	valueSet := make(map[string]bool)
	for _, key := range keys {
		vals, err := s.GetAnnotationValues(key, timeFrom, timeTo, filtered)
		if err != nil {
			continue
		}
		for _, v := range vals {
			valueSet[v] = true
		}
	}

	var values []string
	for v := range valueSet {
		values = append(values, v)
	}

	// Sort for consistent ordering
	sort.Strings(values)
	return values, nil
}

// QueryResult represents results in a format suitable for Grafana
type QueryResult struct {
	Columns []string        `json:"columns"`
	Rows    [][]interface{} `json:"rows"`
}

// Query executes a SQL query against the Parquet files
func (s *Service) Query(sqlQuery string) (*QueryResult, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(sqlQuery)
	if err != nil {
		return nil, fmt.Errorf("query failed: %w", err)
	}
	defer rows.Close()

	columns, err := rows.Columns()
	if err != nil {
		return nil, fmt.Errorf("failed to get columns: %w", err)
	}

	result := &QueryResult{
		Columns: columns,
		Rows:    make([][]interface{}, 0),
	}

	for rows.Next() {
		values := make([]interface{}, len(columns))
		valuePtrs := make([]interface{}, len(columns))
		for i := range values {
			valuePtrs[i] = &values[i]
		}

		if err := rows.Scan(valuePtrs...); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		row := make([]interface{}, len(columns))
		for i, v := range values {
			row[i] = convertValue(v)
		}
		result.Rows = append(result.Rows, row)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("row iteration error: %w", err)
	}

	return result, nil
}

// GrafanaTableResponse is the table format for Grafana
type GrafanaTableResponse struct {
	Columns []GrafanaColumn `json:"columns"`
	Rows    [][]interface{} `json:"rows"`
	Type    string          `json:"type"`
}

type GrafanaColumn struct {
	Text string `json:"text"`
	Type string `json:"type"`
}

// ToGrafanaTable converts QueryResult to Grafana table format
func (r *QueryResult) ToGrafanaTable() *GrafanaTableResponse {
	columns := make([]GrafanaColumn, len(r.Columns))
	for i, col := range r.Columns {
		columns[i] = GrafanaColumn{Text: col, Type: "string"}
	}
	return &GrafanaTableResponse{
		Columns: columns,
		Rows:    r.Rows,
		Type:    "table",
	}
}

func convertValue(v interface{}) interface{} {
	if v == nil {
		return nil
	}
	switch val := v.(type) {
	case []byte:
		return string(val)
	case json.RawMessage:
		return string(val)
	default:
		return val
	}
}

// DataDir returns the data directory path
func (s *Service) DataDir() string {
	return s.dataDir
}

// HasData checks if there's any aggregated analytics data available
func (s *Service) HasData() bool {
	// Check for hourly files
	hourlyGlob := filepath.Join(s.dataDir, "aggregated", "*", "*.parquet")
	if s.hasParquetFiles(hourlyGlob) {
		return true
	}
	// Check for daily files
	dailyGlob := filepath.Join(s.dataDir, "aggregated", "*.parquet")
	return s.hasParquetFiles(dailyGlob)
}

// GetDataStats returns statistics about available data
func (s *Service) GetDataStats() map[string]interface{} {
	stats := make(map[string]interface{})

	// Count aggregated files (hourly + daily)
	hourlyGlob := filepath.Join(s.dataDir, "aggregated", "*", "*.parquet")
	dailyGlob := filepath.Join(s.dataDir, "aggregated", "*.parquet")
	hourlyMatches, _ := filepath.Glob(hourlyGlob)
	dailyMatches, _ := filepath.Glob(dailyGlob)
	allMatches := append(hourlyMatches, dailyMatches...)

	stats["aggregated_files"] = len(allMatches)
	var totalSize int64
	for _, f := range allMatches {
		if info, err := os.Stat(f); err == nil {
			totalSize += info.Size()
		}
	}
	stats["aggregated_size_bytes"] = totalSize

	// Count event files (events/YYYYMMDD/HH/*.parquet)
	eventsGlob := filepath.Join(s.dataDir, "events", "*", "*", "*.parquet")
	if matches, err := filepath.Glob(eventsGlob); err == nil {
		stats["event_files"] = len(matches)
	}

	// Count annotation files (annotations/YYYYMMDD/HH/*.parquet)
	annotationsGlob := filepath.Join(s.dataDir, "annotations", "*", "*", "*.parquet")
	if matches, err := filepath.Glob(annotationsGlob); err == nil {
		stats["annotation_files"] = len(matches)
	}

	return stats
}

// QueryEventAnalysis executes an error or filter analysis query with groupBy support
func (s *Service) QueryEventAnalysis(req EventAnalysisRequest, eventType string) ([]EventAnalysisRow, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := s.buildEventAnalysisQuery(req, eventType)
	s.logger.Debug("executing event analysis query", "query", query, "eventType", eventType)

	rows, err := s.db.Query(query)
	if err != nil {
		return nil, fmt.Errorf("event analysis query failed: %w", err)
	}
	defer rows.Close()

	var results []EventAnalysisRow
	for rows.Next() {
		var row EventAnalysisRow
		var timeVal interface{}
		var groupValue sql.NullString

		if err := rows.Scan(&timeVal, &groupValue, &row.Count); err != nil {
			return nil, fmt.Errorf("failed to scan row: %w", err)
		}

		row.GroupValue = groupValue.String

		switch t := timeVal.(type) {
		case time.Time:
			row.Time = t.UnixMilli()
		case string:
			if parsed, err := time.Parse("2006-01-02 15:04:05", t); err == nil {
				row.Time = parsed.UnixMilli()
			}
		}

		results = append(results, row)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("row iteration error: %w", err)
	}

	return results, nil
}

// buildEventAnalysisQuery constructs the SQL query for error/filter analysis
func (s *Service) buildEventAnalysisQuery(req EventAnalysisRequest, eventType string) string {
	intervalSeconds := req.IntervalMs / 1000
	if intervalSeconds < 300 {
		intervalSeconds = 300 // Minimum 5 minutes
	}

	timeFrom := req.TimeFrom.Format("2006-01-02 15:04:05")
	timeTo := req.TimeTo.Format("2006-01-02 15:04:05")

	aggregatedFiles := s.getAggregatedFilesForRange(req.TimeFrom, req.TimeTo)
	if len(aggregatedFiles) == 0 {
		return `SELECT
			TIMESTAMP '2000-01-01' AS bucket,
			'' AS group_value,
			0::BIGINT AS count
			WHERE false`
	}

	fileList := formatFileList(aggregatedFiles)

	// Build the groupBy select expression
	groupBySelect := s.buildGroupBySelect(req.GroupBy)

	// Build filters
	filters := []string{
		fmt.Sprintf("bucket >= '%s'", timeFrom),
		fmt.Sprintf("bucket < '%s'", timeTo),
		fmt.Sprintf("event_type = '%s'", eventType),
	}

	if len(req.DataSources) > 0 && !containsAll(req.DataSources) {
		quoted := quoteStrings(req.DataSources)
		filters = append(filters, fmt.Sprintf("data_source IN (%s)", strings.Join(quoted, ", ")))
	}

	// Add annotation filters
	expandedAnnotations := expandAnnotationFilters(req.Annotations)
	for key, values := range expandedAnnotations {
		if len(values) > 0 {
			hasNotPresent := false
			var regularValues []string
			for _, v := range values {
				if v == "not present" {
					hasNotPresent = true
				} else {
					regularValues = append(regularValues, v)
				}
			}

			if hasNotPresent && len(regularValues) == 0 {
				filters = append(filters, fmt.Sprintf("(annotations['%s'] IS NULL OR annotations['%s'][1] IS NULL)", key, key))
			} else if hasNotPresent {
				quoted := quoteStrings(regularValues)
				filters = append(filters, fmt.Sprintf("(annotations['%s'] IS NULL OR annotations['%s'][1] IS NULL OR annotations['%s'][1] IN (%s))", key, key, key, strings.Join(quoted, ", ")))
			} else {
				quoted := quoteStrings(values)
				filters = append(filters, fmt.Sprintf("annotations['%s'][1] IN (%s)", key, strings.Join(quoted, ", ")))
			}
		}
	}

	return fmt.Sprintf(`
		SELECT
			time_bucket(INTERVAL '%d seconds', bucket::TIMESTAMP) AS bucket,
			%s AS group_value,
			CAST(SUM(event_count) AS BIGINT) AS count
		FROM read_parquet([%s])
		WHERE %s
		GROUP BY 1, 2
		ORDER BY 1, 2
	`, intervalSeconds, groupBySelect, fileList, strings.Join(filters, " AND "))
}

// buildGroupBySelect builds the select expression for the groupBy field
func (s *Service) buildGroupBySelect(groupBy string) string {
	switch groupBy {
	case "data_source", "cause", "action_name", "flow_name":
		return fmt.Sprintf("COALESCE(%s, '')", groupBy)
	case "":
		return "CAST('' AS VARCHAR)"
	default:
		// Assume it's an annotation key
		return fmt.Sprintf("COALESCE(annotations['%s'][1], 'not present')", groupBy)
	}
}

// ProvenanceStats contains summary statistics for provenance data
type ProvenanceStats struct {
	TotalRecords int64            `json:"total_records"`
	ByFinalState map[string]int64 `json:"by_final_state"`
	BySystemName map[string]int64 `json:"by_system_name"`
	ByDataSource map[string]int64 `json:"by_data_source"`
	OldestRecord *time.Time       `json:"oldest_record,omitempty"`
	NewestRecord *time.Time       `json:"newest_record,omitempty"`
}

// ProvenanceQueryRequest defines filters for querying provenance records
type ProvenanceQueryRequest struct {
	DID             string    `json:"did"`
	ParentDID       string    `json:"parent_did"`
	SystemName      string    `json:"system_name"`
	DataSource      string    `json:"data_source"`
	DataSink        string    `json:"data_sink"`
	Filename        string    `json:"filename"`
	FinalState      string    `json:"final_state"`
	AnnotationKey   string    `json:"annotation_key"`
	AnnotationValue string    `json:"annotation_value"`
	From            time.Time `json:"from"`
	To              time.Time `json:"to"`
	Limit           int       `json:"limit"`
}

// ProvenanceRecord represents a single provenance record in query results
type ProvenanceRecord struct {
	DID         string            `json:"did"`
	ParentDID   string            `json:"parent_did"`
	SystemName  string            `json:"system_name"`
	DataSource  string            `json:"data_source"`
	Filename    string            `json:"filename"`
	Transforms  string            `json:"transforms"` // JSON array as string for simplicity
	DataSink    string            `json:"data_sink"`
	FinalState  string            `json:"final_state"`
	Created     time.Time         `json:"created"`
	Completed   time.Time         `json:"completed"`
	Annotations map[string]string `json:"annotations"`
}

// getProvenanceFiles returns all provenance files (both raw and compacted)
func (s *Service) getProvenanceFiles() []string {
	var files []string

	// Raw files: provenance/raw/YYYYMMDD/HH/*.parquet
	rawPattern := filepath.Join(s.dataDir, "provenance", "raw", "*", "*", "*.parquet")
	if rawFiles, err := filepath.Glob(rawPattern); err == nil {
		files = append(files, rawFiles...)
	}

	// Compacted files: provenance/compacted/{system_name}/YYYYMMDD/*.parquet
	compactedPattern := filepath.Join(s.dataDir, "provenance", "compacted", "*", "*", "*.parquet")
	if compactedFiles, err := filepath.Glob(compactedPattern); err == nil {
		files = append(files, compactedFiles...)
	}

	return files
}

// ProvenanceStatsRequest defines filters for stats queries
type ProvenanceStatsRequest struct {
	DID             string `json:"did"`
	ParentDID       string `json:"parent_did"`
	DataSource      string `json:"data_source"`
	DataSink        string `json:"data_sink"`
	Filename        string `json:"filename"`
	AnnotationKey   string `json:"annotation_key"`
	AnnotationValue string `json:"annotation_value"`
}

// GetProvenanceStats returns summary statistics for provenance data
func (s *Service) GetProvenanceStats(req ProvenanceStatsRequest) (*ProvenanceStats, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	// Find all provenance files (raw + compacted)
	files := s.getProvenanceFiles()

	if len(files) == 0 {
		// Return empty stats if no files
		return &ProvenanceStats{
			ByFinalState: make(map[string]int64),
			BySystemName: make(map[string]int64),
			ByDataSource: make(map[string]int64),
		}, nil
	}

	stats := &ProvenanceStats{
		ByFinalState: make(map[string]int64),
		BySystemName: make(map[string]int64),
		ByDataSource: make(map[string]int64),
	}

	fileList := formatFileList(files)

	// Build WHERE clause from filters
	var filters []string
	if req.DID != "" {
		filters = append(filters, fmt.Sprintf("did = '%s'", escapeSQL(req.DID)))
	}
	if req.ParentDID != "" {
		filters = append(filters, fmt.Sprintf("parent_did = '%s'", escapeSQL(req.ParentDID)))
	}
	if req.DataSource != "" {
		filters = append(filters, fmt.Sprintf("data_source = '%s'", escapeSQL(req.DataSource)))
	}
	if req.DataSink != "" {
		filters = append(filters, fmt.Sprintf("data_sink = '%s'", escapeSQL(req.DataSink)))
	}
	if req.Filename != "" {
		filters = append(filters, fmt.Sprintf("filename = '%s'", escapeSQL(req.Filename)))
	}
	if req.AnnotationKey != "" && req.AnnotationValue != "" {
		filters = append(filters, fmt.Sprintf("annotations['%s'][1] = '%s'", escapeSQL(req.AnnotationKey), escapeSQL(req.AnnotationValue)))
	}

	whereClause := ""
	if len(filters) > 0 {
		whereClause = "WHERE " + strings.Join(filters, " AND ")
	}

	// Query total and time range
	totalQuery := fmt.Sprintf(`
		SELECT
			COUNT(*) as total,
			MIN(completed) as oldest,
			MAX(completed) as newest
		FROM read_parquet([%s])
		%s
	`, fileList, whereClause)

	var oldest, newest sql.NullTime
	if err := s.db.QueryRow(totalQuery).Scan(&stats.TotalRecords, &oldest, &newest); err != nil {
		return nil, fmt.Errorf("querying provenance totals: %w", err)
	}
	if oldest.Valid {
		stats.OldestRecord = &oldest.Time
	}
	if newest.Valid {
		stats.NewestRecord = &newest.Time
	}

	// Query by final_state
	stateWhereClause := "WHERE final_state IS NOT NULL"
	if len(filters) > 0 {
		stateWhereClause += " AND " + strings.Join(filters, " AND ")
	}
	stateQuery := fmt.Sprintf(`
		SELECT final_state, COUNT(*) as cnt
		FROM read_parquet([%s])
		%s
		GROUP BY final_state
	`, fileList, stateWhereClause)
	rows, err := s.db.Query(stateQuery)
	if err != nil {
		return nil, fmt.Errorf("querying final_state: %w", err)
	}
	for rows.Next() {
		var state string
		var cnt int64
		if err := rows.Scan(&state, &cnt); err == nil {
			stats.ByFinalState[state] = cnt
		}
	}
	rows.Close()

	// Query by system_name
	sysWhereClause := "WHERE system_name IS NOT NULL AND system_name != ''"
	if len(filters) > 0 {
		sysWhereClause += " AND " + strings.Join(filters, " AND ")
	}
	sysQuery := fmt.Sprintf(`
		SELECT system_name, COUNT(*) as cnt
		FROM read_parquet([%s])
		%s
		GROUP BY system_name
		ORDER BY cnt DESC
		LIMIT 50
	`, fileList, sysWhereClause)
	rows, err = s.db.Query(sysQuery)
	if err != nil {
		return nil, fmt.Errorf("querying system_name: %w", err)
	}
	for rows.Next() {
		var name string
		var cnt int64
		if err := rows.Scan(&name, &cnt); err == nil {
			stats.BySystemName[name] = cnt
		}
	}
	rows.Close()

	// Query by data_source
	dsWhereClause := "WHERE data_source IS NOT NULL AND data_source != ''"
	if len(filters) > 0 {
		dsWhereClause += " AND " + strings.Join(filters, " AND ")
	}
	dsQuery := fmt.Sprintf(`
		SELECT data_source, COUNT(*) as cnt
		FROM read_parquet([%s])
		%s
		GROUP BY data_source
		ORDER BY cnt DESC
		LIMIT 50
	`, fileList, dsWhereClause)
	rows, err = s.db.Query(dsQuery)
	if err != nil {
		return nil, fmt.Errorf("querying data_source: %w", err)
	}
	for rows.Next() {
		var ds string
		var cnt int64
		if err := rows.Scan(&ds, &cnt); err == nil {
			stats.ByDataSource[ds] = cnt
		}
	}
	rows.Close()

	return stats, nil
}

// QueryProvenance returns provenance records matching the given filters
func (s *Service) QueryProvenance(req ProvenanceQueryRequest) ([]ProvenanceRecord, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	// Find all provenance files (raw + compacted)
	files := s.getProvenanceFiles()

	if len(files) == 0 {
		return []ProvenanceRecord{}, nil
	}

	// Build WHERE clause
	var filters []string
	filters = append(filters, "1=1") // Always true base

	if req.DID != "" {
		filters = append(filters, fmt.Sprintf("did = '%s'", escapeSQL(req.DID)))
	}
	if req.ParentDID != "" {
		filters = append(filters, fmt.Sprintf("parent_did = '%s'", escapeSQL(req.ParentDID)))
	}
	if req.SystemName != "" {
		filters = append(filters, fmt.Sprintf("system_name = '%s'", escapeSQL(req.SystemName)))
	}
	if req.DataSource != "" {
		filters = append(filters, fmt.Sprintf("data_source = '%s'", escapeSQL(req.DataSource)))
	}
	if req.DataSink != "" {
		filters = append(filters, fmt.Sprintf("data_sink = '%s'", escapeSQL(req.DataSink)))
	}
	if req.Filename != "" {
		filters = append(filters, fmt.Sprintf("filename = '%s'", escapeSQL(req.Filename)))
	}
	if req.FinalState != "" {
		filters = append(filters, fmt.Sprintf("final_state = '%s'", escapeSQL(req.FinalState)))
	}
	if req.AnnotationKey != "" && req.AnnotationValue != "" {
		filters = append(filters, fmt.Sprintf("annotations['%s'][1] = '%s'", escapeSQL(req.AnnotationKey), escapeSQL(req.AnnotationValue)))
	}
	if !req.From.IsZero() {
		filters = append(filters, fmt.Sprintf("completed >= '%s'", req.From.Format(time.RFC3339)))
	}
	if !req.To.IsZero() {
		filters = append(filters, fmt.Sprintf("completed <= '%s'", req.To.Format(time.RFC3339)))
	}

	limit := req.Limit
	if limit <= 0 || limit > 1000 {
		limit = 100
	}

	fileList := formatFileList(files)

	query := fmt.Sprintf(`
		SELECT
			did,
			COALESCE(parent_did, '') as parent_did,
			system_name,
			data_source,
			filename,
			CAST(transforms AS VARCHAR) as transforms,
			COALESCE(data_sink, '') as data_sink,
			final_state,
			created,
			completed,
			CAST(annotations AS VARCHAR) as annotations
		FROM read_parquet([%s])
		WHERE %s
		ORDER BY completed DESC
		LIMIT %d
	`, fileList, strings.Join(filters, " AND "), limit)

	rows, err := s.db.Query(query)
	if err != nil {
		return nil, fmt.Errorf("querying provenance: %w", err)
	}
	defer rows.Close()

	var results []ProvenanceRecord
	for rows.Next() {
		var rec ProvenanceRecord
		var annotationsStr string
		if err := rows.Scan(
			&rec.DID,
			&rec.ParentDID,
			&rec.SystemName,
			&rec.DataSource,
			&rec.Filename,
			&rec.Transforms,
			&rec.DataSink,
			&rec.FinalState,
			&rec.Created,
			&rec.Completed,
			&annotationsStr,
		); err != nil {
			s.logger.Warn("failed to scan provenance row", "error", err)
			continue
		}
		// Parse annotations from DuckDB MAP string format: {key1=value1, key2=value2}
		rec.Annotations = parseAnnotationsString(annotationsStr)
		results = append(results, rec)
	}

	return results, nil
}

// escapeSQL escapes single quotes for SQL string literals
func escapeSQL(s string) string {
	return strings.ReplaceAll(s, "'", "''")
}

// parseAnnotationsString parses DuckDB MAP string format into a map
// Format: {key1=value1, key2=value2} or empty string
func parseAnnotationsString(s string) map[string]string {
	if s == "" || s == "{}" {
		return nil
	}
	// Remove surrounding braces
	s = strings.TrimPrefix(s, "{")
	s = strings.TrimSuffix(s, "}")
	if s == "" {
		return nil
	}

	result := make(map[string]string)
	// Split by ", " (comma-space) to handle values that might contain commas
	pairs := strings.Split(s, ", ")
	for _, pair := range pairs {
		// Split on first "=" only (value might contain "=")
		idx := strings.Index(pair, "=")
		if idx > 0 {
			key := pair[:idx]
			value := pair[idx+1:]
			result[key] = value
		}
	}
	if len(result) == 0 {
		return nil
	}
	return result
}
