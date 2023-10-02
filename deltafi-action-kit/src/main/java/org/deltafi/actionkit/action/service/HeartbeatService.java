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
package org.deltafi.actionkit.action.service;

import org.deltafi.actionkit.action.Action;
import org.deltafi.common.action.ActionEventQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collections;
import java.util.List;

public class HeartbeatService {
    @Autowired
    private ActionEventQueue actionEventQueue;

    @Autowired(required = false)
    private List<Action<?, ?, ?>> actions = Collections.emptyList();

    @Scheduled(fixedRate = 10000)
    void setHeartbeat() {
        for (Action<?, ?, ?> action : actions) {
            actionEventQueue.setHeartbeat(action.getClassCanonicalName());
        }
    }
}
