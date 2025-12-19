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
 * ABOUTME: Tests for the HTTP handlers.
 * ABOUTME: Verifies event and annotation ingestion endpoints.
 */
package main

import (
	"bytes"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"sync/atomic"
	"testing"
	"time"

	"deltafi.org/deltafi-analytics/internal/buffer"
	"deltafi.org/deltafi-analytics/internal/schema"
	"deltafi.org/deltafi-analytics/internal/writer"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

func newTestHandler(t *testing.T, eventFlush buffer.FlushFunc[schema.Event], annotationFlush buffer.FlushFunc[schema.Annotation]) http.Handler {
	t.Helper()

	tmpDir, err := os.MkdirTemp("", "handler-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	t.Cleanup(func() { os.RemoveAll(tmpDir) })

	w, err := writer.New(writer.Config{OutputDir: tmpDir}, testLogger())
	if err != nil {
		t.Fatalf("failed to create writer: %v", err)
	}

	if eventFlush == nil {
		eventFlush = func(events []schema.Event) error { return nil }
	}
	if annotationFlush == nil {
		annotationFlush = func(annotations []schema.Annotation) error { return nil }
	}

	eventBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "events"}, eventFlush, testLogger())
	annotationBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "annotations"}, annotationFlush, testLogger())
	provenanceBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "provenance"}, func(p []schema.Provenance) error { return nil }, testLogger())

	return newHandler(eventBuf, annotationBuf, provenanceBuf, nil, w, testLogger())
}

func TestHandler_Health(t *testing.T) {
	h := newTestHandler(t, nil, nil)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", rec.Code)
	}
	if rec.Body.String() != "ok" {
		t.Errorf("expected body 'ok', got '%s'", rec.Body.String())
	}
}

func TestHandler_SingleEvent(t *testing.T) {
	var receivedEvents []schema.Event
	var receivedAnnotations []schema.Annotation

	eventFlush := func(events []schema.Event) error {
		receivedEvents = append(receivedEvents, events...)
		return nil
	}
	annotationFlush := func(annotations []schema.Annotation) error {
		receivedAnnotations = append(receivedAnnotations, annotations...)
		return nil
	}

	tmpDir, err := os.MkdirTemp("", "handler-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	w, _ := writer.New(writer.Config{OutputDir: tmpDir}, testLogger())
	eventBuf := buffer.New(buffer.Config{FlushCount: 1, Name: "events"}, eventFlush, testLogger())
	annotationBuf := buffer.New(buffer.Config{FlushCount: 1, Name: "annotations"}, annotationFlush, testLogger())
	provenanceBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "provenance"}, func(p []schema.Provenance) error { return nil }, testLogger())
	h := newHandler(eventBuf, annotationBuf, provenanceBuf, nil, w, testLogger())

	now := time.Now()
	eventReq := schema.EventRequest{
		DID:          "test-did",
		DataSource:   "test-source",
		EventType:    "INGRESS",
		EventTime:    now.UnixMilli(),
		CreationTime: now.UnixMilli(),
		Bytes:        1024,
		FileCount:    1,
		Annotations: map[string]string{
			"customer": "acme",
		},
	}

	body, _ := json.Marshal(eventReq)
	req := httptest.NewRequest(http.MethodPost, "/event", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Errorf("expected status 202, got %d", rec.Code)
	}

	if len(receivedEvents) != 1 {
		t.Fatalf("expected 1 event, got %d", len(receivedEvents))
	}
	if receivedEvents[0].DID != "test-did" {
		t.Errorf("expected DID 'test-did', got '%s'", receivedEvents[0].DID)
	}

	if len(receivedAnnotations) != 1 {
		t.Fatalf("expected 1 annotation, got %d", len(receivedAnnotations))
	}
	if receivedAnnotations[0].Key != "customer" || receivedAnnotations[0].Value != "acme" {
		t.Errorf("unexpected annotation: %+v", receivedAnnotations[0])
	}
}

func TestHandler_BatchEvents(t *testing.T) {
	var totalEvents atomic.Int32

	eventFlush := func(events []schema.Event) error {
		totalEvents.Add(int32(len(events)))
		return nil
	}

	tmpDir, err := os.MkdirTemp("", "handler-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	w, _ := writer.New(writer.Config{OutputDir: tmpDir}, testLogger())
	eventBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "events"}, eventFlush, testLogger())
	annotationBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "annotations"}, func(a []schema.Annotation) error { return nil }, testLogger())
	provenanceBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "provenance"}, func(p []schema.Provenance) error { return nil }, testLogger())
	h := newHandler(eventBuf, annotationBuf, provenanceBuf, nil, w, testLogger())

	now := time.Now().UnixMilli()
	events := []schema.EventRequest{
		{DID: "did-1", DataSource: "src", EventType: "INGRESS", EventTime: now, CreationTime: now},
		{DID: "did-2", DataSource: "src", EventType: "EGRESS", EventTime: now, CreationTime: now},
		{DID: "did-3", DataSource: "src", EventType: "ERROR", EventTime: now, CreationTime: now},
	}

	body, _ := json.Marshal(events)
	req := httptest.NewRequest(http.MethodPost, "/events", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Errorf("expected status 202, got %d", rec.Code)
	}

	// Manually flush to verify events were buffered
	eventBuf.Flush()

	if totalEvents.Load() != 3 {
		t.Errorf("expected 3 events received, got %d", totalEvents.Load())
	}
}

func TestHandler_InvalidJSON(t *testing.T) {
	h := newTestHandler(t, nil, nil)

	req := httptest.NewRequest(http.MethodPost, "/event", bytes.NewReader([]byte("not json")))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected status 400, got %d", rec.Code)
	}
}

func TestHandler_Annotations(t *testing.T) {
	var receivedAnnotations []schema.Annotation

	annotationFlush := func(annotations []schema.Annotation) error {
		receivedAnnotations = append(receivedAnnotations, annotations...)
		return nil
	}

	tmpDir, err := os.MkdirTemp("", "handler-test-*")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tmpDir)

	w, _ := writer.New(writer.Config{OutputDir: tmpDir}, testLogger())
	eventBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "events"}, func(e []schema.Event) error { return nil }, testLogger())
	annotationBuf := buffer.New(buffer.Config{FlushCount: 1, Name: "annotations"}, annotationFlush, testLogger())
	provenanceBuf := buffer.New(buffer.Config{FlushCount: 100, Name: "provenance"}, func(p []schema.Provenance) error { return nil }, testLogger())
	h := newHandler(eventBuf, annotationBuf, provenanceBuf, nil, w, testLogger())

	annotationReqs := []schema.AnnotationRequest{
		{
			DID: "did-1",
			Annotations: map[string]string{
				"status": "complete",
				"region": "us-east",
			},
		},
	}

	body, _ := json.Marshal(annotationReqs)
	req := httptest.NewRequest(http.MethodPost, "/annotations", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	h.ServeHTTP(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Errorf("expected status 202, got %d", rec.Code)
	}

	if len(receivedAnnotations) != 2 {
		t.Fatalf("expected 2 annotations, got %d", len(receivedAnnotations))
	}
}

func TestConvertEvent(t *testing.T) {
	now := time.Now()
	eventTime := now.UnixMilli()
	creationTime := now.UnixMilli()
	req := schema.EventRequest{
		DID:          "test-did",
		DataSource:   "test-source",
		EventType:    "INGRESS",
		EventTime:    eventTime,
		CreationTime: creationTime,
		Bytes:        2048,
		FileCount:    5,
		FlowName:     "test-flow",
		ActionName:   "test-action",
		Cause:        "test-cause",
	}

	event, err := convertEvent(req)
	if err != nil {
		t.Fatalf("convertEvent failed: %v", err)
	}

	if event.DID != "test-did" {
		t.Errorf("expected DID 'test-did', got '%s'", event.DID)
	}
	if event.DataSource != "test-source" {
		t.Errorf("expected DataSource 'test-source', got '%s'", event.DataSource)
	}
	if event.EventType != "INGRESS" {
		t.Errorf("expected EventType 'INGRESS', got '%s'", event.EventType)
	}
	if event.EventTime.UnixMilli() != eventTime {
		t.Errorf("expected EventTime %d, got %d", eventTime, event.EventTime.UnixMilli())
	}
	if event.CreationTime.UnixMilli() != creationTime {
		t.Errorf("expected CreationTime %d, got %d", creationTime, event.CreationTime.UnixMilli())
	}
	if event.Bytes != 2048 {
		t.Errorf("expected Bytes 2048, got %d", event.Bytes)
	}
	if event.FileCount != 5 {
		t.Errorf("expected FileCount 5, got %d", event.FileCount)
	}
	if event.FlowName != "test-flow" {
		t.Errorf("expected FlowName 'test-flow', got '%s'", event.FlowName)
	}
	if event.ActionName != "test-action" {
		t.Errorf("expected ActionName 'test-action', got '%s'", event.ActionName)
	}
	if event.Cause != "test-cause" {
		t.Errorf("expected Cause 'test-cause', got '%s'", event.Cause)
	}
	if event.IngestTime.IsZero() {
		t.Error("expected IngestTime to be set")
	}
}

func TestConvertEvent_RejectsOldEventTime(t *testing.T) {
	oldTime := time.Now().Add(-2 * time.Hour) // > 1 hour ago
	req := schema.EventRequest{
		DID:          "test-did",
		DataSource:   "test-source",
		EventType:    "INGRESS",
		EventTime:    oldTime.UnixMilli(),
		CreationTime: oldTime.UnixMilli(),
	}

	_, err := convertEvent(req)
	if err == nil {
		t.Error("expected error for old event_time")
	}
}

func TestConvertEvent_RejectsFutureEventTime(t *testing.T) {
	futureTime := time.Now().Add(10 * time.Minute) // > 5 min in future
	req := schema.EventRequest{
		DID:          "test-did",
		DataSource:   "test-source",
		EventType:    "INGRESS",
		EventTime:    futureTime.UnixMilli(),
		CreationTime: time.Now().UnixMilli(),
	}

	_, err := convertEvent(req)
	if err == nil {
		t.Error("expected error for future event_time")
	}
}

func TestConvertEvent_RejectsMissingCreationTime(t *testing.T) {
	req := schema.EventRequest{
		DID:        "test-did",
		DataSource: "test-source",
		EventType:  "INGRESS",
		EventTime:  time.Now().UnixMilli(),
		// CreationTime omitted (0)
	}

	_, err := convertEvent(req)
	if err == nil {
		t.Error("expected error for missing creationTime")
	}
}

func TestConvertAnnotations(t *testing.T) {
	now := time.Now()
	creationTime := now.Add(-1 * time.Hour)
	annotations := map[string]string{
		"key1": "value1",
		"key2": "value2",
	}

	result := convertAnnotationsWithCreationTime("test-did", annotations, now, creationTime)

	if len(result) != 2 {
		t.Fatalf("expected 2 annotations, got %d", len(result))
	}

	// Check that all annotations have correct DID and timestamps
	for _, a := range result {
		if a.DID != "test-did" {
			t.Errorf("expected DID 'test-did', got '%s'", a.DID)
		}
		if a.UpdateTime != now {
			t.Errorf("expected UpdateTime %v, got %v", now, a.UpdateTime)
		}
		if a.CreationTime != creationTime {
			t.Errorf("expected CreationTime %v, got %v", creationTime, a.CreationTime)
		}
	}
}

func TestConvertAnnotations_Empty(t *testing.T) {
	now := time.Now()
	result := convertAnnotationsWithCreationTime("test-did", nil, now, now)
	if result != nil {
		t.Errorf("expected nil for empty annotations, got %v", result)
	}

	result = convertAnnotationsWithCreationTime("test-did", map[string]string{}, now, now)
	if result != nil {
		t.Errorf("expected nil for empty map, got %v", result)
	}
}
