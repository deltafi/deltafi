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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.repo.PendingDeleteRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class LocalContentStorageService {
    private final PendingDeleteRepo pendingDeleteRepo;
    private final SystemService systemService;

    private static final long POLL_INTERVAL_MS = 50;

    public void deleteContent(List<UUID> dids, boolean blockUntilDeleted) {
        pendingDeleteRepo.insertPendingDeletes(systemService.getContentNodeNames(), dids, ContentStorageService.CONTENT_BUCKET);

        if (!blockUntilDeleted) {
            return;
        }

        int lastCount = -1;
        boolean logged = false;

        // block and wait for deletes to complete
        // used by disk space delete to ensure we don't aggressively delete too many files while waiting for the
        // delete process to catch up with the pending list
        while (true) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for pending deletes to clear", e);
            }

            int total = pendingDeleteRepo.count();
            if (total == 0) {
                if (logged) {
                    log.info("All deletes processed.");
                }
                break;
            }

            if (total != lastCount) {
                log.info("Waiting for pending deletes to complete ({} remaining)...", total);
                lastCount = total;
                logged = true;
            }
        }
    }
}
