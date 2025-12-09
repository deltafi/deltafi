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
 * ABOUTME: Thread-safe generic buffer with configurable flush triggers.
 * ABOUTME: Flushes on count threshold or time interval, whichever comes first.
 */
package buffer

import (
	"context"
	"log/slog"
	"sync"
	"time"
)

// FlushFunc is called when the buffer needs to be flushed
type FlushFunc[T any] func(items []T) error

// Buffer accumulates items and flushes them based on count or time triggers
type Buffer[T any] struct {
	mu            sync.Mutex
	items         []T
	flushCount    int
	flushInterval time.Duration
	flushFunc     FlushFunc[T]
	logger        *slog.Logger
	name          string
}

// Config holds buffer configuration
type Config struct {
	FlushCount    int           // Flush when this many items accumulated
	FlushInterval time.Duration // Flush after this duration regardless of count
	Name          string        // Name for logging purposes
}

// New creates a new buffer
func New[T any](cfg Config, flushFunc FlushFunc[T], logger *slog.Logger) *Buffer[T] {
	if cfg.FlushCount <= 0 {
		cfg.FlushCount = 10000
	}
	if cfg.FlushInterval <= 0 {
		cfg.FlushInterval = 60 * time.Second
	}
	if cfg.Name == "" {
		cfg.Name = "buffer"
	}

	return &Buffer[T]{
		items:         make([]T, 0, cfg.FlushCount),
		flushCount:    cfg.FlushCount,
		flushInterval: cfg.FlushInterval,
		flushFunc:     flushFunc,
		logger:        logger,
		name:          cfg.Name,
	}
}

// Add adds an item to the buffer, triggering flush if count threshold reached
func (b *Buffer[T]) Add(item T) error {
	b.mu.Lock()
	b.items = append(b.items, item)
	shouldFlush := len(b.items) >= b.flushCount
	b.mu.Unlock()

	if shouldFlush {
		return b.Flush()
	}
	return nil
}

// AddBatch adds multiple items to the buffer
func (b *Buffer[T]) AddBatch(items []T) error {
	b.mu.Lock()
	b.items = append(b.items, items...)
	shouldFlush := len(b.items) >= b.flushCount
	b.mu.Unlock()

	if shouldFlush {
		return b.Flush()
	}
	return nil
}

// Flush writes all buffered items and clears the buffer
func (b *Buffer[T]) Flush() error {
	b.mu.Lock()
	if len(b.items) == 0 {
		b.mu.Unlock()
		return nil
	}

	// Take ownership of current items
	items := b.items
	b.items = make([]T, 0, b.flushCount)
	b.mu.Unlock()

	b.logger.Info("flushing buffer", "name", b.name, "count", len(items))
	return b.flushFunc(items)
}

// Len returns the current number of buffered items
func (b *Buffer[T]) Len() int {
	b.mu.Lock()
	defer b.mu.Unlock()
	return len(b.items)
}

// StartPeriodicFlush starts a goroutine that flushes the buffer periodically
func (b *Buffer[T]) StartPeriodicFlush(ctx context.Context) {
	ticker := time.NewTicker(b.flushInterval)
	go func() {
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				// Final flush on shutdown
				if err := b.Flush(); err != nil {
					b.logger.Error("final flush failed", "name", b.name, "error", err)
				}
				return
			case <-ticker.C:
				if err := b.Flush(); err != nil {
					b.logger.Error("periodic flush failed", "name", b.name, "error", err)
				}
			}
		}
	}()
}
