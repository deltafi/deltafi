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

import lombok.RequiredArgsConstructor;
import org.deltafi.core.services.DeltaFilesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@RefreshScope
@EnableScheduling
@RequiredArgsConstructor
public class RequeueScheduler implements ApplicationListener<RefreshScopeRefreshedEvent> {

    final DeltaFilesService deltaFilesService;

    // convert to milliseconds then divide each interval into 10 samples
    @Scheduled(fixedDelayString = "#{deltaFiProperties.getRequeueSeconds() * 1000 / 10}")
    public void requeue() {
        deltaFilesService.requeue();
    }

    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {

    }
}