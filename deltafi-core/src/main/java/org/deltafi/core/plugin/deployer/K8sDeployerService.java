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
package org.deltafi.core.plugin.deployer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.SystemSnapshotService;
import org.deltafi.core.types.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class K8sDeployerService extends BaseDeployerService {
    static final String APP_LABEL_KEY = "app";
    static final String CONFIG_MOUNT_NAME = "config";
    private static final String CONFIG_MAP_NAME_TPL = "%s-config";
    public static final String PROGRESSING = "Progressing";
    public static final String PROGRESS_DEADLINE_EXCEEDED = "ProgressDeadlineExceeded";
    private static final String MISSING_POD = "No pod was found for the given plugin coordinates that was not ready";

    private final KubernetesClient k8sClient;
    private final PodService podService;

    @Setter
    @Value("file:/template/action-deployment.yaml")
    private Resource baseDeployment;

    public K8sDeployerService(DeltaFiPropertiesService deltaFiPropertiesService, KubernetesClient k8sClient, PodService podService, PluginService pluginService, EventService eventService) {
        super(pluginService, eventService, deltaFiPropertiesService);
        this.k8sClient = k8sClient;
        this.podService = podService;
    }

    @Override
    public DeployResult deploy(InstallDetails installDetails) {
        try {
            return createOrReplace(buildDeployment(installDetails), installDetails);
        } catch (IOException e) {
            return DeployResult.builder().success(false).errors(List.of("Could not create the deployment: " + e.getMessage())).build();
        }
    }

    @Override
    Result removePluginResources(String appName) {
        Result result = new Result();
        deleteDeployment(appName, result);
        return result;
    }

    @Override
    public void restartPlugin(String plugin) {
        Deployment dep = k8sClient.apps().deployments().withName(plugin).get();
        if (dep != null) {
            k8sClient.apps().deployments().resource(dep).rolling().restart();
            k8sClient.resource(dep).waitUntilCondition(this::rolloutSuccessful, getTimeoutInMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void restartApp(String podOrContainer) {
        try {
            k8sClient.pods().withName(podOrContainer).delete();
        } catch (Exception e) {
            log.error("Could not restart {} via a delete", podOrContainer, e);
        }
    }

    private DeployResult createOrReplace(Deployment deployment, InstallDetails installDetails) {
        Deployment existingDeployment = k8sClient.apps().deployments().withName(deployment.getMetadata().getName()).get();

        boolean isUpgrade = existingDeployment != null;

        if (isUpgrade) {
            preserveValues(deployment, existingDeployment);
        }

        Deployment installed = k8sClient.resource(deployment).serverSideApply();

        try {
            k8sClient.resource(installed).waitUntilCondition(this::rolloutSuccessful, getTimeoutInMillis(), TimeUnit.MILLISECONDS);
        } catch (KubernetesClientTimeoutException | IllegalStateException exception) {
            DeployResult deployResult = new DeployResult();
            deployResult.setSuccess(false);
            Pod pod = podService.findNotReadyPluginPod(installDetails).orElse(null);

            if (pod == null) {
                deployResult.getErrors().add(MISSING_POD);
            } else {
                List<Event> events = podService.podEvents(pod);
                String logs = podService.podLogs(pod, installDetails.appName());

                deployResult.setEvents(events.stream().map(K8sEventUtil::formatEvent).toList());
                deployResult.setLogs(logs);
            }

            if (deltaFiPropertiesService.getDeltaFiProperties().isPluginAutoRollback()) {
                if (isUpgrade) {
                    k8sClient.apps().deployments().resource(installed).rolling().undo();
                } else {
                    k8sClient.apps().deployments().resource(installed).delete();
                }
            }

            return deployResult;
        }

        return new DeployResult();
    }

    Deployment buildDeployment(InstallDetails installDetails) throws IOException {
        Deployment deployment = loadBaseDeployment();

        String applicationName = installDetails.appName();
        Map<String, String> labels = Map.of(APP_LABEL_KEY, applicationName);

        deployment.getMetadata().setName(applicationName);
        deployment.getMetadata().getLabels().putAll(labels);
        deployment.getSpec().getTemplate().getMetadata().getLabels().putAll(labels);

        // just use the app label in the match label, so we don't break existing deployments
        deployment.getSpec().getSelector().getMatchLabels().put(APP_LABEL_KEY, applicationName);

        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().getFirst();
        container.setName(applicationName);
        container.setImage(installDetails.image());
        List<EnvVar> envVars = container.getEnv();
        envVars.add(new EnvVar(IMAGE, installDetails.image(), null));

        if (StringUtils.isNotBlank(installDetails.imagePullSecret())) {
            envVars.add(new EnvVar(IMAGE_PULL_SECRET, installDetails.imagePullSecret(), null));
            LocalObjectReference localObjectReference = new LocalObjectReferenceBuilder().withName(installDetails.imagePullSecret()).build();
            deployment.getSpec().getTemplate().getSpec().setImagePullSecrets(List.of(localObjectReference));
        }

        addConfigMounts(deployment, applicationName);

        return deployment;
    }

    void deleteDeployment(String appName, Result result) {
        List<StatusDetails> details = k8sClient.apps().deployments().withName(appName).delete();

        if (details.isEmpty()) {
            result.setSuccess(false);
            result.getErrors().add("No deployment exists for " + appName);
            return;
        } else if(details.size() > 1) {
            result.setSuccess(false);
            result.getErrors().add("Unexpected delete results for deployment " + appName + " " + details);
            return;
        }

        checkStatusDetail(details.getFirst(), result);
    }

    void checkStatusDetail(StatusDetails statusDetails, Result result) {
        List<StatusCause> statusCauses = statusDetails.getCauses();

        if (!statusCauses.isEmpty()) {
            result.setSuccess(false);
            result.getErrors().addAll(statusCauses.stream().map(this::statusCause).toList());
        }
    }

    String statusCause(StatusCause statusCause) {
        return "Field: " + statusCause.getField() + " Message: " + statusCause.getMessage() + " Reason: " + statusCause.getReason();
    }

    /**
     * Add optional volume mount where extra properties can set for a plugin
     * @param deployment to add config mount to
     * @param applicationName name of the application
     */
    void addConfigMounts(Deployment deployment, String applicationName) {
        VolumeMount volumeMount = new VolumeMountBuilder()
                .withName(CONFIG_MOUNT_NAME)
                .withMountPath("/config")
                .withReadOnly(true)
                .build();

        Volume configVolume = new VolumeBuilder()
                .withName(CONFIG_MOUNT_NAME)
                .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(String.format(CONFIG_MAP_NAME_TPL, applicationName)).withOptional(true).build())
                .build();

        deployment.getSpec().getTemplate().getSpec().getContainers().getFirst().getVolumeMounts().add(volumeMount);
        deployment.getSpec().getTemplate().getSpec().getVolumes().add(configVolume);
    }

    void preserveValues(Deployment upgradedDeployment, Deployment existingDeployment) {
        if (existingDeployment != null) {
            upgradedDeployment.getSpec().setReplicas(existingDeployment.getSpec().getReplicas());
        }
    }

    private boolean rolloutSuccessful(Deployment deployment) {
        if (deployment == null) {
            throw new IllegalStateException("Deployment was removed prior to completing");
        }

        DeploymentStatus deploymentStatus = deployment.getStatus();

        if (deploymentStatus == null) {
            log.debug("Waiting for deployment spec update to be observed...\n");
            return false;
        }

        String deploymentName = deployment.getMetadata().getName();
        long generation = getGeneration(deployment.getMetadata());
        long observedGeneration = getObservedGeneration(deployment.getStatus());

        Integer specReplicas = deployment.getSpec().getReplicas();
        int statusReplicas = valueOrDefault(deploymentStatus.getReplicas(), 0);
        int updatedReplicas = valueOrDefault(deploymentStatus.getUpdatedReplicas(), 0);
        int availableReplicas = valueOrDefault(deploymentStatus.getAvailableReplicas(), 0);

        if (generation <= observedGeneration) {
            if (deployment.getStatus().getConditions().stream().anyMatch(this::progressingTimedOut)) {
                throw new IllegalStateException("Deployment " + deploymentName + " exceeded its progress deadline");
            }

            if (specReplicas != null && updatedReplicas < specReplicas) {
                log.debug("Waiting for deployment {} rollout to finish: {} out of {} new replicas have been updated...\n", deploymentName, updatedReplicas, specReplicas);
                return false;
            }

            if (statusReplicas > updatedReplicas) {
                log.debug("Waiting for deployment {} rollout to finish: {} old replicas are pending termination...\n", deploymentName, statusReplicas-updatedReplicas);
                return false;
            }

            if (availableReplicas < updatedReplicas) {
                log.debug("Waiting for deployment {} rollout to finish: {} of {} updated replicas are available...\n", deploymentName, availableReplicas, updatedReplicas);
                return false;
            }

            return true;
        }

        return false;
    }

    private long getGeneration(ObjectMeta deploymentMetadata) {
        return valueOrDefault(deploymentMetadata.getGeneration(), 0);
    }

    private long getObservedGeneration(DeploymentStatus status) {
        return status != null ? valueOrDefault(status.getObservedGeneration(), -1) : -1;
    }

    private long valueOrDefault(Long value, long defaultValue) {
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("SameParameterValue")
    private int valueOrDefault(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    private boolean progressingTimedOut(DeploymentCondition condition) {
        return PROGRESSING.equals(condition.getType()) && PROGRESS_DEADLINE_EXCEEDED.equals(condition.getReason());
    }

    private Deployment loadBaseDeployment() throws IOException {
        return Serialization.unmarshal(baseDeployment.getInputStream(), Deployment.class);
    }

    private long getTimeoutInMillis() {
        Duration timeout = deltaFiPropertiesService.getDeltaFiProperties().getPluginDeployTimeout();
        return timeout != null ? timeout.toMillis() : 60_000L;
    }
}
