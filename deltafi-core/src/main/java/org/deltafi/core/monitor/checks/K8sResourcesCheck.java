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
package org.deltafi.core.monitor.checks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.PodStatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.monitor.checks.CheckResult.ResultBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.deltafi.core.monitor.checks.CheckResult.CODE_RED;
import static org.deltafi.core.monitor.checks.CheckResult.CODE_YELLOW;

@Slf4j
@Service
@Profile("kubernetes & " + MonitorProfile.MONITOR)
public class K8sResourcesCheck extends StatusCheck {
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    public static final String DELTAFI_STATUS_CHECKS = "deltafi-status-checks";
    private final KubernetesClient k8sClient;

    public K8sResourcesCheck(KubernetesClient k8sClient) {
        super("Kubernetes Resource Check");
        this.k8sClient = k8sClient;
    }

    @Override
    public CheckResult check() {
        ResultBuilder resultBuilder = new ResultBuilder();
        checkPods(resultBuilder);
        checkForMissingResources(resultBuilder);
        return result(resultBuilder);
    }

    private void checkForMissingResources(ResultBuilder resultBuilder) {

        ConfigMap configMap = k8sClient.configMaps().withName(DELTAFI_STATUS_CHECKS).get();

        if (configMap == null) {
            resultBuilder.code(CODE_YELLOW);
            resultBuilder.addHeader("Missing the " + DELTAFI_STATUS_CHECKS + " ConfigMap");
            resultBuilder.addLine("Run the installer:");
            resultBuilder.addLine("\n\t$ deltafi install");
            return;
        }

        Map<String, String> checks = configMap.getData();

        boolean missing = missing(k8sClient.apps().deployments().list().getItems(), checks, "Deployment", resultBuilder);
        missing |= missing(k8sClient.network().v1().ingresses().list().getItems(), checks, "Ingress", resultBuilder);
        missing |= missing(k8sClient.services().list().getItems(), checks, "Service", resultBuilder);
        missing |= missing(k8sClient.apps().statefulSets().list().getItems(), checks, "StatefulSet", resultBuilder);
        missing |= missing(k8sClient.persistentVolumeClaims().list().getItems(), checks, "PersistentVolumeClaim", resultBuilder);

        if (missing) {
            resultBuilder.addHeader("Recommendation");
            resultBuilder.addLine("Run the installer:");
            resultBuilder.addLine("\n\t$ deltafi install");
        }
    }

    private boolean missing(List<? extends HasMetadata> actual, Map<String, String> configData, String type, ResultBuilder resultBuilder) {
        Set<String> expected;
        try {
            String expectedYaml = configData.get(type);
            if (expectedYaml == null) {
                resultBuilder.code(CODE_RED);
                resultBuilder.addHeader("Missing expected list for " + type);
                return true;
            }
            expected = YAML_MAPPER.readValue(expectedYaml, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            resultBuilder.code(CODE_RED);
            resultBuilder.addHeader("Invalid expected list for " + type);
            return true;
        }

        Set<String> actualNames = actual.stream().map(d -> d.getMetadata().getName()).collect(Collectors.toSet());
        expected.removeAll(actualNames);

        if (!expected.isEmpty()) {
            resultBuilder.code(CODE_RED);
            resultBuilder.addHeader("Missing " + type + "(s)");
            expected.forEach(missing -> resultBuilder.addLine("- " + missing));
            return true;
        }

        return false;
    }

    private void checkPods(ResultBuilder resultBuilder) {
        List<Pod> invalidPods = k8sClient.pods().list().getItems()
                .stream().filter(this::badState).toList();

        if (invalidPods.isEmpty()) {
            return;
        }

        resultBuilder.code(CODE_YELLOW);
        resultBuilder.addHeader("Pods with issues");
        invalidPods.forEach(p -> resultBuilder.addLine("- " + p.getMetadata().getName()));

        resultBuilder.addHeader("Recommendation");
        resultBuilder.addLine("Check the logs:\n");
        invalidPods.forEach(p -> resultBuilder.addLine("\t$ kubectl logs " + p.getMetadata().getName()));
    }

    public boolean badState(Pod pod) {
        return PodStatusUtil.getContainerStatus(pod)
                .stream().anyMatch(this::badContainerStatus);
    }

    private boolean badContainerStatus(ContainerStatus status) {
        return !(isReady(status) || isCompleted(status));
    }

    private boolean isReady(ContainerStatus status) {
        return isTrue(status.getStarted()) &&  isTrue(status.getReady());
    }

    private boolean isCompleted(ContainerStatus status) {
        ContainerStateTerminated terminated = status.getState().getTerminated();
        return terminated != null && "Completed".equals(terminated.getReason());
    }

    private boolean isTrue(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}
