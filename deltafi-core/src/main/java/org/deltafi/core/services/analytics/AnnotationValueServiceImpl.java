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
package org.deltafi.core.services.analytics;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.deltafi.core.repo.AnnotationValueRepo;
import org.deltafi.core.types.AnnotationValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class AnnotationValueServiceImpl implements AnnotationValueService {

    private final AnnotationValueRepo repo;
    private final LoadingCache<String, AnnotationValue> cache;

    public AnnotationValueServiceImpl(AnnotationValueRepo repo) {
        this.repo = repo;
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    @NotNull
                    @Override
                    public AnnotationValue load(@NotNull String key) {
                        return findOrCreate(key);
                    }
                });
    }

    @Override
    public int getOrCreateValueId(String name) {
        try {
            AnnotationValue group = cache.get(name);
            return group.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get/create AnnotationValue for name=" + name, e);
        }
    }

    private AnnotationValue findOrCreate(String groupName) {
        return repo.findByValueText(groupName)
                .orElseGet(() -> {
                    try {
                        return repo.save(AnnotationValue.builder().valueText(groupName).build());
                    } catch (DataIntegrityViolationException ex) {
                        // handle race condition if two threads create simultaneously
                        return repo.findByValueText(groupName)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Concurrent creation for AnnotationValue " + groupName, ex));
                    }
                });
    }

    @Override
    public void clearCache() {
        cache.invalidateAll();
    }
}


