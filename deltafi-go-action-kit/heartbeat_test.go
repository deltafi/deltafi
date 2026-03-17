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
// ABOUTME: Tests for the in-flight task tracker.
// ABOUTME: Verifies start/stop/snapshot lifecycle and concurrent safety of inFlightTracker.

package actionkit

import (
	"encoding/json"
	"sync"
	"testing"
	"time"
)

func TestInFlightTracker_StartAndStop(t *testing.T) {
	tracker := newInFlightTracker()

	tracker.start("did-1", "Action1", "app-1")

	snap := tracker.snapshot()
	if len(snap) != 1 {
		t.Fatalf("expected 1 task, got %d", len(snap))
	}
	if snap[0].DID != "did-1" {
		t.Errorf("DID = %q", snap[0].DID)
	}
	if snap[0].ActionName != "Action1" {
		t.Errorf("ActionName = %q", snap[0].ActionName)
	}
	if snap[0].AppName != "app-1" {
		t.Errorf("AppName = %q", snap[0].AppName)
	}

	tracker.stop("did-1", "Action1")

	snap = tracker.snapshot()
	if len(snap) != 0 {
		t.Errorf("expected 0 tasks after stop, got %d", len(snap))
	}
}

func TestInFlightTracker_MultipleTasks(t *testing.T) {
	tracker := newInFlightTracker()

	tracker.start("did-1", "Action1", "app")
	tracker.start("did-2", "Action2", "app")
	tracker.start("did-3", "Action1", "app")

	snap := tracker.snapshot()
	if len(snap) != 3 {
		t.Fatalf("expected 3 tasks, got %d", len(snap))
	}

	// Stop one
	tracker.stop("did-2", "Action2")

	snap = tracker.snapshot()
	if len(snap) != 2 {
		t.Errorf("expected 2 tasks after stop, got %d", len(snap))
	}
}

func TestInFlightTracker_StopNonexistent(t *testing.T) {
	tracker := newInFlightTracker()

	// Should not panic
	tracker.stop("nonexistent", "Action")

	snap := tracker.snapshot()
	if len(snap) != 0 {
		t.Errorf("expected 0 tasks, got %d", len(snap))
	}
}

func TestInFlightTracker_SnapshotUpdatesHeartbeatTime(t *testing.T) {
	tracker := newInFlightTracker()
	tracker.start("did-1", "Action1", "app")

	// Wait a tiny bit so heartbeat time differs
	time.Sleep(1 * time.Millisecond)

	snap := tracker.snapshot()
	if len(snap) != 1 {
		t.Fatalf("expected 1 task, got %d", len(snap))
	}

	// HeartbeatTime should be recent (not the original StartTime)
	hbt, err := time.Parse(time.RFC3339Nano, snap[0].HeartbeatTime)
	if err != nil {
		t.Fatalf("invalid HeartbeatTime: %q", snap[0].HeartbeatTime)
	}
	if time.Since(hbt) > 1*time.Second {
		t.Errorf("HeartbeatTime too old: %v", hbt)
	}
}

func TestInFlightTracker_SnapshotIsCopy(t *testing.T) {
	tracker := newInFlightTracker()
	tracker.start("did-1", "Action1", "app")

	snap := tracker.snapshot()

	// Modify the snapshot — should not affect tracker
	snap[0].DID = "modified"

	snap2 := tracker.snapshot()
	if snap2[0].DID == "modified" {
		t.Error("snapshot should return copies, not references")
	}
}

func TestInFlightTracker_ConcurrentAccess(t *testing.T) {
	tracker := newInFlightTracker()

	var wg sync.WaitGroup
	for i := range 100 {
		wg.Add(3)
		did := "did-" + string(rune('A'+i%26))
		go func() {
			defer wg.Done()
			tracker.start(did, "Act", "app")
		}()
		go func() {
			defer wg.Done()
			tracker.snapshot()
		}()
		go func() {
			defer wg.Done()
			tracker.stop(did, "Act")
		}()
	}
	wg.Wait()

	// Should not panic or deadlock
}

func TestActionExecutionJSON(t *testing.T) {
	exec := actionExecution{
		DID:           "did-1",
		ActionName:    "Act",
		StartTime:     "2025-01-01T00:00:01Z",
		HeartbeatTime: "2025-01-01T00:00:11Z",
		AppName:       "app-1",
	}

	// Verify all fields are JSON-tagged
	data, err := json.Marshal(exec)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]string
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	expected := map[string]string{
		"did":           "did-1",
		"actionName":    "Act",
		"startTime":     "2025-01-01T00:00:01Z",
		"heartbeatTime": "2025-01-01T00:00:11Z",
		"appName":       "app-1",
	}
	for k, want := range expected {
		if parsed[k] != want {
			t.Errorf("%s = %q, want %q", k, parsed[k], want)
		}
	}

	// Verify actionCreated is NOT present
	if _, ok := parsed["actionCreated"]; ok {
		t.Error("actionCreated should not be in JSON output")
	}
}
