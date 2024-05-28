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

import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static org.deltafi.core.services.CoreEventQueue.DGS_QUEUE;

@AllArgsConstructor
@ConditionalOnProperty(value = "schedule.actionEvents", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Service
public class HeartbeatService {
    private IdentityService identityService;
    private CoreEventQueue coreEventQueue;

    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        coreEventQueue.setHeartbeat(DGS_QUEUE + "-" + identityService.getUniqueId());
        coreEventQueue.setHeartbeat(DGS_QUEUE);
    }
}
