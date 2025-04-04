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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

class K8sDeployerServiceTest {

    private static final InstallDetails INSTALL_DETAILS = InstallDetails.from("docker.io/plugin:1.0.0", "docker-cred");


    final K8sDeployerService k8sDeployerService = new K8sDeployerService(null, null, null, null, null, null);

    @BeforeEach
    public void setDeploymentTemplate() {
        k8sDeployerService.setBaseDeployment(new ClassPathResource("plugins/deployer/deployment-template.yaml"));
    }

    @Test
    void testCreateDeployment() throws IOException {
        Deployment deployment = k8sDeployerService.buildDeployment(INSTALL_DETAILS);

        Assertions.assertThat(deployment).isEqualTo(expectedDeployment());
    }

    @Test
    void testPreserveValuesIfUpgrade() throws IOException {
        Deployment withReplicas = expectedDeployment();
        withReplicas.getSpec().setReplicas(2);

        Deployment deployment = k8sDeployerService.buildDeployment(INSTALL_DETAILS);

        Assertions.assertThat(deployment.getSpec().getReplicas()).isNull();
        k8sDeployerService.preserveValues(deployment, withReplicas);
        Assertions.assertThat(deployment.getSpec().getReplicas()).isEqualTo(2);
    }

    Deployment expectedDeployment() {
        try {
            return Serialization.unmarshal(new ClassPathResource("plugins/deployer/expected-deployment.yaml").getInputStream(), Deployment.class);
        } catch (IOException exception) {
            Assertions.fail("Could not read the file", exception);
            return null;
        }
    }
}