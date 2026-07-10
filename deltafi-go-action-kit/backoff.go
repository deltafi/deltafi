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
// ABOUTME: Capped exponential backoff with jitter for the queue poll loops.
// ABOUTME: Keeps a downed Valkey from causing a tight reconnect spin; escalates 100ms -> 5s.

package actionkit

import (
	"context"
	"math/rand/v2"
	"time"
)

// pollBackoffBase and pollBackoffMax bound the poll-loop retry delay. They are vars
// (not consts) so tests can shrink or lengthen them. The delay doubles from base to max
// on consecutive read errors and resets to base after a healthy read.
var (
	pollBackoffBase = 100 * time.Millisecond
	pollBackoffMax  = 5 * time.Second
)

// nextPollBackoff doubles current, floored at pollBackoffBase and capped at pollBackoffMax.
func nextPollBackoff(current time.Duration) time.Duration {
	d := current
	if d < pollBackoffBase {
		d = pollBackoffBase
	}
	d *= 2
	if d > pollBackoffMax {
		d = pollBackoffMax
	}
	return d
}

// jitterBackoff returns a duration in [d/2, d] so poll loops across threads do not
// retry in lockstep (thundering herd). math/rand/v2's top-level source is safe for
// concurrent use.
func jitterBackoff(d time.Duration) time.Duration {
	if d <= 0 {
		return d
	}
	half := int64(d / 2)
	return d - time.Duration(rand.Int64N(half+1))
}

// sleepCtx blocks for d or until ctx is cancelled. It returns false if ctx was
// cancelled (the caller should stop), true if the delay elapsed.
func sleepCtx(ctx context.Context, d time.Duration) bool {
	if d <= 0 {
		return ctx.Err() == nil
	}
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}

// pollSleep is the seam poll loops use to wait between error retries; tests replace it.
var pollSleep = sleepCtx

// backoffAfterError waits (jittered) for the current backoff, then returns the next
// backoff to use. It returns ok=false if ctx was cancelled during the wait, in which
// case the caller should stop and the returned duration is unchanged.
func backoffAfterError(ctx context.Context, current time.Duration) (next time.Duration, ok bool) {
	if !pollSleep(ctx, jitterBackoff(current)) {
		return current, false
	}
	return nextPollBackoff(current), true
}
