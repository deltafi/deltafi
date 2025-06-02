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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.AppInfo;
import org.deltafi.core.types.AppName;
import org.deltafi.common.types.Image;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Profile("!kubernetes")
public class DockerPlatformService implements PlatformService {

    public static final String DELTAFI_GROUP = "deltafi-group";
    private final DockerClient dockerClient;
    @Value("${HOSTNAME:UNKNOWN}")
    private String hostname;

    public DockerPlatformService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public Map<String, List<AppName>> appsByNode() {
        List<AppName> apps = getRunningContainers().stream()
                .map(container -> new AppName(containerNameOrId(container)))
                .toList();
        return Map.of(hostname, apps);
    }

    public List<AppInfo> getRunningVersions() {
        return getRunningContainers().stream()
                .map(this::containerToAppInfo)
                .toList();
    }

    @Override
    public List<String> contentNodeNames() {
        return List.of(hostname);
    }

    @Override
    public List<String> metadataNodeNames() {
        return List.of(hostname);
    }

    private List<Container> getRunningContainers() {
        return dockerClient.listContainersCmd()
                .withStatusFilter(List.of("running"))
                .withLabelFilter(List.of(DELTAFI_GROUP))
                .exec();
    }

    private AppInfo containerToAppInfo(Container container) {
        String nameOrId = containerNameOrId(container);
        return new AppInfo(nameOrId, nameOrId, getImage(container), containerGroupLabel(container));
    }

    private String containerNameOrId(Container container) {
        String[] names = container.getNames();
        if (names.length > 0) {
            String name = String.join(", ", names);
            return name.startsWith("/") ? name.substring(1) : name;
        }

        return container.getId();
    }

    private String containerGroupLabel(Container container) {
        return container.getLabels().getOrDefault(DELTAFI_GROUP, "other");
    }

    private Image getImage(Container container) {
        return Image.image(container.getImage());
    }

}
