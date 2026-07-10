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
// ABOUTME: Tests for the poll-loop backoff primitives (escalation, cap, jitter, ctx-aware sleep).
// ABOUTME: Verifies exponential backoff to a 5s cap with jitter, matching the Java action kit.

package actionkit

import (
	"context"
	"testing"
	"time"
)

func TestNextPollBackoff(t *testing.T) {
	cases := []struct{ in, want time.Duration }{
		{100 * time.Millisecond, 200 * time.Millisecond},
		{200 * time.Millisecond, 400 * time.Millisecond},
		{3200 * time.Millisecond, 5 * time.Second}, // 6400ms capped at 5s
		{5 * time.Second, 5 * time.Second},          // stays at cap
		{0, 200 * time.Millisecond},                 // floored to base, then doubled
		{50 * time.Millisecond, 200 * time.Millisecond},
	}
	for _, c := range cases {
		if got := nextPollBackoff(c.in); got != c.want {
			t.Errorf("nextPollBackoff(%v) = %v, want %v", c.in, got, c.want)
		}
	}
}

func TestJitterBackoff_WithinHalfToFull(t *testing.T) {
	for _, d := range []time.Duration{2 * time.Millisecond, 100 * time.Millisecond, 200 * time.Millisecond, 5 * time.Second} {
		lo := d - d/2
		for i := 0; i < 1000; i++ {
			got := jitterBackoff(d)
			if got < lo || got > d {
				t.Fatalf("jitterBackoff(%v) = %v, want within [%v, %v]", d, got, lo, d)
			}
		}
	}
}

func TestJitterBackoff_Zero(t *testing.T) {
	if got := jitterBackoff(0); got != 0 {
		t.Errorf("jitterBackoff(0) = %v, want 0", got)
	}
}

func TestSleepCtx_CancelledReturnsFalse(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if sleepCtx(ctx, time.Hour) {
		t.Fatal("sleepCtx should return false when ctx is already cancelled")
	}
}

func TestSleepCtx_ElapsesReturnsTrue(t *testing.T) {
	start := time.Now()
	if !sleepCtx(context.Background(), 10*time.Millisecond) {
		t.Fatal("sleepCtx should return true after the delay elapses")
	}
	if elapsed := time.Since(start); elapsed < 10*time.Millisecond {
		t.Fatalf("sleepCtx returned after %v, want >= 10ms", elapsed)
	}
}

func TestBackoffAfterError_EscalatesOnSuccessfulWait(t *testing.T) {
	var slept time.Duration
	old := pollSleep
	pollSleep = func(ctx context.Context, d time.Duration) bool { slept = d; return true }
	defer func() { pollSleep = old }()

	next, ok := backoffAfterError(context.Background(), 100*time.Millisecond)
	if !ok {
		t.Fatal("backoffAfterError should return ok=true when the wait completes")
	}
	if next != 200*time.Millisecond {
		t.Errorf("next backoff = %v, want 200ms", next)
	}
	if lo := 50 * time.Millisecond; slept < lo || slept > 100*time.Millisecond {
		t.Errorf("slept %v, want jittered within [50ms, 100ms]", slept)
	}
}

func TestBackoffAfterError_StopsWhenWaitCancelled(t *testing.T) {
	old := pollSleep
	pollSleep = func(ctx context.Context, d time.Duration) bool { return false }
	defer func() { pollSleep = old }()

	next, ok := backoffAfterError(context.Background(), 100*time.Millisecond)
	if ok {
		t.Fatal("backoffAfterError should return ok=false when the wait is cancelled")
	}
	if next != 100*time.Millisecond {
		t.Errorf("next backoff = %v, want unchanged 100ms", next)
	}
}
