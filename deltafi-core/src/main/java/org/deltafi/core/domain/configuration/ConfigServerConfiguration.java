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
package org.deltafi.core.domain.configuration;

import org.deltafi.core.config.server.loader.DeltaFiConfigDataLocationResolver;
import org.deltafi.core.config.server.service.PropertyService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.config.server.config.ConfigServerMvcConfiguration;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Set up the beans necessary for refreshing properties.
 * Get the PropertyService bean and set the ContextRefresher
 */
@Configuration
@Import({RefreshAutoConfiguration.class, ConfigServerMvcConfiguration.class})
public class ConfigServerConfiguration implements BeanFactoryAware {

    private static final String PROPERTY_SERVICE_NAME = DeltaFiConfigDataLocationResolver.CONFIG_BEAN_NAME_PREFIX + PropertyService.class.getSimpleName();

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {

        /*
         * The PropertyService singleton is propagated up from the BootstrapContext prior
         * to the ContextRefresher bean creation. Find the PropertyService bean here
         * and set the ContextRefresher.
         */
        if (beanFactory.containsBean(PROPERTY_SERVICE_NAME)) {
            ContextRefresher contextRefresher = beanFactory.getBean(ContextRefresher.class);
            beanFactory.getBean(PropertyService.class).setContextRefresher(contextRefresher);
        }
    }
}
