/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Plugin;
import org.deltafi.core.plugin.PluginCleaner;
import org.deltafi.core.repo.ActionDescriptorRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActionDescriptorService implements PluginCleaner {
    private final ActionDescriptorRepo actionDescriptorRepo;

    public List<ActionDescriptor> getAll() {
        return actionDescriptorRepo.findAll();
    }

    public void registerActions(List<ActionDescriptor> actionDescriptors) {
        actionDescriptorRepo.saveAll(actionDescriptors);
    }

    public boolean verifyActionsExist(List<String> actionNames) {
        return actionNames.size() == actionDescriptorRepo.countAllByNameIn(actionNames);
    }

    public Optional<ActionDescriptor> getByActionClass(String id) {
        return actionDescriptorRepo.findById(id);
    }

    @Override
    public void cleanupFor(Plugin plugin) {
        actionDescriptorRepo.deleteAllById(plugin.actionNames());
    }
}
