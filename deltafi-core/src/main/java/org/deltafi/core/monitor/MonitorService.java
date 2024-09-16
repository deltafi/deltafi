/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.monitor.checks.CheckResult;
import org.deltafi.core.monitor.checks.StatusCheck;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue.SSE_VALKEY_CHANNEL_PREFIX;

@Slf4j
@MonitorProfile
public class MonitorService {
    private static final String SSE_STATUS_CHANNEL = SSE_VALKEY_CHANNEL_PREFIX + ".status";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final List<StatusCheck> checks;
    private final ValkeyKeyedBlockingQueue valkeyQueue;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Map<String, CheckResult> statuses = new ConcurrentHashMap<>();

    public MonitorService(List<StatusCheck> checks, ValkeyKeyedBlockingQueue valkeyQueue) {
        this.checks = checks;
        this.valkeyQueue = valkeyQueue;
        this.scheduledExecutorService = Executors.newScheduledThreadPool(checks.size());
        scheduleChecks();
    }

    @Scheduled(fixedDelay = 5_000)
    public void publishStatus() {
        MonitorResult monitorResult = MonitorResult.statuses(new ArrayList<>(statuses.values()));
        try {
            String json = OBJECT_MAPPER.writeValueAsString(monitorResult);
            valkeyQueue.set(SSE_STATUS_CHANNEL, json);
            valkeyQueue.set(ValkeyKeyedBlockingQueue.MONITOR_STATUS_HASH, json);
        } catch (Exception e) {
            log.error("Failed to publish status", e);
        }
    }

    public void scheduleChecks() {
        for (StatusCheck check : checks) {
            scheduledExecutorService.scheduleWithFixedDelay(() -> updateStatus(check), 0, 5, TimeUnit.SECONDS);
        }
    }

    void updateStatus(StatusCheck statusCheck) {
        CheckResult result = statusCheck.runCheck();
        statuses.put(statusCheck.getClass().getSimpleName(), result);
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutorService.shutdown();
    }
}