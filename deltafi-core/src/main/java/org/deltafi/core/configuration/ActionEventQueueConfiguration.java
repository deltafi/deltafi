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
package org.deltafi.core.configuration;

import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.action.ActionEventQueueProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
@ConfigurationPropertiesScan(basePackages = {"org.deltafi.common.action", "org.deltafi.core.configuration"})
public class ActionEventQueueConfiguration {
    @Bean
    public ActionEventQueue actionEventQueue(ActionEventQueueProperties actionEventQueueProperties,
                                             DeltaFiProperties deltaFiProperties)
            throws URISyntaxException {
        return new ActionEventQueue(actionEventQueueProperties, deltaFiProperties.getCoreServiceThreads());
    }
}
