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
package org.deltafi.core.plugin.deployer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.customization.PluginCustomization;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.deltafi.core.types.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class K8sDeployerService extends BaseDeployerService {

    private static final int READY_TIMEOUT = 60;
    private static final String APP_LABEL_KEY = "app";
    private static final String PLUGIN_GROUP_LABEL_KEY = "pluginGroup";
    private static final String CONFIG_MOUNT_NAME = "config";
    private static final String CONFIG_MAP_NAME_TPL = "%s-config";
    private final KubernetesClient k8sClient;

    @Value("file:/template/action-deployment.yaml")
    private Resource baseDeployment;

    public K8sDeployerService(PluginImageRepositoryService pluginImageRepositoryService, KubernetesClient k8sClient, PluginCustomizationService pluginCustomizationService, PluginRegistryService pluginRegistryService, SystemSnapshotService systemSnapshotService, EventService eventService) {
        super(pluginImageRepositoryService, pluginRegistryService, pluginCustomizationService, systemSnapshotService, eventService);
        this.k8sClient = k8sClient;
    }

    @Override
    public Result deploy(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride) {
        PluginImageRepository pluginImageRepository = pluginImageRepositoryService.findByGroupId(pluginCoordinates);

        ArrayList<String> info = new ArrayList<>();

        if (imageRepoOverride != null) {
            pluginImageRepository.setImageRepositoryBase(imageRepoOverride);
            info.add("Image repo override: " + imageRepoOverride);
        }

        if (imagePullSecretOverride != null) {
            pluginImageRepository.setImagePullSecret(imagePullSecretOverride);
            info.add("Image pull secret override: " + imagePullSecretOverride);
        }

        PluginCustomization pluginCustomization;
        try {
            pluginCustomization = customDeploymentOverride != null ?
                    PluginCustomizationService.unmarshalPluginCustomization(customDeploymentOverride) :
                    pluginCustomizationService.getPluginCustomizations(pluginCoordinates);
        } catch (Exception e) {
            return Result.newBuilder().success(false).info(info).errors(List.of("Could not retrieve plugin customizations: " + e.getMessage())).build();
        }

        try {
            Result result = createOrReplace(createDeployment(pluginCoordinates, pluginImageRepository, pluginCustomization));
            if(result.getInfo() != null) {
                result.getInfo().addAll(info);
            }
            else {
                result.setInfo(info);
            }
            return result;
        } catch (IOException e) {
            return Result.newBuilder().success(false).info(info).errors(List.of("Could not create the deployment: " + e.getMessage())).build();
        }
    }

    @Override
    Result removeDeployment(PluginCoordinates pluginCoordinates) {
        // TODO: Fix this, it can never be true
        if (Boolean.FALSE.equals(k8sClient.apps().deployments().withName(pluginCoordinates.getArtifactId()).delete())) {
            return Result.newBuilder().success(false).errors(List.of("Failed to remove the deployment named " + pluginCoordinates.getArtifactId())).build();
        }

        return new Result();
    }

    public void setBaseDeployment(Resource resource) {
        baseDeployment = resource;
    }

    private Result createOrReplace(Deployment deployment) {
        preserveValuesIfUpgrade(deployment, k8sClient.apps().deployments().withName(deployment.getMetadata().getName()).get());

        Deployment installed = k8sClient.resource(deployment).createOrReplace();

        try {
            k8sClient.resource(installed).waitUntilReady(READY_TIMEOUT, TimeUnit.SECONDS);
        } catch (KubernetesClientTimeoutException timeoutException) {
            // TODO - should we attempt to rollback/remove the deployment or leave it for further investigation?
            return Result.newBuilder().success(false).errors(List.of("Deployment " + deployment.getMetadata().getName() + " did not reach a ready state within " + READY_TIMEOUT + " seconds")).build();
        }

        return new Result();
    }

    Deployment createDeployment(PluginCoordinates pluginCoordinates, PluginImageRepository pluginImageRepository, PluginCustomization pluginCustomization) throws IOException {
        Deployment deployment = loadBaseDeployment();

        String applicationName = pluginCoordinates.getArtifactId();
        Map<String, String> labels = Map.of(APP_LABEL_KEY, applicationName, PLUGIN_GROUP_LABEL_KEY, pluginCoordinates.getGroupId());

        deployment.getMetadata().setName(applicationName);
        deployment.getMetadata().getLabels().putAll(labels);
        deployment.getSpec().getTemplate().getMetadata().getLabels().putAll(labels);

        // just use the app label in the match label, so we don't break existing deployments
        deployment.getSpec().getSelector().getMatchLabels().put(APP_LABEL_KEY, applicationName);

        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        container.setName(applicationName);
        container.setImage(pluginImageRepository.getImageRepositoryBase() + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion());

        if (null != pluginCustomization.getExtraContainers()) {
            deployment.getSpec().getTemplate().getSpec().getContainers().addAll(pluginCustomization.getExtraContainers());
        }

        if (null != pluginCustomization.getPorts()) {
            List<ContainerPort> containerPorts = pluginCustomization.getPorts().stream().map(this::createContainerPort).map(ContainerPortBuilder::build).collect(Collectors.toList());
            container.setPorts(containerPorts);

            Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withName(applicationName).build()).withSpec(
                    new ServiceSpecBuilder().withPorts(
                            pluginCustomization.getPorts().stream().map(this::createServicePort).map(b -> b.withTargetPort(new IntOrString(b.getPort()))).map(ServicePortBuilder::build).collect(Collectors.toList())
                    ).withSelector(
                            Map.of("app", applicationName)
                    ).build()
            ).build();
            k8sClient.services().resource(service).createOrReplace();
            log.info("Created service {}", applicationName);
        }

        if (null != pluginImageRepository.getImagePullSecret()) {
            LocalObjectReference localObjectReference = new LocalObjectReferenceBuilder().withName(pluginImageRepository.getImagePullSecret()).build();
            deployment.getSpec().getTemplate().getSpec().setImagePullSecrets(List.of(localObjectReference));
        }

        addConfigMounts(deployment, applicationName);

        return deployment;
    }

    private ContainerPortBuilder createContainerPort(Integer port) {
        ContainerPortBuilder builder = new ContainerPortBuilder();
        builder.withContainerPort(port).withName("port-" + port);

        return builder;
    }

    private ServicePortBuilder createServicePort(Integer port) {
        ServicePortBuilder builder = new ServicePortBuilder();
        builder.withPort(port);

        return builder;
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

        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().add(volumeMount);
        deployment.getSpec().getTemplate().getSpec().getVolumes().add(configVolume);
    }

    void preserveValuesIfUpgrade(Deployment upgradedDeployment, Deployment existingDeployment) {
        if (existingDeployment != null) {
            upgradedDeployment.getSpec().setReplicas(existingDeployment.getSpec().getReplicas());
        }
    }

    private Deployment loadBaseDeployment() throws IOException {
        return Serialization.unmarshal(baseDeployment.getInputStream(), Deployment.class);
    }
}
