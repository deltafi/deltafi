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
package org.deltafi.core.configuration;

import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.action.ActionEventQueueProperties;
import org.deltafi.common.uuid.RandomUUIDGenerator;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.TaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
@EnableConfigurationProperties({ActionEventQueueProperties.class})
public class DeltaFiConfiguration {
    @Bean
    public ActionEventQueue actionEventQueue(ActionEventQueueProperties actionEventQueueProperties,
                                             DeltaFiPropertiesService deltaFiPropertiesService) throws URISyntaxException {
        // add two additional threads to the pool for the incoming action event threads
        int poolSize = deltaFiPropertiesService.getDeltaFiProperties().getCoreServiceThreads() + 2;
        return new ActionEventQueue(actionEventQueueProperties, poolSize);
    }

    @Bean
    public TaskSchedulerCustomizer taskSchedulerCustomizer(DeltaFiPropertiesService deltaFiPropertiesService) {
        return taskScheduler ->  taskScheduler.setPoolSize(deltaFiPropertiesService.getDeltaFiProperties().getScheduledServiceThreads());
    }

    @Bean
    public UUIDGenerator uuidGenerator() {
        return new RandomUUIDGenerator();
    }
}
