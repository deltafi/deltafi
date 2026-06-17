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
package org.deltafi.common.cache;

/**
 * Declares a Caffeine cache that {@link CacheAutoConfiguration} will register with the
 * shared {@code CacheManager}. Components should define a {@code @Bean CacheSpec} next to
 * each {@code @Cacheable}/{@code @CacheEvict} usage so the cache lives with its consumer.
 *
 * @param name the cache name as referenced by {@code @Cacheable("name")}
 * @param spec a Caffeine spec string, e.g. {@code "expireAfterWrite=PT1H,maximumSize=100"}
 */
public record CacheSpec(String name, String spec) {
}
