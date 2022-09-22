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
package org.deltafi.actionkit;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.service.ActionRunner;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.actionkit.service.RegistrationService;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.action.ActionEventQueueProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

@AutoConfiguration
@ConfigurationPropertiesScan(basePackages ={"org.deltafi.common.action", "org.deltafi.actionkit.properties"})
public class ActionKitAutoConfiguration {
    @Autowired(required = false)
    private final List<Action<?>> actions = Collections.emptyList();

    @Bean
    public ActionEventQueue actionEventQueue(ActionEventQueueProperties actionEventQueueProperties)
            throws URISyntaxException {
        return new ActionEventQueue(actionEventQueueProperties, actions.size());
    }

    @Bean
    public ActionRunner actionRunner() {
        return new ActionRunner();
    }

    @Bean
    public RegistrationService registrationService() {
        return new RegistrationService();
    }

    @Bean
    public HostnameService hostnameService(ActionsProperties actionsProperties) {
        return new HostnameService(actionsProperties);
    }
}
