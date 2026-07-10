#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

import random
import time

# Capped exponential backoff for the queue poll loops, matching the Java and Go action
# kits: escalate 100ms -> 5s on consecutive read errors, reset to base after a healthy read.
POLL_BACKOFF_BASE_S = 0.1
POLL_BACKOFF_MAX_S = 5.0


def next_poll_backoff(current):
    """Double current, floored at the base delay and capped at the max delay."""
    return min(max(current, POLL_BACKOFF_BASE_S) * 2, POLL_BACKOFF_MAX_S)


def jitter_backoff(d):
    """Return a delay in [d/2, d] so poll loops don't retry in lockstep (thundering herd)."""
    if d <= 0:
        return d
    return d - random.random() * (d / 2)


def backoff_after_error(backoff, sleep=time.sleep):
    """Sleep (jittered) for the current backoff, then return the next backoff to use."""
    sleep(jitter_backoff(backoff))
    return next_poll_backoff(backoff)
