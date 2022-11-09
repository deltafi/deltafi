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
package org.deltafi.core.plugin.deployer;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.customization.PluginCustomization;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryRepo;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.deltafi.core.types.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class K8sDeployerService extends BaseDeployerService {

    private static final int READY_TIMEOUT = 60;
    private static final String APP_LABEL_KEY = "app";
    private static final String PLUGIN_GROUP_LABEL_KEY = "pluginGroup";
    private final KubernetesClient k8sClient;

    @Value("file:/template/action-deployment.yaml")
    private Resource baseDeployment;

    public K8sDeployerService(DeltaFiProperties deltaFiProperties, KubernetesClient k8sClient, PluginImageRepositoryRepo imageRepositoryRepo, PluginCustomizationService pluginCustomizationService, PluginRegistryService pluginRegistryService, SystemSnapshotService systemSnapshotService) {
        super(deltaFiProperties, imageRepositoryRepo, pluginRegistryService, pluginCustomizationService, systemSnapshotService);
        this.k8sClient = k8sClient;
    }

    @Override
    public Result deploy(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride) {
        PluginImageRepository pluginImageRepository = findByGroupId(pluginCoordinates);

        if (imageRepoOverride != null) {
            pluginImageRepository.setImageRepositoryBase(imageRepoOverride);
        }

        if (imagePullSecretOverride != null) {
            pluginImageRepository.setImagePullSecret(imagePullSecretOverride);
        }

        PluginCustomization pluginCustomization;
        try {
            pluginCustomization = customDeploymentOverride != null ?
                    PluginCustomizationService.unmarshalPluginCustomization(customDeploymentOverride) :
                    pluginCustomizationService.getPluginCustomizations(pluginCoordinates);
        } catch (Exception e) {
            return Result.newBuilder().success(false).errors(List.of("Could not retrieve plugin customizations: " + e.getMessage())).build();
        }

        try {
            return createOrReplace(createDeployment(pluginCoordinates, pluginImageRepository, pluginCustomization));
        } catch (IOException e) {
            return Result.newBuilder().success(false).errors(List.of("Could not create the deployment: " + e.getMessage())).build();
        }
    }

    @Override
    Result removeDeployment(PluginCoordinates pluginCoordinates) {
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

        if (null != pluginImageRepository.getImagePullSecret()) {
            LocalObjectReference localObjectReference = new LocalObjectReferenceBuilder().withName(pluginImageRepository.getImagePullSecret()).build();
            deployment.getSpec().getTemplate().getSpec().setImagePullSecrets(List.of(localObjectReference));
        }

        return deployment;
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
