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

import org.deltafi.actionkit.action.service.ActionRunner;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.actionkit.service.RegistrationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConfigurationPropertiesScan
public class ActionKitAutoConfiguration {
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
