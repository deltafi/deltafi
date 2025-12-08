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
package org.deltafi.core.monitor.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.EventService;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

import static org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue.SSE_VALKEY_CHANNEL_PREFIX;

@Slf4j
@MonitorProfile
@RequiredArgsConstructor
public class StatsService {

    private static final String DELTAFILE_STATS_CHANNEL = SSE_VALKEY_CHANNEL_PREFIX + ".deltafiStats";
    private static final String ERROR_COUNT_CHANNEL = SSE_VALKEY_CHANNEL_PREFIX + ".errorCount";
    private static final String NOTIFICATION_COUNT_CHANNEL = SSE_VALKEY_CHANNEL_PREFIX + ".notificationCount";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DeltaFilesService deltaFilesService;
    private final EventService eventService;
    private final ValkeyKeyedBlockingQueue valkeyQueue;
    private final MetricService metricService;

    @Scheduled(fixedDelay = 5000)
    public void deltaFileStats() {
        DeltaFileStats deltaFileStats = deltaFilesService.deltaFileStats();
        sendMetrics(deltaFileStats);
        try {
            valkeyQueue.set(DELTAFILE_STATS_CHANNEL, OBJECT_MAPPER.writeValueAsString(deltaFileStats));
        } catch (Exception e) {
            log.error("Failed to publish deltaFileStats to valkey", e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void errorCount() {
        publishData(ERROR_COUNT_CHANNEL, deltaFilesService.countUnacknowledgedErrors());
    }

    @Scheduled(fixedDelay = 5000)
    public void notificationCount() {
        publishData(NOTIFICATION_COUNT_CHANNEL, eventService.notificationCount());
    }

    private void sendMetrics(DeltaFileStats stats) {
        metricService.sendGauges(Map.of(
                "gauge.deltafile.totalCount", stats.getTotalCount(),
                "gauge.deltafile.inFlightCount", stats.getInFlightCount(),
                "gauge.deltafile.inFlightBytes", stats.getInFlightBytes(),
                "gauge.deltafile.coldQueuedCount", stats.getColdQueuedCount(),
                "gauge.deltafile.pausedCount", stats.getPausedCount(),
                "gauge.deltafile.warmQueuedCount", stats.getWarmQueuedCount()
        ));
    }

    protected void publishData(String key, long value) {
        valkeyQueue.set(key, "" + value);
    }
}
