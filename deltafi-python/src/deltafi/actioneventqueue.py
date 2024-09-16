#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

from datetime import datetime, timezone
from urllib.parse import urlparse

import json
import redis
import time

HEARTBEAT_HASH = "org.deltafi.action-queue.heartbeat"
LONG_RUNNING_TASKS_HASH = "org.deltafi.action-queue.long-running-tasks"


class ActionEventQueue:
    def __init__(self, url, max_connections, password):
        parsed = urlparse(url)
        self.pool = redis.ConnectionPool(
            max_connections=max_connections,
            host=parsed.hostname,
            port=parsed.port,
            password=password)
        self.connection = None

    def get_connection(self):
        if self.connection is None:
            self.connection = redis.Redis(connection_pool=self.pool)
        return self.connection

    def size(self, name) -> int:
        conn = self.get_connection()
        count = conn.zcard(name)
        return count

    def put(self, name, item):
        now = round(time.time() * 1000)
        conn = self.get_connection()
        added = conn.zadd(name, {item: now}, nx=True)
        return added

    def take(self, name: str) -> str:
        conn = self.get_connection()
        setkey, item, score = conn.bzpopmin(name, 0)
        return item

    def heartbeat(self, name: str):
        conn = self.get_connection()
        utcnow = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        conn.hset(HEARTBEAT_HASH, name, utcnow)

    def record_long_running_task(self, action_execution):
        try:
            key = action_execution.key
            start_time = action_execution.start_time.isoformat().replace("+00:00", "Z")
            heartbeat_time = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            value = json.dumps([start_time, heartbeat_time])
            conn = self.get_connection()
            conn.hset(LONG_RUNNING_TASKS_HASH, key, value)
        except Exception as e:
            print(f"Unable to convert long running task information to JSON: {str(e)}")

    def remove_long_running_task(self, action_execution):
        key = action_execution.key
        conn = self.get_connection()
        conn.hdel(LONG_RUNNING_TASKS_HASH, key)
