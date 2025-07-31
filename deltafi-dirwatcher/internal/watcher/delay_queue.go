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
package watcher

import (
	"container/heap"
	"context"
	"sync"
	"time"

	"go.uber.org/zap"
)

// DelayQueueItem represents an item in the delay queue with its scheduled time
type DelayQueueItem[T any] struct {
	Item      T
	Scheduled time.Time
	index     int // Used by the heap implementation
}

// DelayQueue is a generic delay queue that efficiently manages items with scheduled processing times
type DelayQueue[T any] struct {
	items     *delayQueueHeap[T]
	mutex     sync.RWMutex
	logger    *zap.Logger
	ctx       context.Context
	cancel    context.CancelFunc
	done      chan struct{}
	processor func(T) error
}

// delayQueueHeap implements heap.Interface for efficient priority queue operations
type delayQueueHeap[T any] []*DelayQueueItem[T]

func (h delayQueueHeap[T]) Len() int { return len(h) }

func (h delayQueueHeap[T]) Less(i, j int) bool {
	return h[i].Scheduled.Before(h[j].Scheduled)
}

func (h delayQueueHeap[T]) Swap(i, j int) {
	h[i], h[j] = h[j], h[i]
	h[i].index = i
	h[j].index = j
}

func (h *delayQueueHeap[T]) Push(x interface{}) {
	n := len(*h)
	item := x.(*DelayQueueItem[T])
	item.index = n
	*h = append(*h, item)
}

func (h *delayQueueHeap[T]) Pop() interface{} {
	old := *h
	n := len(old)
	item := old[n-1]
	old[n-1] = nil  // avoid memory leak
	item.index = -1 // for safety
	*h = old[0 : n-1]
	return item
}

// NewDelayQueue creates a new delay queue with the specified processor function
func NewDelayQueue[T any](processor func(T) error, logger *zap.Logger) *DelayQueue[T] {
	ctx, cancel := context.WithCancel(context.Background())

	return &DelayQueue[T]{
		items:     &delayQueueHeap[T]{},
		logger:    logger,
		ctx:       ctx,
		cancel:    cancel,
		done:      make(chan struct{}),
		processor: processor,
	}
}

// Add adds an item to the delay queue with the specified delay
func (dq *DelayQueue[T]) Add(item T, delay time.Duration) error {
	dq.mutex.Lock()
	defer dq.mutex.Unlock()

	queueItem := &DelayQueueItem[T]{
		Item:      item,
		Scheduled: time.Now().Add(delay),
	}

	heap.Push(dq.items, queueItem)

	if dq.logger != nil {
		dq.logger.Debug("Added item to delay queue",
			zap.Duration("delay", delay),
			zap.Time("scheduled", queueItem.Scheduled))
	}

	return nil
}

// AddAt adds an item to the delay queue scheduled for a specific time
func (dq *DelayQueue[T]) AddAt(item T, scheduledTime time.Time) error {
	dq.mutex.Lock()
	defer dq.mutex.Unlock()

	queueItem := &DelayQueueItem[T]{
		Item:      item,
		Scheduled: scheduledTime,
	}

	heap.Push(dq.items, queueItem)

	if dq.logger != nil {
		dq.logger.Debug("Added item to delay queue",
			zap.Time("scheduled", queueItem.Scheduled))
	}

	return nil
}

// Peek returns the next item to be processed without removing it
func (dq *DelayQueue[T]) Peek() (T, time.Time, bool) {
	dq.mutex.RLock()
	defer dq.mutex.RUnlock()

	if dq.items.Len() == 0 {
		var zero T
		return zero, time.Time{}, false
	}

	item := (*dq.items)[0]
	return item.Item, item.Scheduled, true
}

// Size returns the current number of items in the queue
func (dq *DelayQueue[T]) Size() int {
	dq.mutex.RLock()
	defer dq.mutex.RUnlock()
	return dq.items.Len()
}

// IsEmpty returns true if the queue is empty
func (dq *DelayQueue[T]) IsEmpty() bool {
	return dq.Size() == 0
}

// Start begins processing the delay queue
func (dq *DelayQueue[T]) Start() {
	go dq.processLoop()
}

// Stop stops the delay queue processing
func (dq *DelayQueue[T]) Stop() {
	dq.cancel()
	select {
	case <-dq.done:
		// Successfully stopped
	case <-time.After(5 * time.Second):
		// Timeout after 5 seconds
		if dq.logger != nil {
			dq.logger.Warn("Delay queue stop timed out")
		}
	}
}

// processLoop continuously monitors the queue for items ready to be processed
func (dq *DelayQueue[T]) processLoop() {
	defer close(dq.done)

	for {
		select {
		case <-dq.ctx.Done():
			return
		default:
			// Check if there's an item ready to be processed
			item, scheduled, exists := dq.Peek()
			if !exists {
				// No items in queue, wait a bit before checking again
				select {
				case <-dq.ctx.Done():
					return
				case <-time.After(100 * time.Millisecond):
					continue
				}
			}

			now := time.Now()
			if now.Before(scheduled) {
				// Item not ready yet, wait until it's time or a new item is added
				waitTime := scheduled.Sub(now)
				if waitTime > 100*time.Millisecond {
					waitTime = 100 * time.Millisecond
				}
				select {
				case <-dq.ctx.Done():
					return
				case <-time.After(waitTime):
					continue
				}
			}

			// Item is ready, remove it and process
			dq.mutex.Lock()
			if dq.items.Len() > 0 {
				heap.Pop(dq.items)
			}
			dq.mutex.Unlock()

			// Process the item
			if err := dq.processor(item); err != nil {
				if dq.logger != nil {
					dq.logger.Error("Failed to process delay queue item",
						zap.Error(err))
				}
			} else if dq.logger != nil {
				dq.logger.Debug("Successfully processed delay queue item")
			}
		}
	}
}

// Clear removes all items from the queue
func (dq *DelayQueue[T]) Clear() {
	dq.mutex.Lock()
	defer dq.mutex.Unlock()

	// Reset the heap
	dq.items = &delayQueueHeap[T]{}
}

// GetNextScheduledTime returns the time of the next item to be processed
func (dq *DelayQueue[T]) GetNextScheduledTime() (time.Time, bool) {
	_, scheduled, exists := dq.Peek()
	return scheduled, exists
}

// GetAllItems returns all items in the queue (for debugging/monitoring purposes)
func (dq *DelayQueue[T]) GetAllItems() []DelayQueueItem[T] {
	dq.mutex.RLock()
	defer dq.mutex.RUnlock()

	items := make([]DelayQueueItem[T], len(*dq.items))
	for i, item := range *dq.items {
		items[i] = DelayQueueItem[T]{
			Item:      item.Item,
			Scheduled: item.Scheduled,
		}
	}
	return items
}
