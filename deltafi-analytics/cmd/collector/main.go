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
 * ABOUTME: Entry point for the analytics collector service.
 * ABOUTME: HTTP server that receives events and annotations, writes to Parquet files.
 */
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"deltafi.org/deltafi-analytics/internal/buffer"
	"deltafi.org/deltafi-analytics/internal/compactor"
	"deltafi.org/deltafi-analytics/internal/config"
	"deltafi.org/deltafi-analytics/internal/query"
	"deltafi.org/deltafi-analytics/internal/schema"
	"deltafi.org/deltafi-analytics/internal/writer"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	cfg := loadConfig()

	parquetWriter, err := writer.New(writer.Config{
		OutputDir: cfg.OutputDir,
	}, logger)
	if err != nil {
		logger.Error("failed to create parquet writer", "error", err)
		os.Exit(1)
	}

	// Create core config fetcher first (needed by compactor for age-off)
	coreConfig := config.NewCoreConfig(cfg.CoreURL, logger)

	// Create compactor (triggered after buffer flushes, not on timer)
	comp := compactor.New(compactor.Config{
		DataDir:                  cfg.OutputDir,
		ArchiveThreshold:         cfg.ArchiveThreshold,
		AgeOffDaysFunc:           coreConfig.GetAgeOffDays,
		ProvenanceAgeOffDaysFunc: coreConfig.GetProvenanceAgeOffDays,
	}, logger)

	// Wrap write functions to trigger compaction after flush
	writeEventsAndCompact := func(events []schema.Event) error {
		if err := parquetWriter.WriteEvents(events); err != nil {
			return err
		}
		go comp.CheckAndRun() // Run compaction async after write
		return nil
	}

	writeAnnotationsAndCompact := func(annotations []schema.Annotation) error {
		if err := parquetWriter.WriteAnnotations(annotations); err != nil {
			return err
		}
		go comp.CheckAndRun() // Run compaction async after write
		return nil
	}

	eventBuffer := buffer.New(buffer.Config{
		FlushCount:    cfg.FlushCount,
		FlushInterval: cfg.FlushInterval,
		Name:          "events",
	}, writeEventsAndCompact, logger)

	annotationBuffer := buffer.New(buffer.Config{
		FlushCount:    cfg.FlushCount,
		FlushInterval: cfg.FlushInterval,
		Name:          "annotations",
	}, writeAnnotationsAndCompact, logger)

	// Provenance buffer flushes every minute - compaction will consolidate files hourly
	provenanceBuffer := buffer.New(buffer.Config{
		FlushCount:    cfg.FlushCount,
		FlushInterval: time.Minute,
		Name:          "provenance",
	}, parquetWriter.WriteProvenance, logger)

	queryService, err := query.NewService(cfg.OutputDir, coreConfig, logger)
	if err != nil {
		logger.Error("failed to create query service", "error", err)
		os.Exit(1)
	}
	defer queryService.Close()

	ctx, cancel := context.WithCancel(context.Background())
	eventBuffer.StartPeriodicFlush(ctx)
	annotationBuffer.StartPeriodicFlush(ctx)
	provenanceBuffer.StartPeriodicFlush(ctx)

	// Start periodic compaction (runs age-off and compaction on timer)
	comp.Start(ctx)

	// Start periodic config refresh
	coreConfig.StartPeriodicRefresh(ctx.Done())

	// Watch for .flush file signal (used during graceful shutdown)
	go watchFlushFile(ctx, cfg.OutputDir, eventBuffer, annotationBuffer, provenanceBuffer, comp, logger)

	handler := newHandler(eventBuffer, annotationBuffer, provenanceBuffer, queryService, parquetWriter, logger)

	server := &http.Server{
		Addr:         cfg.ListenAddr,
		Handler:      handler,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	// Graceful shutdown
	go func() {
		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
		<-sigCh

		logger.Info("shutting down")
		cancel() // Triggers final buffer flush

		shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer shutdownCancel()

		if err := server.Shutdown(shutdownCtx); err != nil {
			logger.Error("shutdown error", "error", err)
		}
	}()

	logger.Info("starting analytics collector",
		"addr", cfg.ListenAddr,
		"output_dir", cfg.OutputDir,
		"flush_count", cfg.FlushCount,
		"flush_interval", cfg.FlushInterval,
		"archive_threshold", cfg.ArchiveThreshold,
	)

	if err := server.ListenAndServe(); err != http.ErrServerClosed {
		logger.Error("server error", "error", err)
		os.Exit(1)
	}
}

type appConfig struct {
	ListenAddr       string
	OutputDir        string
	FlushCount       int
	FlushInterval    time.Duration
	CoreURL          string
	ArchiveThreshold time.Duration
}

func loadConfig() appConfig {
	cfg := appConfig{
		ListenAddr:       ":8080",
		OutputDir:        "/data/analytics",
		FlushCount:       10000,
		FlushInterval:    60 * time.Second,
		CoreURL:          "http://deltafi-core:8080",
		ArchiveThreshold: 72 * time.Hour, // 3 days default
	}

	if addr := os.Getenv("LISTEN_ADDR"); addr != "" {
		cfg.ListenAddr = addr
	}
	if dir := os.Getenv("OUTPUT_DIR"); dir != "" {
		cfg.OutputDir = dir
	}
	if coreURL := os.Getenv("CORE_URL"); coreURL != "" {
		cfg.CoreURL = coreURL
	}
	if archiveHours := os.Getenv("ARCHIVE_THRESHOLD_HOURS"); archiveHours != "" {
		if hours, err := strconv.Atoi(archiveHours); err == nil && hours > 0 {
			cfg.ArchiveThreshold = time.Duration(hours) * time.Hour
		}
	}

	return cfg
}

// watchFlushFile polls for a .flush file and flushes all buffers when found.
// After flushing, runs full compaction (including current hour) since this is
// typically used for graceful shutdown. Removes the file after completion.
func watchFlushFile(ctx context.Context, outputDir string, eventBuf *buffer.Buffer[schema.Event], annotationBuf *buffer.Buffer[schema.Annotation], provenanceBuf *buffer.Buffer[schema.Provenance], comp *compactor.Compactor, logger *slog.Logger) {
	flushPath := filepath.Join(outputDir, ".flush")
	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if _, err := os.Stat(flushPath); err == nil {
				logger.Info("flush file detected, flushing all buffers")

				if err := eventBuf.Flush(); err != nil {
					logger.Error("event buffer flush failed", "error", err)
				}
				if err := annotationBuf.Flush(); err != nil {
					logger.Error("annotation buffer flush failed", "error", err)
				}
				if err := provenanceBuf.Flush(); err != nil {
					logger.Error("provenance buffer flush failed", "error", err)
				}

				// Run full compaction including current hour
				if err := comp.CheckAndRunAll(); err != nil {
					logger.Error("compaction failed", "error", err)
				}

				if err := os.Remove(flushPath); err != nil {
					logger.Error("failed to remove flush file", "error", err)
				} else {
					logger.Info("flush and compaction complete, flush file removed")
				}
			}
		}
	}
}

type handler struct {
	eventBuffer      *buffer.Buffer[schema.Event]
	annotationBuffer *buffer.Buffer[schema.Annotation]
	provenanceBuffer *buffer.Buffer[schema.Provenance]
	queryService     *query.Service
	writer           *writer.ParquetWriter
	logger           *slog.Logger
}

// loggingMiddleware logs all incoming HTTP requests
func loggingMiddleware(logger *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		logger.Info("request", "method", r.Method, "path", r.URL.Path, "query", r.URL.RawQuery)
		next.ServeHTTP(w, r)
	})
}

func newHandler(eventBuf *buffer.Buffer[schema.Event], annotationBuf *buffer.Buffer[schema.Annotation], provenanceBuf *buffer.Buffer[schema.Provenance], qs *query.Service, w *writer.ParquetWriter, logger *slog.Logger) http.Handler {
	h := &handler{eventBuffer: eventBuf, annotationBuffer: annotationBuf, provenanceBuffer: provenanceBuf, queryService: qs, writer: w, logger: logger}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /events", h.handleEvents)
	mux.HandleFunc("POST /event", h.handleEvent)
	mux.HandleFunc("POST /annotations", h.handleAnnotations)
	mux.HandleFunc("POST /provenance", h.handleProvenance)
	mux.HandleFunc("GET /health", h.handleHealth)
	mux.HandleFunc("POST /query", h.handleQuery)
	mux.HandleFunc("GET /query/parquet-glob", h.handleParquetGlob)

	// Analytics endpoints for Grafana dashboards
	mux.HandleFunc("POST /analytics", h.handleAnalytics)
	mux.HandleFunc("POST /analytics/errors", h.handleErrorAnalysis)
	mux.HandleFunc("POST /analytics/filters", h.handleFilterAnalysis)
	mux.HandleFunc("GET /analytics/data-sources", h.handleDataSources)
	mux.HandleFunc("GET /analytics/flow-names", h.handleFlowNames)
	mux.HandleFunc("GET /analytics/annotation-keys", h.handleAnnotationKeys)
	mux.HandleFunc("GET /analytics/annotation-values", h.handleAnnotationValues)
	mux.HandleFunc("GET /analytics/stats", h.handleStats)

	// Provenance endpoints
	mux.HandleFunc("GET /provenance/stats", h.handleProvenanceStats)
	mux.HandleFunc("GET /provenance/query", h.handleProvenanceQuery)
	return loggingMiddleware(logger, mux)
}

func (h *handler) handleEvents(w http.ResponseWriter, r *http.Request) {
	var requests []schema.EventRequest
	if err := json.NewDecoder(r.Body).Decode(&requests); err != nil {
		h.logger.Error("failed to decode request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	now := time.Now()
	var rejected []string

	for _, req := range requests {
		event, err := convertEvent(req)
		if err != nil {
			h.logger.Warn("skipping invalid event", "error", err, "did", req.DID)
			rejected = append(rejected, fmt.Sprintf("%s: %s", req.DID, err.Error()))
			continue
		}
		if err := h.eventBuffer.Add(event); err != nil {
			h.logger.Error("failed to buffer event", "error", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}

		// Buffer any annotations that came with the event
		// Use event's creation_time so annotations partition with their events
		annotations := convertAnnotationsWithCreationTime(req.DID, req.Annotations, now, event.CreationTime)
		if len(annotations) > 0 {
			if err := h.annotationBuffer.AddBatch(annotations); err != nil {
				h.logger.Error("failed to buffer annotations", "error", err)
				http.Error(w, "internal error", http.StatusInternalServerError)
				return
			}
		}
	}

	if len(rejected) > 0 {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte(fmt.Sprintf("rejected %d events: %s", len(rejected), strings.Join(rejected, "; "))))
		return
	}

	w.WriteHeader(http.StatusAccepted)
	w.Write([]byte("accepted"))
}

func (h *handler) handleEvent(w http.ResponseWriter, r *http.Request) {
	var req schema.EventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Error("failed to decode request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	event, err := convertEvent(req)
	if err != nil {
		h.logger.Error("invalid event", "error", err, "did", req.DID)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if err := h.eventBuffer.Add(event); err != nil {
		h.logger.Error("failed to buffer event", "error", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	// Buffer any annotations that came with the event
	// Use event's creation_time so annotations partition with their events
	now := time.Now()
	annotations := convertAnnotationsWithCreationTime(req.DID, req.Annotations, now, event.CreationTime)
	if len(annotations) > 0 {
		if err := h.annotationBuffer.AddBatch(annotations); err != nil {
			h.logger.Error("failed to buffer annotations", "error", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}
	}

	w.WriteHeader(http.StatusAccepted)
	w.Write([]byte("accepted"))
}

func (h *handler) handleAnnotations(w http.ResponseWriter, r *http.Request) {
	var requests []schema.AnnotationRequest
	if err := json.NewDecoder(r.Body).Decode(&requests); err != nil {
		h.logger.Error("failed to decode annotation request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	now := time.Now()
	maxAge := now.Add(-maxAnnotationAge)

	for _, req := range requests {
		// Use creationTime from request for hour-based partitioning, fall back to now
		creationTime := now
		if req.CreationTime > 0 {
			creationTime = time.UnixMilli(req.CreationTime)
		}

		// Reject annotations for DIDs created too long ago
		if creationTime.Before(maxAge) {
			h.logger.Warn("rejecting annotation for old DID",
				"did", req.DID,
				"creation_time", creationTime,
				"max_age", maxAnnotationAge)
			http.Error(w, fmt.Sprintf("annotation rejected: DID creation_time too old (> %v ago)", maxAnnotationAge), http.StatusBadRequest)
			return
		}

		annotations := convertAnnotationsWithCreationTime(req.DID, req.Annotations, now, creationTime)
		if len(annotations) > 0 {
			if err := h.annotationBuffer.AddBatch(annotations); err != nil {
				h.logger.Error("failed to buffer annotations", "error", err)
				http.Error(w, "internal error", http.StatusInternalServerError)
				return
			}
		}
	}

	w.WriteHeader(http.StatusAccepted)
	w.Write([]byte("accepted"))
}

func (h *handler) handleProvenance(w http.ResponseWriter, r *http.Request) {
	var requests []schema.ProvenanceRequest
	if err := json.NewDecoder(r.Body).Decode(&requests); err != nil {
		h.logger.Error("failed to decode provenance request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	for _, req := range requests {
		record := schema.Provenance{
			DID:         req.DID,
			ParentDID:   req.ParentDID,
			SystemName:  req.SystemName,
			DataSource:  req.DataSource,
			Filename:    req.Filename,
			Transforms:  req.Transforms,
			DataSink:    req.DataSink,
			FinalState:  req.FinalState,
			Created:     time.UnixMilli(req.Created),
			Completed:   time.UnixMilli(req.Completed),
			Annotations: req.Annotations,
		}
		if err := h.provenanceBuffer.Add(record); err != nil {
			h.logger.Error("failed to buffer provenance record", "error", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}
	}

	w.WriteHeader(http.StatusAccepted)
	w.Write([]byte("accepted"))
}

func (h *handler) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("ok"))
}

type queryRequest struct {
	SQL string `json:"sql"`
}

func (h *handler) handleQuery(w http.ResponseWriter, r *http.Request) {
	var req queryRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Error("failed to decode query request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	if req.SQL == "" {
		http.Error(w, "sql field is required", http.StatusBadRequest)
		return
	}

	result, err := h.queryService.Query(req.SQL)
	if err != nil {
		h.logger.Error("query failed", "error", err, "sql", req.SQL)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result.ToGrafanaTable())
}

func (h *handler) handleParquetGlob(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"events":      h.writer.EventsDir(),
		"annotations": h.writer.AnnotationsDir(),
		"aggregated":  h.writer.AggregatedDir(),
	})
}

// Event time validation thresholds
const (
	maxEventAge    = 1 * time.Hour      // Reject events older than 1 hour
	maxEventFuture = 5 * time.Minute    // Reject events more than 5 min in the future
	maxAnnotationAge = 3 * 24 * time.Hour // Reject annotations for DIDs created > 3 days ago
)

func convertEvent(req schema.EventRequest) (schema.Event, error) {
	if req.CreationTime == 0 {
		return schema.Event{}, fmt.Errorf("creationTime is required")
	}

	now := time.Now()
	eventTime := time.UnixMilli(req.EventTime)

	// Reject events with event_time too old or in the future
	if eventTime.Before(now.Add(-maxEventAge)) {
		return schema.Event{}, fmt.Errorf("event_time too old (> %v ago): %v", maxEventAge, eventTime)
	}
	if eventTime.After(now.Add(maxEventFuture)) {
		return schema.Event{}, fmt.Errorf("event_time in future (> %v ahead): %v", maxEventFuture, eventTime)
	}

	return schema.Event{
		DID:          req.DID,
		DataSource:   req.DataSource,
		EventType:    req.EventType,
		EventTime:    eventTime,
		IngestTime:   now,
		CreationTime: time.UnixMilli(req.CreationTime),
		Bytes:        req.Bytes,
		FileCount:    req.FileCount,
		FlowName:     req.FlowName,
		ActionName:   req.ActionName,
		Cause:        req.Cause,
		IngressType:  req.IngressType,
	}, nil
}

func convertAnnotationsWithCreationTime(did string, annotations map[string]string, updateTime time.Time, creationTime time.Time) []schema.Annotation {
	if len(annotations) == 0 {
		return nil
	}

	result := make([]schema.Annotation, 0, len(annotations))
	for key, value := range annotations {
		result = append(result, schema.Annotation{
			DID:          did,
			Key:          key,
			Value:        value,
			UpdateTime:   updateTime,
			CreationTime: creationTime,
		})
	}
	return result
}

// Analytics handlers for Grafana dashboards

func (h *handler) handleAnalytics(w http.ResponseWriter, r *http.Request) {
	var req query.AnalyticsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Error("failed to decode analytics request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	// Parse time from request if provided as strings or unix timestamps
	if req.TimeFrom.IsZero() {
		req.TimeFrom = time.Now().Add(-24 * time.Hour)
	}
	if req.TimeTo.IsZero() {
		req.TimeTo = time.Now()
	}
	if req.IntervalMs == 0 {
		req.IntervalMs = 300000 // 5 minutes default
	}

	results, err := h.queryService.QueryAnalytics(req)
	if err != nil {
		h.logger.Error("analytics query failed", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

func (h *handler) handleErrorAnalysis(w http.ResponseWriter, r *http.Request) {
	var req query.EventAnalysisRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Error("failed to decode error analysis request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	if req.TimeFrom.IsZero() {
		req.TimeFrom = time.Now().Add(-24 * time.Hour)
	}
	if req.TimeTo.IsZero() {
		req.TimeTo = time.Now()
	}
	if req.IntervalMs == 0 {
		req.IntervalMs = 300000 // 5 minutes default
	}

	results, err := h.queryService.QueryEventAnalysis(req, "ERROR")
	if err != nil {
		h.logger.Error("error analysis query failed", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

func (h *handler) handleFilterAnalysis(w http.ResponseWriter, r *http.Request) {
	var req query.EventAnalysisRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Error("failed to decode filter analysis request", "error", err)
		http.Error(w, "invalid JSON", http.StatusBadRequest)
		return
	}

	if req.TimeFrom.IsZero() {
		req.TimeFrom = time.Now().Add(-24 * time.Hour)
	}
	if req.TimeTo.IsZero() {
		req.TimeTo = time.Now()
	}
	if req.IntervalMs == 0 {
		req.IntervalMs = 300000 // 5 minutes default
	}

	results, err := h.queryService.QueryEventAnalysis(req, "FILTER")
	if err != nil {
		h.logger.Error("filter analysis query failed", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

// variableOption represents a Grafana variable option
type variableOption struct {
	Text  string `json:"__text"`
	Value string `json:"__value"`
}

// toVariableOptions converts a string slice to Grafana variable options
func toVariableOptions(values []string) []variableOption {
	options := make([]variableOption, len(values))
	for i, v := range values {
		options[i] = variableOption{Text: v, Value: v}
	}
	return options
}

func (h *handler) handleDataSources(w http.ResponseWriter, r *http.Request) {
	timeFrom, timeTo := parseTimeRange(r)
	filtered := r.URL.Query().Get("filtered") == "true"

	values, err := h.queryService.GetDistinctValues("data_source", timeFrom, timeTo, filtered)
	if err != nil {
		h.logger.Error("failed to get data sources", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(toVariableOptions(values))
}

func (h *handler) handleFlowNames(w http.ResponseWriter, r *http.Request) {
	timeFrom, timeTo := parseTimeRange(r)
	filtered := r.URL.Query().Get("filtered") == "true"

	values, err := h.queryService.GetDistinctValues("flow_name", timeFrom, timeTo, filtered)
	if err != nil {
		h.logger.Error("failed to get flow names", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(toVariableOptions(values))
}

func (h *handler) handleAnnotationKeys(w http.ResponseWriter, r *http.Request) {
	timeFrom, timeTo := parseTimeRange(r)
	forGroup := r.URL.Query().Get("forGroup") == "true"
	filtered := r.URL.Query().Get("filtered") == "true"
	excludeKeys := r.URL.Query()["exclude"]

	keys, err := h.queryService.GetAnnotationKeys(timeFrom, timeTo, forGroup, filtered)
	if err != nil {
		h.logger.Error("failed to get annotation keys", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Filter out excluded keys (but not "None" since that's a valid choice)
	if len(excludeKeys) > 0 {
		excludeSet := make(map[string]bool)
		for _, k := range excludeKeys {
			if k != "" && k != "None" {
				excludeSet[k] = true
			}
		}
		filteredKeys := make([]string, 0, len(keys))
		for _, key := range keys {
			if !excludeSet[key] {
				filteredKeys = append(filteredKeys, key)
			}
		}
		keys = filteredKeys
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(toVariableOptions(keys))
}

const defaultValueLimit = 200 // Max annotation values to return in dropdown

func (h *handler) handleAnnotationValues(w http.ResponseWriter, r *http.Request) {
	key := r.URL.Query().Get("key")
	timeFrom, timeTo := parseTimeRange(r)
	filtered := r.URL.Query().Get("filtered") == "true"

	// Parse limit parameter (default: 200, max: 1000)
	limit := defaultValueLimit
	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil && l > 0 {
			limit = l
			if limit > 1000 {
				limit = 1000
			}
		}
	}

	var values []string
	var err error

	// Handle empty key, "None" selection, or Grafana multi-value format {a,b,c}
	if key == "" || key == "$__all" || key == "None" {
		// Return empty list - no values when no key is selected
		values = []string{}
	} else if len(key) > 2 && key[0] == '{' && key[len(key)-1] == '}' {
		// Grafana multi-value format: {key1,key2,key3}
		keys := strings.Split(key[1:len(key)-1], ",")
		values, err = h.queryService.GetAnnotationValuesForKeys(keys, timeFrom, timeTo, filtered)
	} else {
		values, err = h.queryService.GetAnnotationValues(key, timeFrom, timeTo, filtered)
	}

	if err != nil {
		h.logger.Error("failed to get annotation values", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Add "not present" option when a key is selected (allows filtering for missing annotations)
	if key != "" && key != "$__all" && key != "None" && !(len(key) > 2 && key[0] == '{') {
		values = append([]string{"not present"}, values...)
	}

	// Apply limit (after adding "not present" so it's always available)
	if len(values) > limit {
		values = values[:limit]
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(toVariableOptions(values))
}

func (h *handler) handleStats(w http.ResponseWriter, r *http.Request) {
	stats := h.queryService.GetDataStats()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (h *handler) handleProvenanceStats(w http.ResponseWriter, r *http.Request) {
	req := query.ProvenanceStatsRequest{
		DID:             r.URL.Query().Get("did"),
		ParentDID:       r.URL.Query().Get("parent_did"),
		DataSource:      r.URL.Query().Get("data_source"),
		DataSink:        r.URL.Query().Get("data_sink"),
		Filename:        r.URL.Query().Get("filename"),
		AnnotationKey:   r.URL.Query().Get("annotation_key"),
		AnnotationValue: r.URL.Query().Get("annotation_value"),
	}

	stats, err := h.queryService.GetProvenanceStats(req)
	if err != nil {
		h.logger.Error("failed to get provenance stats", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (h *handler) handleProvenanceQuery(w http.ResponseWriter, r *http.Request) {
	// Parse query parameters
	req := query.ProvenanceQueryRequest{
		DID:             r.URL.Query().Get("did"),
		ParentDID:       r.URL.Query().Get("parent_did"),
		SystemName:      r.URL.Query().Get("system_name"),
		DataSource:      r.URL.Query().Get("data_source"),
		DataSink:        r.URL.Query().Get("data_sink"),
		Filename:        r.URL.Query().Get("filename"),
		FinalState:      r.URL.Query().Get("final_state"),
		AnnotationKey:   r.URL.Query().Get("annotation_key"),
		AnnotationValue: r.URL.Query().Get("annotation_value"),
		Limit:           100, // Default limit
	}

	// Parse time range
	if from := r.URL.Query().Get("from"); from != "" {
		if ms, err := strconv.ParseInt(from, 10, 64); err == nil {
			req.From = time.UnixMilli(ms)
		}
	}
	if to := r.URL.Query().Get("to"); to != "" {
		if ms, err := strconv.ParseInt(to, 10, 64); err == nil {
			req.To = time.UnixMilli(ms)
		}
	}
	if limit := r.URL.Query().Get("limit"); limit != "" {
		if l, err := strconv.Atoi(limit); err == nil && l > 0 && l <= 1000 {
			req.Limit = l
		}
	}

	results, err := h.queryService.QueryProvenance(req)
	if err != nil {
		h.logger.Error("provenance query failed", "error", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

func parseTimeRange(r *http.Request) (time.Time, time.Time) {
	// Default to last 24 hours
	timeTo := time.Now()
	timeFrom := timeTo.Add(-24 * time.Hour)

	if fromStr := r.URL.Query().Get("from"); fromStr != "" {
		if fromMs, err := strconv.ParseInt(fromStr, 10, 64); err == nil {
			timeFrom = time.UnixMilli(fromMs)
		} else if t, err := time.Parse(time.RFC3339, fromStr); err == nil {
			timeFrom = t
		}
	}

	if toStr := r.URL.Query().Get("to"); toStr != "" {
		if toMs, err := strconv.ParseInt(toStr, 10, 64); err == nil {
			timeTo = time.UnixMilli(toMs)
		} else if t, err := time.Parse(time.RFC3339, toStr); err == nil {
			timeTo = t
		}
	}

	return timeFrom, timeTo
}
