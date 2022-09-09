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
package org.deltafi.core.config.server.loader;

import org.deltafi.core.config.server.constants.PropertyConstants;
import org.deltafi.core.config.server.environment.DeltaFiCompositeEnvironmentRepository;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeltaFiConfigDataLoader implements ConfigDataLoader<DeltaFiConfigDataResource> {

    @Override
    public boolean isLoadable(ConfigDataLoaderContext context, DeltaFiConfigDataResource resource) {
        return ConfigDataLoader.super.isLoadable(context, resource);
    }

    @Override
    public ConfigData load(ConfigDataLoaderContext context, DeltaFiConfigDataResource resource) throws IOException, ConfigDataResourceNotFoundException {
        DeltaFiCompositeEnvironmentRepository deltaFiCompositeEnvironmentRepository = context.getBootstrapContext().get(DeltaFiCompositeEnvironmentRepository.class);
        Environment environment = deltaFiCompositeEnvironmentRepository.findOne(PropertyConstants.CORE_APP_NAME, PropertyConstants.PROFILE, PropertyConstants.DEFAULT_LABEL);
        List<PropertySource<?>> propertySources = new ArrayList<>();
        for (org.springframework.cloud.config.environment.PropertySource propertySource : environment.getPropertySources()) {
            propertySources.add(0, asMapPropertySource(propertySource));
        }
        return new ConfigData(propertySources);
    }

    MapPropertySource asMapPropertySource(org.springframework.cloud.config.environment.PropertySource propertySource) {
        return new MapPropertySource(propertySource.getName(), (Map<String, Object>) propertySource.getSource());
    }

}
