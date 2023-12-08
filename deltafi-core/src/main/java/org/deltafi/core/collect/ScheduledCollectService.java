/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.collect;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledCollectService {
    public interface CollectHandler {
        void collect(CollectEntry collectEntry, List<String> collectedDids);
    }

    public interface FailCollectHandler {
        void failCollect(CollectEntry collectEntry, List<String> collectedDids, String reason);
    }

    private final Clock clock;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final CollectEntryService collectEntryService;

    private CollectHandler collectHandler;
    private FailCollectHandler failCollectHandler;

    private final ScheduledExecutorService timedOutCollectExecutor = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> timedOutCollectFuture;

    @PreDestroy
    public void onShutdown() {
        if (timedOutCollectFuture != null) {
            timedOutCollectFuture.cancel(true);
        }
    }

    public void registerHandlers(CollectHandler collectHandler, FailCollectHandler failCollectHandler) {
        this.collectHandler = collectHandler;
        this.failCollectHandler = failCollectHandler;
    }

    public void scheduleNextCollectCheck() {
        List<CollectEntry> collectEntries = collectEntryService.findCollectEntriesByCollectDate();
        if (collectEntries.isEmpty()) {
            cancelCollectCheck();
            return;
        }

        scheduleCollectCheck(collectEntries.get(0).getCollectDate());
    }

    private void cancelCollectCheck() {
        if (timedOutCollectFuture != null) {
            timedOutCollectFuture.cancel(false);
        }
    }

    private void scheduleCollectCheck(OffsetDateTime collectDate) {
        cancelCollectCheck();
        log.debug("Scheduling next collect check in {} seconds",
                Math.max(collectDate.toEpochSecond() - OffsetDateTime.now(clock).toEpochSecond(), 1));
        timedOutCollectFuture = timedOutCollectExecutor.schedule(this::handleTimedOutCollects,
                Math.max(collectDate.toEpochSecond() - OffsetDateTime.now(clock).toEpochSecond(), 1), TimeUnit.SECONDS);
    }

    public void updateCollectCheck(OffsetDateTime collectDate) {
        if ((timedOutCollectFuture == null) || timedOutCollectFuture.isDone() || collectDate.isBefore(
                OffsetDateTime.now(clock).plusSeconds(timedOutCollectFuture.getDelay(TimeUnit.SECONDS)))) {
            scheduleCollectCheck(collectDate);
        }
    }

    private void handleTimedOutCollects() {
        log.debug("Handling timed out collects");

        CollectEntry collectEntry = collectEntryService.lockOneBefore(OffsetDateTime.now(clock));
        while (collectEntry != null) {
            if ((collectEntry.getMinNum() != null) && (collectEntry.getCount() < collectEntry.getMinNum())) {
                failCollectHandler.failCollect(collectEntry, collectEntryService.findCollectedDids(collectEntry.getId()),
                        String.format("Collect incomplete: Timed out after receiving %s of %s files",
                                collectEntry.getCount(), collectEntry.getMinNum()));
            } else {
                collectHandler.collect(collectEntry, collectEntryService.findCollectedDids(collectEntry.getId()));
            }
            collectEntryService.delete(collectEntry.getId());
            collectEntry = collectEntryService.lockOneBefore(OffsetDateTime.now(clock));
        }

        scheduleNextCollectCheck();
    }

    public void unlockTimedOutCollectEntryLocks() {
        long numUnlocked = collectEntryService.unlockBefore(OffsetDateTime.now(clock)
                .minus(deltaFiPropertiesService.getDeltaFiProperties().getCollect().getMaxLockDuration()));

        if (numUnlocked > 0) {
            log.warn("Unlocked {} timed out collect entries", numUnlocked);

            scheduleNextCollectCheck();
        }
    }
}
