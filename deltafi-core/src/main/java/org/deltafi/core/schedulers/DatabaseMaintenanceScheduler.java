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
package org.deltafi.core.schedulers;

import lombok.AllArgsConstructor;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@AllArgsConstructor
public class DatabaseMaintenanceScheduler {
    private JdbcTemplate jdbcTemplate;
    private DeltaFiPropertiesService deltaFiPropertiesService;

    @Scheduled(fixedDelay = 300_000)
    public void enableSqueeze() {
        // there's a race condition starting squeeze up in the zalando operator
        // so it must be started manually at least once every time postgres restarts
        // calling it multiple times is safe, as new workers are ignored if one is already running
        if (deltaFiPropertiesService.getDeltaFiProperties().isAutoCleanPostgres()) {
            jdbcTemplate.execute("SELECT squeeze.start_worker()");
        }
    }
}
