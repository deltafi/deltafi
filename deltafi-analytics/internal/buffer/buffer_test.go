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
 * ABOUTME: Tests for the event buffer including flush triggers.
 * ABOUTME: Verifies count-based and time-based flush behavior.
 */
package buffer

import (
	"context"
	"log/slog"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"deltafi.org/deltafi-analytics/internal/schema"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
}

func TestBuffer_Add(t *testing.T) {
	var flushed []schema.Event
	var mu sync.Mutex

	buf := New(Config{FlushCount: 3, FlushInterval: time.Hour}, func(events []schema.Event) error {
		mu.Lock()
		flushed = append(flushed, events...)
		mu.Unlock()
		return nil
	}, testLogger())

	// Add 2 events - should not flush
	buf.Add(schema.Event{DID: "1"})
	buf.Add(schema.Event{DID: "2"})

	if buf.Len() != 2 {
		t.Errorf("expected 2 buffered events, got %d", buf.Len())
	}

	mu.Lock()
	if len(flushed) != 0 {
		t.Errorf("expected no flush yet, got %d events flushed", len(flushed))
	}
	mu.Unlock()

	// Add 3rd event - should trigger flush
	buf.Add(schema.Event{DID: "3"})

	mu.Lock()
	if len(flushed) != 3 {
		t.Errorf("expected 3 events flushed, got %d", len(flushed))
	}
	mu.Unlock()

	if buf.Len() != 0 {
		t.Errorf("expected 0 buffered events after flush, got %d", buf.Len())
	}
}

func TestBuffer_Flush_Empty(t *testing.T) {
	flushCalled := false

	buf := New(Config{FlushCount: 10, FlushInterval: time.Hour}, func(events []schema.Event) error {
		flushCalled = true
		return nil
	}, testLogger())

	// Flush empty buffer should not call flushFunc
	buf.Flush()

	if flushCalled {
		t.Error("flush should not be called for empty buffer")
	}
}

func TestBuffer_PeriodicFlush(t *testing.T) {
	var flushCount atomic.Int32

	buf := New(Config{FlushCount: 1000, FlushInterval: 50 * time.Millisecond}, func(events []schema.Event) error {
		flushCount.Add(1)
		return nil
	}, testLogger())

	ctx, cancel := context.WithCancel(context.Background())
	buf.StartPeriodicFlush(ctx)

	// Add one event
	buf.Add(schema.Event{DID: "1"})

	// Wait for periodic flush
	time.Sleep(100 * time.Millisecond)

	if flushCount.Load() < 1 {
		t.Error("expected at least one periodic flush")
	}

	// Cancel should trigger final flush
	cancel()
	time.Sleep(20 * time.Millisecond)
}

func TestBuffer_Concurrent(t *testing.T) {
	var totalFlushed atomic.Int32

	buf := New(Config{FlushCount: 100, FlushInterval: time.Hour}, func(events []schema.Event) error {
		totalFlushed.Add(int32(len(events)))
		return nil
	}, testLogger())

	// Concurrent writers
	var wg sync.WaitGroup
	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 50; j++ {
				buf.Add(schema.Event{DID: "test"})
			}
		}()
	}
	wg.Wait()

	// Flush remaining
	buf.Flush()

	if totalFlushed.Load() != 500 {
		t.Errorf("expected 500 total events, got %d", totalFlushed.Load())
	}
}
