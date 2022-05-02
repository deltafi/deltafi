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
package org.deltafi.config.server.configuration;

import org.deltafi.config.server.environment.DeltaFiCompositeEnvironmentRepository;
import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class CompositeEnvironmentConfiguration {

    @Bean
    @Primary
    public DeltaFiCompositeEnvironmentRepository searchPathCompositeEnvironmentRepository(List<EnvironmentRepository> environmentRepos, ConfigServerProperties properties, StateHolderService stateHolderService) {
        return new DeltaFiCompositeEnvironmentRepository(environmentRepos, properties.isFailOnCompositeError(), stateHolderService);
    }
}
