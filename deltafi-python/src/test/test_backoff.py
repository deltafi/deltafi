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

import pytest

from deltafi.backoff import (
    POLL_BACKOFF_BASE_S,
    POLL_BACKOFF_MAX_S,
    next_poll_backoff,
    jitter_backoff,
    backoff_after_error,
)


def test_defaults_match_java():
    assert POLL_BACKOFF_BASE_S == pytest.approx(0.1)
    assert POLL_BACKOFF_MAX_S == pytest.approx(5.0)


def test_next_poll_backoff_doubles_and_caps():
    assert next_poll_backoff(0.1) == pytest.approx(0.2)
    assert next_poll_backoff(0.2) == pytest.approx(0.4)
    assert next_poll_backoff(3.2) == pytest.approx(5.0)  # 6.4 capped at 5s
    assert next_poll_backoff(5.0) == pytest.approx(5.0)  # stays at cap


def test_next_poll_backoff_floors_at_base():
    assert next_poll_backoff(0.0) == pytest.approx(0.2)
    assert next_poll_backoff(0.05) == pytest.approx(0.2)


def test_jitter_backoff_within_half_to_full():
    for d in (0.002, 0.1, 0.2, 5.0):
        for _ in range(1000):
            j = jitter_backoff(d)
            assert d / 2 <= j <= d


def test_jitter_backoff_zero():
    assert jitter_backoff(0.0) == 0.0


def test_backoff_after_error_sleeps_jittered_then_escalates():
    sleeps = []
    next_backoff = backoff_after_error(0.1, sleep=sleeps.append)
    assert next_backoff == pytest.approx(0.2)
    assert len(sleeps) == 1
    assert 0.05 <= sleeps[0] <= 0.1
