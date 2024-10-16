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
package org.deltafi.core.schedulers;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.services.ErrorCountService;
import org.deltafi.core.services.RestDataSourceService;
import org.deltafi.core.services.TimedDataSourceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@ConditionalOnProperty(value = "schedule.errorCount", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class ErrorCountScheduler {

    private final ErrorCountService errorCountService;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final TaskScheduler taskScheduler;

    private static final long INITIAL_DELAY = 5L;
    private static final long PERIOD = 5L;

    @PostConstruct
    public void schedule() {
        taskScheduler.scheduleAtFixedRate(this::populateErrorCounts, Instant.now().plusSeconds(INITIAL_DELAY), Duration.ofSeconds(PERIOD));
    }

    public void populateErrorCounts() {
        Set<String> dataSourceNames = new HashSet<>(restDataSourceService.maxErrorsPerFlow().keySet());
        dataSourceNames.addAll(timedDataSourceService.maxErrorsPerFlow().keySet());
        errorCountService.populateErrorCounts(dataSourceNames);
    }
}
