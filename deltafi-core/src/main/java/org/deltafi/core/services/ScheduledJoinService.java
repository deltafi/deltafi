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
package org.deltafi.core.services;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.JoinEntry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJoinService {
    public interface JoinHandler {
        void join(JoinEntry joinEntry, List<UUID> joinDids);
    }

    public interface FailJoinHandler {
        void failJoin(JoinEntry joinEntry, List<UUID> joinDids, String reason);
    }

    private final Clock clock;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final JoinEntryService joinEntryService;

    private JoinHandler joinHandler;
    private FailJoinHandler failJoinHandler;

    private final ScheduledExecutorService timedOutJoinExecutor = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> timedOutJoinFuture;

    @PreDestroy
    public void onShutdown() {
        if (timedOutJoinFuture != null) {
            timedOutJoinFuture.cancel(true);
        }
    }

    public void registerHandlers(JoinHandler joinHandler, FailJoinHandler failJoinHandler) {
        this.joinHandler = joinHandler;
        this.failJoinHandler = failJoinHandler;
    }

    public void scheduleNextJoinCheck() {
        List<JoinEntry> joinEntries = joinEntryService.findJoinEntriesByJoinDate();
        if (joinEntries.isEmpty()) {
            cancelJoinCheck();
            return;
        }

        scheduleJoinCheck(joinEntries.getFirst().getJoinDate());
    }

    private void cancelJoinCheck() {
        if (timedOutJoinFuture != null) {
            timedOutJoinFuture.cancel(false);
        }
    }

    private void scheduleJoinCheck(OffsetDateTime joinDate) {
        cancelJoinCheck();
        log.debug("Scheduling next join check in {} seconds",
                Math.max(joinDate.toEpochSecond() - OffsetDateTime.now(clock).toEpochSecond(), 1));
        timedOutJoinFuture = timedOutJoinExecutor.schedule(this::handleTimedOutJoins,
                Math.max(joinDate.toEpochSecond() - OffsetDateTime.now(clock).toEpochSecond(), 1), TimeUnit.SECONDS);
    }

    public void updateJoinCheck(OffsetDateTime joinDate) {
        if ((timedOutJoinFuture == null) || timedOutJoinFuture.isDone() || joinDate.isBefore(
                OffsetDateTime.now(clock).plusSeconds(timedOutJoinFuture.getDelay(TimeUnit.SECONDS)))) {
            scheduleJoinCheck(joinDate);
        }
    }

    private void handleTimedOutJoins() {
        log.debug("Handling timed out joins");

        Optional<JoinEntry> maybeJoinEntry = joinEntryService.lockOneBefore(OffsetDateTime.now(clock));
        while (maybeJoinEntry.isPresent()) {
            JoinEntry joinEntry = maybeJoinEntry.get();
            if ((joinEntry.getMinNum() != null) && (joinEntry.getCount() < joinEntry.getMinNum())) {
                failJoinHandler.failJoin(joinEntry, joinEntryService.findJoinedDids(joinEntry.getId()),
                        String.format("Join incomplete: Timed out after receiving %s of %s files",
                                joinEntry.getCount(), joinEntry.getMinNum()));
            } else {
                joinHandler.join(joinEntry, joinEntryService.findJoinedDids(joinEntry.getId()));
            }
            joinEntryService.delete(joinEntry.getId());
            maybeJoinEntry = joinEntryService.lockOneBefore(OffsetDateTime.now(clock));
        }

        scheduleNextJoinCheck();
    }

    public void unlockTimedOutJoinEntryLocks() {
        long numUnlocked = joinEntryService.unlockBefore(OffsetDateTime.now(clock)
                .minus(deltaFiPropertiesService.getDeltaFiProperties().getJoinMaxLockDuration()));

        if (numUnlocked > 0) {
            log.warn("Unlocked {} timed out join entries", numUnlocked);

            scheduleNextJoinCheck();
        }
    }
}
