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
package org.deltafi.config.server.environment.factory;

import org.deltafi.config.server.environment.DeltaFiNativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.core.env.ConfigurableEnvironment;

public class DeltaFiNativeEnvironmentRepositoryFactory implements EnvironmentRepositoryFactory<DeltaFiNativeEnvironmentRepository, NativeEnvironmentProperties> {

    private final ConfigurableEnvironment environment;
    private final String label;

    public DeltaFiNativeEnvironmentRepositoryFactory(ConfigurableEnvironment environment, String label) {
        this.environment = environment;
        this.label = label;
    }

    @Override
    public DeltaFiNativeEnvironmentRepository build(NativeEnvironmentProperties environmentProperties) {
        DeltaFiNativeEnvironmentRepository repository = new DeltaFiNativeEnvironmentRepository(this.environment,
                environmentProperties, label);
        repository.setDefaultLabel(label);
        return repository;
    }

}
