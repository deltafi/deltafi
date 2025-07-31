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
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestDelayQueue_BasicOperations(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	// Track processed items
	var processed []string
	var mu sync.Mutex

	processor := func(item string) error {
		mu.Lock()
		defer mu.Unlock()
		processed = append(processed, item)
		return nil
	}

	queue := NewDelayQueue[string](processor, logger)

	// Add items with different delays
	queue.Add("item1", 100*time.Millisecond)
	queue.Add("item2", 50*time.Millisecond)
	queue.Add("item3", 200*time.Millisecond)

	assert.Equal(t, 3, queue.Size())
	assert.False(t, queue.IsEmpty())

	// Start processing
	queue.Start()

	// Wait for all items to be processed
	time.Sleep(300 * time.Millisecond)

	mu.Lock()
	assert.Len(t, processed, 3)
	// Check order (should be processed in order of delay)
	assert.Equal(t, "item2", processed[0]) // 50ms delay
	assert.Equal(t, "item1", processed[1]) // 100ms delay
	assert.Equal(t, "item3", processed[2]) // 200ms delay
	mu.Unlock()

	queue.Stop()
}

func TestDelayQueue_AddAt(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	var processed []string
	var mu sync.Mutex

	processor := func(item string) error {
		mu.Lock()
		defer mu.Unlock()
		processed = append(processed, item)
		return nil
	}

	queue := NewDelayQueue[string](processor, logger)

	now := time.Now()

	// Add items with specific times
	queue.AddAt("item1", now.Add(100*time.Millisecond))
	queue.AddAt("item2", now.Add(50*time.Millisecond))
	queue.AddAt("item3", now.Add(200*time.Millisecond))

	queue.Start()
	time.Sleep(300 * time.Millisecond)

	mu.Lock()
	assert.Len(t, processed, 3)
	assert.Equal(t, "item2", processed[0])
	assert.Equal(t, "item1", processed[1])
	assert.Equal(t, "item3", processed[2])
	mu.Unlock()

	queue.Stop()
}

func TestDelayQueue_Peek(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	// Queue should be empty initially
	item, scheduled, exists := queue.Peek()
	assert.False(t, exists)
	assert.Empty(t, item)
	assert.True(t, scheduled.IsZero())

	// Add an item
	queue.Add("test", 100*time.Millisecond)

	item, scheduled, exists = queue.Peek()
	assert.True(t, exists)
	assert.Equal(t, "test", item)
	assert.True(t, scheduled.After(time.Now()))

	queue.Stop()
}

func TestDelayQueue_GetNextScheduledTime(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	// Empty queue
	scheduled, exists := queue.GetNextScheduledTime()
	assert.False(t, exists)
	assert.True(t, scheduled.IsZero())

	// Add item
	queue.Add("test", 100*time.Millisecond)

	scheduled, exists = queue.GetNextScheduledTime()
	assert.True(t, exists)
	assert.True(t, scheduled.After(time.Now()))

	queue.Stop()
}

func TestDelayQueue_Clear(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	queue.Add("item1", 100*time.Millisecond)
	queue.Add("item2", 200*time.Millisecond)

	assert.Equal(t, 2, queue.Size())

	queue.Clear()

	assert.Equal(t, 0, queue.Size())
	assert.True(t, queue.IsEmpty())

	queue.Stop()
}

func TestDelayQueue_GetAllItems(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	queue.Add("item1", 100*time.Millisecond)
	queue.Add("item2", 200*time.Millisecond)

	items := queue.GetAllItems()
	assert.Len(t, items, 2)

	// Items should be in order of scheduled time
	assert.Equal(t, "item1", items[0].Item)
	assert.Equal(t, "item2", items[1].Item)

	queue.Stop()
}

func TestDelayQueue_ProcessorError(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	var processed []string
	var mu sync.Mutex

	processor := func(item string) error {
		mu.Lock()
		defer mu.Unlock()
		processed = append(processed, item)

		if item == "error" {
			return assert.AnError
		}
		return nil
	}

	queue := NewDelayQueue[string](processor, logger)

	queue.Add("normal", 50*time.Millisecond)
	queue.Add("error", 100*time.Millisecond)

	queue.Start()
	time.Sleep(200 * time.Millisecond)

	mu.Lock()
	assert.Len(t, processed, 2) // Both items should be processed even if one fails
	mu.Unlock()

	queue.Stop()
}

func TestDelayQueue_ConcurrentAccess(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	var processed []string
	var mu sync.Mutex

	processor := func(item string) error {
		mu.Lock()
		defer mu.Unlock()
		processed = append(processed, item)
		return nil
	}

	queue := NewDelayQueue[string](processor, logger)
	queue.Start()

	// Add items concurrently
	var wg sync.WaitGroup
	for i := 0; i < 10; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			queue.Add("item"+string(rune('0'+id)), time.Duration(id*10)*time.Millisecond)
		}(i)
	}

	wg.Wait()

	// Wait for processing
	time.Sleep(200 * time.Millisecond)

	mu.Lock()
	assert.Len(t, processed, 10)
	mu.Unlock()

	queue.Stop()
}

func TestDelayQueue_ContextCancellation(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	queue.Start()

	// Add an item with a long delay
	queue.Add("long-delay", 5*time.Second)

	// Stop immediately
	queue.Stop()

	// Queue should be stopped and item not processed
	assert.Equal(t, 1, queue.Size())
}

func TestDelayQueue_EmptyQueueBehavior(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	queue.Start()

	// Let it run for a bit with no items
	time.Sleep(100 * time.Millisecond)

	assert.True(t, queue.IsEmpty())
	assert.Equal(t, 0, queue.Size())

	queue.Stop()
}

// Example usage with custom struct
type Task struct {
	ID       string
	Priority int
	Data     map[string]interface{}
}

func TestDelayQueue_WithCustomStruct(t *testing.T) {
	logger, _ := zap.NewDevelopment()

	var processed []Task
	var mu sync.Mutex

	processor := func(task Task) error {
		mu.Lock()
		defer mu.Unlock()
		processed = append(processed, task)
		return nil
	}

	queue := NewDelayQueue[Task](processor, logger)

	task1 := Task{ID: "task1", Priority: 1, Data: map[string]interface{}{"key": "value1"}}
	task2 := Task{ID: "task2", Priority: 2, Data: map[string]interface{}{"key": "value2"}}

	queue.Add(task1, 50*time.Millisecond)
	queue.Add(task2, 100*time.Millisecond)

	queue.Start()
	time.Sleep(200 * time.Millisecond)

	mu.Lock()
	assert.Len(t, processed, 2)
	assert.Equal(t, "task1", processed[0].ID)
	assert.Equal(t, "task2", processed[1].ID)
	mu.Unlock()

	queue.Stop()
}

// Benchmark tests
func BenchmarkDelayQueue_Add(b *testing.B) {
	logger, _ := zap.NewDevelopment()
	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		queue.Add("item", time.Duration(i)*time.Millisecond)
	}

	queue.Stop()
}

func BenchmarkDelayQueue_Peek(b *testing.B) {
	logger, _ := zap.NewDevelopment()
	processor := func(item string) error { return nil }
	queue := NewDelayQueue[string](processor, logger)

	// Add some items
	for i := 0; i < 100; i++ {
		queue.Add("item", time.Duration(i)*time.Millisecond)
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		queue.Peek()
	}

	queue.Stop()
}
