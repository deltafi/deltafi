#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import time

import redis
from deltafi.actioneventqueue import ActionEventQueue
from mockito import when, mock, unstub

TEST_URL = "http://127.0.0.1:12345"
TEST_ACTION = "Action"
TEST_ITEM = "1234"


def test_action_event_queue_size():
    unstub()
    mock_pool = mock(redis.ConnectionPool)
    mock_conn = mock(redis.Redis)

    when(redis).ConnectionPool(...).thenReturn(mock_pool)
    when(redis).Redis(connection_pool=mock_pool).thenReturn(mock_conn)
    when(mock_conn).zcard(TEST_ACTION).thenReturn(1)

    service = ActionEventQueue(TEST_URL, 5, "password", None)
    assert service.size(TEST_ACTION) == 1


def test_action_event_queue_put():
    unstub()
    mock_pool = mock(redis.ConnectionPool)
    mock_conn = mock(redis.Redis)

    when(redis).ConnectionPool(...).thenReturn(mock_pool)
    when(redis).Redis(connection_pool=mock_pool).thenReturn(mock_conn)
    when(time).time().thenReturn(1.6)
    when(mock_conn).zadd(TEST_ACTION, {TEST_ITEM: 1600}, nx=True).thenReturn(1)

    service = ActionEventQueue(TEST_URL, 5, "password", None)
    assert service.put(TEST_ACTION, TEST_ITEM) == 1


def test_action_event_queue_take():
    unstub()
    mock_pool = mock(redis.ConnectionPool)
    mock_conn = mock(redis.Redis)

    result_tuple = (TEST_ACTION, TEST_ITEM, 1600)

    when(redis).ConnectionPool(...).thenReturn(mock_pool)
    when(redis).Redis(connection_pool=mock_pool).thenReturn(mock_conn)
    when(mock_conn).bzpopmin(TEST_ACTION, 0).thenReturn(result_tuple)

    service = ActionEventQueue(TEST_URL, 5, "password", "some-pod")
    assert service.take(TEST_ACTION) == TEST_ITEM
