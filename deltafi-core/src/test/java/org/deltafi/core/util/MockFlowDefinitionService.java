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
package org.deltafi.core.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.services.FlowDefinitionService;
import org.deltafi.core.types.FlowDefinition;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MockFlowDefinitionService implements FlowDefinitionService {
    private final LoadingCache<FlowKey, FlowDefinition> flowCache;

    public MockFlowDefinitionService() {
        this.flowCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    @NotNull
                    @Override
                    public FlowDefinition load(@NotNull FlowKey key) {
                        return FlowDefinition.builder()
                                .name(key.name)
                                .type(key.type)
                                .build();
                    }
                });
    }

    @Override
    public FlowDefinition getOrCreateFlow(String name, FlowType type) {
        try {
            return flowCache.get(new FlowKey(name, type));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get/create flow definition", e);
        }
    }

    @Override
    public void clearCache() {
        flowCache.invalidateAll();
    }

    private record FlowKey(String name, FlowType type) {}
}
