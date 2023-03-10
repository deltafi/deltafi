#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
from urllib.parse import urlparse

import redis


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
