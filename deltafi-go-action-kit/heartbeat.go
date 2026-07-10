/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Heartbeat goroutine for reporting action liveness to DeltaFi core.
// ABOUTME: Sends periodic heartbeats and tracks long-running task executions.

package actionkit

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"
)

const (
	heartbeatInterval = 10 * time.Second
)

// actionExecution tracks an in-flight action execution.
type actionExecution struct {
	DID           string
	Clazz         string
	ActionName    string
	ThreadNum     int
	StartTime     string
	HeartbeatTime string
	AppName       string
}

// inFlightTracker tracks currently executing actions for long-running task reporting.
type inFlightTracker struct {
	mu    sync.Mutex
	tasks map[string]*actionExecution
}

func newInFlightTracker() *inFlightTracker {
	return &inFlightTracker{
		tasks: make(map[string]*actionExecution),
	}
}

func (t *inFlightTracker) start(did, clazz, actionName string, threadNum int, appName string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	now := formatTime(time.Now())
	t.tasks[longRunningTaskKeyFor(clazz, actionName, threadNum, did)] = &actionExecution{
		DID:           did,
		Clazz:         clazz,
		ActionName:    actionName,
		ThreadNum:     threadNum,
		StartTime:     now,
		HeartbeatTime: now,
		AppName:       appName,
	}
}

func (t *inFlightTracker) stop(did, clazz, actionName string, threadNum int) {
	t.mu.Lock()
	defer t.mu.Unlock()
	delete(t.tasks, longRunningTaskKeyFor(clazz, actionName, threadNum, did))
}

func (t *inFlightTracker) snapshot() []*actionExecution {
	t.mu.Lock()
	defer t.mu.Unlock()
	result := make([]*actionExecution, 0, len(t.tasks))
	for _, exec := range t.tasks {
		snapshot := *exec
		snapshot.HeartbeatTime = formatTime(time.Now())
		result = append(result, &snapshot)
	}
	return result
}

func longRunningTaskKeyFor(clazz, actionName string, threadNum int, did string) string {
	return fmt.Sprintf("%s:%s#%d:%s", clazz, actionName, threadNum, did)
}

func longRunningTaskRecord(exec *actionExecution) (string, string, error) {
	key := longRunningTaskKeyFor(exec.Clazz, exec.ActionName, exec.ThreadNum, exec.DID)
	values := []string{exec.StartTime, exec.HeartbeatTime}
	if exec.AppName != "" {
		values = append(values, exec.AppName)
	}
	data, err := json.Marshal(values)
	if err != nil {
		return "", "", err
	}
	return key, string(data), nil
}

// startHeartbeat runs a goroutine that sends heartbeats and reports long-running tasks.
// It tracks which tasks have been reported to Redis so completed tasks can be removed.
func startHeartbeat(ctx context.Context, wg *sync.WaitGroup, queue *actionEventQueue, actionNames []string, tracker *inFlightTracker, appName string) {
	wg.Go(func() {
		ticker := time.NewTicker(heartbeatInterval)
		defer ticker.Stop()

		// Keys previously reported to Redis, so we can remove them when done
		reportedKeys := make(map[string]bool)

		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				// Skip the entire iteration if shutdown is already in progress.
				// Without this, every redis call below errors with
				// context.Canceled and floods the log on shutdown.
				if ctx.Err() != nil {
					return
				}

				for _, name := range actionNames {
					if err := queue.Heartbeat(ctx, name); err != nil && ctx.Err() == nil {
						log.Printf("heartbeat error for %s: %v", name, err)
					}
				}

				// Build the set of currently long-running task keys
				currentKeys := make(map[string]bool)
				for _, exec := range tracker.snapshot() {
					key, data, err := longRunningTaskRecord(exec)
					if err != nil {
						continue
					}
					currentKeys[key] = true

					if err := queue.SetLongRunningTask(ctx, key, data); err != nil && ctx.Err() == nil {
						log.Printf("failed to report long-running task %s: %v", key, err)
					}
					reportedKeys[key] = true
				}

				// Remove completed tasks that were previously reported to Redis
				for key := range reportedKeys {
					if !currentKeys[key] {
						if err := queue.RemoveLongRunningTask(ctx, key); err != nil && ctx.Err() == nil {
							log.Printf("failed to remove long-running task %s: %v", key, err)
						}
						delete(reportedKeys, key)
					}
				}
			}
		}
	})
}
