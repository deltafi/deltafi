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
package org.deltafi.core.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.monitor.checks.CheckResult;
import org.deltafi.core.monitor.checks.StatusCheck;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue.SSE_VALKEY_CHANNEL_PREFIX;

@MonitorProfile
@Slf4j
public class MonitorService {
    private static final String SSE_STATUS_CHANNEL = SSE_VALKEY_CHANNEL_PREFIX + ".status";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ValkeyKeyedBlockingQueue valkeyQueue;
    private final StatusCheckRepo statusCheckRepo;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Map<String, StatusCheck> statusCheckMap = new ConcurrentHashMap<>();
    private final Map<String, CheckResult> statuses = new ConcurrentHashMap<>();

    private Set<String> pausedChecks = new HashSet<>();

    public MonitorService(List<StatusCheck> checks, ValkeyKeyedBlockingQueue valkeyQueue,
            StatusCheckRepo statusCheckRepo) {
        this.valkeyQueue = valkeyQueue;
        this.statusCheckRepo = statusCheckRepo;
        this.scheduledExecutorService = Executors.newScheduledThreadPool(checks.size());

        for (StatusCheck check : checks) {
            statusCheckMap.put(check.getId(), check);
            scheduledExecutorService.scheduleWithFixedDelay(() -> updateStatus(check), 0, 5, TimeUnit.SECONDS);
        }
    }

    private void updateStatus(StatusCheck statusCheck) {
        if (pausedChecks.contains(statusCheck.getId())) {
            return;
        }
        statuses.put(statusCheck.getId(), statusCheck.runCheck());
    }

    @Scheduled(fixedDelay = 5_000)
    public void publishStatus() {
        refreshPausedChecks();

        MonitorResult monitorResult = MonitorResult.statuses(new ArrayList<>(statuses.values()));
        try {
            String json = OBJECT_MAPPER.writeValueAsString(monitorResult);
            valkeyQueue.set(SSE_STATUS_CHANNEL, json);
            valkeyQueue.set(ValkeyKeyedBlockingQueue.MONITOR_STATUS_HASH, json);
        } catch (Exception e) {
            log.error("Failed to publish status", e);
        }
    }

    private void refreshPausedChecks() {
        Set<String> pausedChecks = new HashSet<>();

        Map<String, OffsetDateTime> pausedStatusChecks = statusCheckRepo.findAll().stream()
                .collect(Collectors.toMap(StatusCheckEntity::getId, StatusCheckEntity::getNextRunTime));
        for (Map.Entry<String, OffsetDateTime> entry : pausedStatusChecks.entrySet()) {
            CheckResult status = statuses.get(entry.getKey());
            if (entry.getValue().isBefore(OffsetDateTime.now())) {
                statusCheckRepo.deleteById(entry.getKey());
                statuses.put(entry.getKey(), statusCheckMap.get(entry.getKey()).runCheck());
            } else {
                pausedChecks.add(entry.getKey());
                statuses.put(entry.getKey(), new CheckResult(status.statusCheckId(), status.description(),
                        CheckResult.CODE_PAUSED, "", status.timestamp(), entry.getValue()));
            }
        }

        this.pausedChecks = pausedChecks;
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutorService.shutdown();
    }
}