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
package org.deltafi.core.validation;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.maven.VersionMatcher;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.types.PluginEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PluginValidator {
    private final PluginRepository pluginRepository;

    private final static int MAX_NAME_LEN = 256;

    public List<String> validate(PluginEntity plugin) {
        List<String> validationErrors = new ArrayList<>(validateCoordinates(plugin.getPluginCoordinates()));
        List<PluginEntity> existingPlugins = pluginRepository.findAll().stream().toList();
        validationErrors.addAll(validateDependencies(plugin.getDependencies(), existingPlugins));
        validationErrors.addAll(validateUniqueActions(plugin, existingPlugins));
        validationErrors.addAll(validateActionDescriptors(plugin.getActions()));
        return validationErrors;
    }

    private List<String> validateCoordinates(PluginCoordinates pluginCoordinates) {
        List<String> errors = new ArrayList<>();

        if (pluginCoordinates == null) {
            errors.add("The plugin coordinates must be provided");
            return errors;
        }

        if (StringUtils.isBlank(pluginCoordinates.getGroupId())) {
            errors.add("The plugin groupId cannot be null or empty");
        }

        if (StringUtils.isBlank(pluginCoordinates.getArtifactId())) {
            errors.add("The plugin artifactId cannot be null or empty");
        }

        if (StringUtils.isBlank(pluginCoordinates.getVersion())) {
            errors.add("The plugin version cannot be null or empty");
        }

        return errors;
    }

    private List<String> validateDependencies(List<PluginCoordinates> dependencies, List<PluginEntity> existingPlugins) {
        if (dependencies == null) {
            return List.of();
        }

        List<String> dependencyErrors = new ArrayList<>();

        List<PluginCoordinates> registeredPluginCoordinates = existingPlugins.stream()
                .map(PluginEntity::getPluginCoordinates)
                .toList();

        dependencies.forEach(dependency -> {
            Optional<PluginCoordinates> registeredMatchIgnoringVersion = registeredPluginCoordinates.stream()
                    .filter(registered -> registered.equalsIgnoreVersion(dependency))
                    .findFirst();

            if (registeredMatchIgnoringVersion.isEmpty()) {
                dependencyErrors.add(String.format("Plugin dependency not registered: %s.", dependency));
            } else if (!VersionMatcher.matches(registeredMatchIgnoringVersion.get().getVersion(), dependency.getVersion())) {
                dependencyErrors.add(String.format(
                        "Plugin dependency for %s:%s not satisfied. Required version %s but installed version is %s.",
                        dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                        registeredMatchIgnoringVersion.get().getVersion()));
            }
        });

        return dependencyErrors;
    }

    public List<String> validateUniqueActions(PluginEntity newPlugin, List<PluginEntity> existingPlugins) {
        if (newPlugin.getActions() == null || newPlugin.getActions().isEmpty()) {
            return List.of();
        }

        Map<String, String> actionMap = new HashMap<>();
        for (PluginEntity existingPlugin : existingPlugins) {
            if (existingPlugin.getActions() != null && !existingPlugin.getPluginCoordinates().equalsIgnoreVersion(newPlugin.getPluginCoordinates())) {
                for (ActionDescriptor existingAction : existingPlugin.getActions()) {
                    actionMap.put(existingAction.getName(), existingPlugin.getPluginCoordinates().toString());
                }
            }
        }

        List<String> errors = new ArrayList<>();
        for (ActionDescriptor action : newPlugin.getActions()) {
            if (actionMap.containsKey(action.getName())) {
                errors.add("Action '" + action.getName() + "' has registered in another plugin '" + actionMap.get(action.getName()) +"'");
            }
        }

        return errors;
    }

    private List<String> validateActionDescriptors(List<ActionDescriptor> actionDescriptors) {
        if (actionDescriptors == null) {
            return List.of();
        }

        return actionDescriptors.stream()
                .filter(ad -> ad.getName().length() > MAX_NAME_LEN)
                .map(ad -> String.format("Action name %s exceeds maximum length %d", ad.getName(), MAX_NAME_LEN))
                .toList();
    }
}
