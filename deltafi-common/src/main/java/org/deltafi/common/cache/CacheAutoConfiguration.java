/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass({ Caffeine.class, CaffeineCache.class })
@ConditionalOnMissingBean(CacheManager.class)
@ConditionalOnExpression("'${spring.cache.type:CAFFEINE}' eq 'CAFFEINE'")
@EnableConfigurationProperties(CacheProperties.class)
@EnableCaching
public class CacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public Ticker ticker() {
        return System::nanoTime;
    }

    @Bean
    public CacheManager cacheManager(CacheProperties cacheProperties, Ticker ticker) {
        SimpleCacheManager simpleCacheManager = new SimpleCacheManager();
        List<CaffeineCache> caches = cacheProperties.getCaches().entrySet().stream()
                .map(cacheEntry -> new CaffeineCache(cacheEntry.getKey(),
                        Caffeine.from(cacheEntry.getValue()).ticker(ticker).build()))
                .toList();
        simpleCacheManager.setCaches(caches);
        return simpleCacheManager;
    }
}
