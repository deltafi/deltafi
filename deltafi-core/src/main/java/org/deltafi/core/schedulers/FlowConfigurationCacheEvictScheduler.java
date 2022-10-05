/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.core.services.FlowService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@ConditionalOnProperty(value = "schedule.propertySync", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class FlowConfigurationCacheEvictScheduler {

    List<FlowService> flowServices;

    public FlowConfigurationCacheEvictScheduler(List<FlowService> flowServices) {
        this.flowServices = flowServices;
    }

    @Scheduled(fixedDelay = 5000)
    public void cacheEvict() {
        flowServices.forEach(FlowService::refreshCache);
    }
}