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
import org.deltafi.core.repo.ActionNameRepo;
import org.deltafi.core.types.ActionName;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class ActionNameServiceImpl implements ActionNameService {

    private final ActionNameRepo repo;
    private final LoadingCache<String, ActionName> cache;

    public ActionNameServiceImpl(ActionNameRepo repo) {
        this.repo = repo;
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<>() {
                    @NotNull
                    @Override
                    public ActionName load(@NotNull String key) {
                        return findOrCreate(key);
                    }
                });
    }

    @Override
    public int getOrCreateActionName(String name) {
        try {
            ActionName group = cache.get(name);
            return group.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get/create ActionName for name=" + name, e);
        }
    }

    private ActionName findOrCreate(String groupName) {
        return repo.findByName(groupName)
                .orElseGet(() -> {
                    try {
                        return repo.save(ActionName.builder().name(groupName).build());
                    } catch (DataIntegrityViolationException ex) {
                        // handle race condition if two threads create simultaneously
                        return repo.findByName(groupName)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Concurrent creation for ActionName " + groupName, ex));
                    }
                });
    }

    @Override
    public void clearCache() {
        cache.invalidateAll();
    }
}


