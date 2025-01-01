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
package org.deltafi.core.services;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.UUID;

@Service
public class DidMutexService {
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(UUID did) {
        return locks.computeIfAbsent(did, k -> new ReentrantLock());
    }

    public void executeWithLock(UUID did, Runnable task) {
        ReentrantLock lock = getLock(did);
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
            cleanupLock(did);
        }
    }

    private void cleanupLock(UUID did) {
        ReentrantLock lock = locks.get(did);
        if (lock != null && !lock.isLocked() && !lock.hasQueuedThreads()) {
            locks.remove(did);
        }
    }
}