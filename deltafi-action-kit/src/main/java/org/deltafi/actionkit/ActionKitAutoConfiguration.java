/**
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
package org.deltafi.actionkit;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.service.ActionRunner;
import org.deltafi.actionkit.action.service.HeartbeatService;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.action.ActionEventQueueProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URISyntaxException;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties({ActionEventQueueProperties.class, ActionsProperties.class})
@EnableScheduling
public class ActionKitAutoConfiguration {
    @Bean
    public ActionEventQueue actionEventQueue(ActionEventQueueProperties actionEventQueueProperties,
                                             List<Action<?>> actions) throws URISyntaxException {
        // 1 thread for every action plus a thread for heartbeats
        return new ActionEventQueue(actionEventQueueProperties, actions.size() + 1);
    }

    @Bean
    public PluginRegistrar pluginRegistrar() {
        return new PluginRegistrar();
    }

    @Bean
    public ActionRunner actionRunner() {
        return new ActionRunner();
    }

    @Bean
    public HostnameService hostnameService(ActionsProperties actionsProperties) {
        return new HostnameService(actionsProperties);
    }

    @Bean
    public HeartbeatService heartbeatService() {
        return new HeartbeatService();
    }
}
