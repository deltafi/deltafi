/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Rolls flow_summary_deltas rows into flow_summary_counts on a 1-second cadence.
 *
 * <p>Triggers on delta_file_flows append to flow_summary_deltas (append-only, no ON CONFLICT)
 * so they don't hold row locks on flow_summary_counts for the duration of the outer transaction.
 * This worker drains the delta log in short, self-contained transactions — summary-row locks
 * are only held briefly, and bulk operations upstream (auto-resume, mass acknowledge, ingest
 * bursts) no longer form lock convoys against incoming ingest.
 *
 * <p>Safe to run on multiple core replicas: the underlying SQL uses FOR UPDATE SKIP LOCKED so
 * concurrent workers partition the work without blocking each other. Duplicated effort is
 * harmless (and tiny).
 */
@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowSummaryDeltasConsumer {
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelayString = "${deltafi.flowSummaryDeltasIntervalMs:1000}")
    public void consume() {
        try {
            Integer processed = jdbcTemplate.queryForObject("SELECT consume_flow_summary_deltas()", Integer.class);
            if (processed != null && processed > 0) {
                log.debug("Rolled up {} flow summary count group(s)", processed);
            }
        } catch (Exception e) {
            log.warn("Failed to consume flow summary deltas: {}", e.getMessage());
        }
    }
}
