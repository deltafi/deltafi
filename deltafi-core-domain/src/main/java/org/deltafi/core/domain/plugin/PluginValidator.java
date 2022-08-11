/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.plugin;

import lombok.RequiredArgsConstructor;
import org.deltafi.common.maven.VersionMatcher;
import org.deltafi.common.types.PluginCoordinates;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PluginValidator {
    private final PluginRepository pluginRepository;

    public List<String> validate(Plugin plugin) {
        List<String> validationErrors = new ArrayList<>();

        if (plugin.getDependencies() != null) {
            validationErrors.addAll(validateDependencies(plugin.getDependencies()));
        }

        return validationErrors;
    }

    private List<String> validateDependencies(List<PluginCoordinates> dependencies) {
        List<String> dependencyErrors = new ArrayList<>();

        List<PluginCoordinates> registeredPluginCoordinates = pluginRepository.findAll().stream()
                .map(Plugin::getPluginCoordinates)
                .collect(Collectors.toList());

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
}
