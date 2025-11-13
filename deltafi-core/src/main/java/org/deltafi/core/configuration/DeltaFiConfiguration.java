/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import io.valkey.JedisPool;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.content.StorageProperties;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.rules.RuleEvaluator;
import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.uuid.RandomUUIDGenerator;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.repo.PendingDeleteRepo;
import org.deltafi.core.services.CoreEventQueue;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.LocalContentStorageService;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.util.ParameterResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;

import java.net.URISyntaxException;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties({EventQueueProperties.class, SslSecretNames.class, LocalStorageProperties.class})
public class DeltaFiConfiguration {

    @Bean
    public ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue(EventQueueProperties eventQueueProperties,
            DeltaFiPropertiesService deltaFiPropertiesService, @Value("${lookup.enabled:false}") boolean lookupEnabled)
            throws URISyntaxException {
        // add two additional threads to the pool for the incoming action event threads
        int poolSize = deltaFiPropertiesService.getDeltaFiProperties().getCoreServiceThreads() + 2;

        if (lookupEnabled) {
            poolSize++;
        }

        return new ValkeyKeyedBlockingQueue(eventQueueProperties, poolSize);
    }

    @Bean
    public JedisPool jedisPool(EventQueueProperties eventQueueProperties,
            DeltaFiPropertiesService deltaFiPropertiesService, @Value("${lookup.enabled:false}") boolean lookupEnabled)
            throws URISyntaxException {
        // add two additional threads to the pool for the incoming action event threads
        int poolSize = deltaFiPropertiesService.getDeltaFiProperties().getCoreServiceThreads() + 2;

        if (lookupEnabled) {
            poolSize++;
        }

        return ValkeyKeyedBlockingQueue.createJedisPool(eventQueueProperties, poolSize, poolSize);
    }

    @Bean
    public CoreEventQueue coreEventQueue(ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue, Clock clock) {
        return new CoreEventQueue(valkeyKeyedBlockingQueue, clock);
    }

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler(DeltaFiPropertiesService deltaFiPropertiesService) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(deltaFiPropertiesService.getDeltaFiProperties().getScheduledServiceThreads());
        return scheduler;
    }

    @Bean
    public UUIDGenerator uuidGenerator() {
        return new RandomUUIDGenerator();
    }

    @Bean
    public RuleEvaluator ruleEvaluator() {
        return new RuleEvaluator();
    }

    @Bean
    public RuleValidator ruleValidator(RuleEvaluator ruleEvaluator) {
        return new RuleValidator(ruleEvaluator);
    }

    @Bean
    public AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SpringAuthorizationEventPublisher(applicationEventPublisher);
    }

    @Bean
    public ParameterResolver parameterResolver() {
        return new ParameterResolver();
    }

    @Bean
    @ConditionalOnProperty(name = "local.storage.content", havingValue = "true", matchIfMissing = true)
    public LocalContentStorageService localContentStorageService(PendingDeleteRepo pendingDeleteRepo, SystemService systemService, StorageProperties storageProperties) {
        return new LocalContentStorageService(pendingDeleteRepo, systemService, storageProperties);
    }
}
