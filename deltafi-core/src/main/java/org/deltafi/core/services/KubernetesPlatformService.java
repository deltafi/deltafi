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
package org.deltafi.core.services;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.AllArgsConstructor;
import org.deltafi.core.types.AppInfo;
import org.deltafi.core.types.AppName;
import org.deltafi.core.types.Image;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
@Profile("kubernetes")
@AllArgsConstructor
public class KubernetesPlatformService implements PlatformService {

    private final KubernetesClient k8sClient;

    @Override
    public Map<String, List<AppName>> appsByNode() {
        Map<String, List<AppName>> nodeInfo = new LinkedHashMap<>();
        List<Pod> runningPods = runningPods().getItems();
        for (Pod pod : runningPods) {
            List<AppName> apps = nodeInfo.computeIfAbsent(extractNodeName(pod), node -> new ArrayList<>());
            apps.add(new AppName(pod.getMetadata().getName()));
        }
        return nodeInfo;
    }

    @Override
    public List<AppInfo> getRunningVersions() {
        return runningPods().getItems().stream().flatMap(this::toAppInfo)
                .sorted(Comparator.comparing(AppInfo::app))
                .distinct().toList();
    }

    @Override
    public String contentNodeName() {
        return k8sClient.pods().withLabels(Map.of("app", "minio")).list()
                .getItems().stream().findFirst()
                .map(this::extractNodeName).orElse(null);
    }

    private PodList runningPods() {
        return k8sClient.pods().withField("status.phase", "Running").list();
    }

    private String extractNodeName(Pod pod) {
        return pod.getSpec().getNodeName();
    }

    private Stream<AppInfo> toAppInfo(Pod pod) {
        String appName = pod.getMetadata().getLabels().getOrDefault("app",
                pod.getMetadata().getLabels().getOrDefault("app.kubernetes.io/name",
                        pod.getMetadata().getAnnotations().getOrDefault("application",
                                pod.getMetadata().getName())));
        String group = pod.getMetadata().getLabels().get("group");
        return pod.getSpec().getContainers().stream()
                .map(container -> toAppInfo(container, appName, group));
    }

    private AppInfo toAppInfo(Container container, String appName, String group) {
        return AppInfo.builder()
                .app(appName)
                .container(container.getName())
                .image(Image.image(container.getImage()))
                .group(group)
                .build();
    }
}
