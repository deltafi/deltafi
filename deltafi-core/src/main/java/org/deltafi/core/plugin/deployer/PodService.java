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
package org.deltafi.core.plugin.deployer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

import static org.deltafi.core.plugin.deployer.K8sDeployerService.APP_LABEL_KEY;

/**
 * Service to retrieve k8s events and logs for a given pod
 */
@RequiredArgsConstructor
public class PodService {
    public static final String GROUP = "group";
    public static final String DELTAFI_PLUGINS_GROUP = "deltafi-plugins";
    private final KubernetesClient k8sClient;

    Optional<Pod> findNotReadyPluginPod(InstallDetails installDetails) {
        return k8sClient.pods()
                .withLabel(APP_LABEL_KEY, installDetails.appName())
                .withLabel(GROUP, DELTAFI_PLUGINS_GROUP)
                .resources()
                .filter(podResource -> !podResource.isReady())
                .findFirst().map(Resource::get);
    }

    public String podLogs(Pod pod, String pluginContainer) {
        return canCheckLogs(pod.getStatus(), pluginContainer) ? k8sClient.pods().resource(pod).getLog(true) : null;
    }

    public List<Event> podEvents(Pod pod) {
        List<Event> events = k8sClient.v1().events().withInvolvedObject(podObjectRef(pod)).list().getItems();
        return K8sEventUtil.sortEvents(events);
    }

    private boolean canCheckLogs(PodStatus podStatus, String containerName) {
        if (podStatus == null || podStatus.getContainerStatuses() == null || podStatus.getContainerStatuses().isEmpty()) {
            return false;
        }

        ContainerStatus containerStatus = podStatus.getContainerStatuses().stream().filter(cs -> cs.getName().equals(containerName)).findFirst().orElse(null);

        return containerStatus != null && isInCrashLoop(containerStatus);
    }

    private boolean isInCrashLoop(ContainerStatus containerStatus) {
        return "CrashLoopBackOff".equals(getWaitingReason(containerStatus));
    }

    private String getWaitingReason(ContainerStatus containerStatus) {
        return containerStatus != null && containerStatus.getState() != null && containerStatus.getState().getWaiting() != null ?
                containerStatus.getState().getWaiting().getReason() : "UNKNOWN";
    }

    private ObjectReference podObjectRef(Pod pod) {
        ObjectMeta meta = pod.getMetadata();

        return new ObjectReferenceBuilder()
                .withName(meta.getName())
                .withNamespace(meta.getNamespace())
                .withKind(pod.getKind())
                .withUid(meta.getUid()).build();
    }
}
