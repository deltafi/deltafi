package org.deltafi.dgs.configuration;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class K8sClientConfiguration {

    @Bean
    public KubernetesClient appClient() {
        return new DefaultKubernetesClient();
    }
}
