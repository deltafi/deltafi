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
package org.deltafi.core.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.transaction.Transactional;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.repo.FlowDefinitionRepo;
import org.deltafi.core.types.FlowDefinition;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class FlowDefinitionServiceImpl implements FlowDefinitionService {
    private final LoadingCache<FlowKey, FlowDefinition> flowCache;
    private final FlowDefinitionRepo flowDefinitionRepo;

    public FlowDefinitionServiceImpl(FlowDefinitionRepo flowDefinitionRepo) {
        this.flowDefinitionRepo = flowDefinitionRepo;
        this.flowCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    @NotNull
                    @Override
                    public FlowDefinition load(@NotNull FlowKey key) {
                        return getOrCreateFlowDefinition(key);
                    }
                });
    }

    private FlowDefinition getOrCreateFlowDefinition(FlowKey key) {
        return flowDefinitionRepo.findByNameAndType(key.name(), key.type())
                .orElseGet(() -> {
                    try {
                        return flowDefinitionRepo.save(
                                FlowDefinition.builder()
                                        .name(key.name())
                                        .type(key.type())
                                        .build()
                        );
                    } catch (DataIntegrityViolationException e) {
                        // Another instance created the flow definition first
                        // Try to fetch it one more time
                        return flowDefinitionRepo.findByNameAndType(key.name(), key.type())
                                .orElseThrow(() -> new IllegalStateException(
                                        "Flow definition for %s %s not found after concurrent creation attempt".formatted(key.type().name(), key.name()), e));
                    }
                });
    }

    @Override
    public FlowDefinition getOrCreateFlow(String name, FlowType type) {
        try {
            return flowCache.get(new FlowKey(name, type));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get/create flow definition for %s %s".formatted(name, type.name()), e);
        }
    }

    @Override
    public void clearCache() {
        flowCache.invalidateAll();
    }

    private record FlowKey(String name, FlowType type) {}
}
