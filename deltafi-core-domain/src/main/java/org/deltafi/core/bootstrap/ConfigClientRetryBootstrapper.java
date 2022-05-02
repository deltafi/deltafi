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
package org.deltafi.core.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.cloud.config.client.ConfigClientFailFastException;
import org.springframework.cloud.config.client.ConfigServerBootstrapper;
import org.springframework.cloud.config.client.ConfigServerConfigDataResource;

/**
 * Workaround for:
 * https://github.com/spring-cloud/spring-cloud-config/issues/1963
 *
 * This initializer changes the LoaderInterceptor to throw an
 * IllegalStateException instead of bubbling up the ConfigClientFailFastException
 * which is deferred and causes other issues (i.e. can't connect to mongo which takes 30 seconds to fail)
 */
@Slf4j
public class ConfigClientRetryBootstrapper implements BootstrapRegistryInitializer {

    @Override
    public void initialize(BootstrapRegistry registry) {
        registry.register(ConfigServerBootstrapper.LoaderInterceptor.class, context -> loadContext -> {
            ConfigServerConfigDataResource resource = loadContext.getResource();
            try {
                return loadContext.getInvocation().apply(loadContext.getLoaderContext(), resource);
            } catch (ConfigClientFailFastException e) {
                throw new IllegalStateException("Failed to retrieve configuration from the config-server");
            }
        });
    }
}
