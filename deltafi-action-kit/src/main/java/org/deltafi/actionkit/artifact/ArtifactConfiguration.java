/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.artifact;

import io.valkey.JedisPool;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.URISyntaxException;

/**
 * Internal Spring configuration for the action-kit artifact infrastructure.
 * Imported by {@link org.deltafi.actionkit.ActionKitAutoConfiguration} — not a
 * standalone auto-configuration.
 * Not part of the public plugin API — inject {@link ArtifactRegistryService} or use
 * {@link PluginArtifact} field injection instead.
 */
@Configuration
@EnableConfigurationProperties(ArtifactRegistryCacheProperties.class)
public class ArtifactConfiguration {

    private static String pluginOwner(BuildProperties bp) {
        return bp.getGroup() + "." + bp.getArtifact();
    }

    @Bean
    ArtifactRegistryClient artifactRegistryClientImpl(
            Environment env, HttpService httpService, BuildProperties bp) {
        String coreUrl = env.getProperty("CORE_URL", "http://deltafi-core:8080") + "/api/v2";
        return new ArtifactRegistryClient(coreUrl, pluginOwner(bp), httpService);
    }

    @Bean
    ArtifactCacheManager artifactCacheManager(
            ArtifactRegistryClient client, ArtifactRegistryCacheProperties cacheProperties) {
        ArtifactCacheManager manager = new ArtifactCacheManager(client, cacheProperties);
        manager.startPoller();
        return manager;
    }

    @Bean
    PluginArtifactFactory pluginArtifactFactory(
            ArtifactCacheManager cacheManager, BuildProperties bp) {
        return new PluginArtifactFactory(cacheManager, pluginOwner(bp));
    }

    @Bean
    @ConditionalOnMissingBean
    public ArtifactRegistryService artifactRegistry(ArtifactRegistryClient client) {
        return new ArtifactRegistryService(client);
    }

    @Bean
    SmartInitializingSingleton artifactStartupCallbackFirer(ArtifactCacheManager cacheManager) {
        return cacheManager::fireStartupCallbacks;
    }

    @Bean
    ArtifactVersionChangeListener artifactVersionChangeListener(
            EventQueueProperties eventQueueProperties,
            ArtifactCacheManager cacheManager,
            ApplicationEventPublisher eventPublisher) throws URISyntaxException {
        JedisPool subscriberPool = ValkeyKeyedBlockingQueue.createJedisPool(eventQueueProperties.getUrl(), eventQueueProperties.getPassword(), 1, 1);
        return new ArtifactVersionChangeListener(subscriberPool, cacheManager, eventPublisher);
    }

    @Bean
    public static BeanFactoryPostProcessor pluginArtifactResolverConfigurer() {
        return beanFactory -> {
            if (beanFactory instanceof DefaultListableBeanFactory dlbf) {
                var existing = dlbf.getAutowireCandidateResolver();
                dlbf.setAutowireCandidateResolver(new PluginArtifactAutowireCandidateResolver(dlbf, existing));
            }
        };
    }
}
