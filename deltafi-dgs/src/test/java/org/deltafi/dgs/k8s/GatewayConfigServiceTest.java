package org.deltafi.dgs.k8s;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.deltafi.dgs.generated.types.DomainEndpointConfigurationInput;
import org.deltafi.dgs.services.DeltaFiConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.deltafi.dgs.k8s.GatewayConfigService.*;

@EnableKubernetesMockClient(crud = true)
@ExtendWith(MockitoExtension.class)
class GatewayConfigServiceTest {

    private static final String DOMAIN_LABEL = "test-domain-service";
    private static final Map<String, String> DOMAIN_LABEL_MAP = Map.of(DOMAIN_SERVICE_LABEL_KEY, DOMAIN_LABEL);
    private static final Map<String, String> ANNOTATIONS = Map.of(API_VERSION_ANNOTATION_KEY, "v1", DOMAIN_VERSION_ANNOTATION_KEY, "v2", GRAPHQL_ENDPOINT_ANNOTATION_KEY, "custom-graphql");

    @InjectMocks
    GatewayConfigService gatewayConfigService;

    @Mock
    DeltaFiConfigService configService;

    @Spy
    KubernetesClient client;

    @Test
    void startServerWatcher() {
        Watch watch = gatewayConfigService.startServiceWatcher();

        // Add a new service with the deltafi-domain-service label which should be sent to the configService
        client.services().inNamespace("deltafi").create(builderService());

        Mockito.verify(configService, Mockito.timeout(1000L)).saveDomainEndpoint(Mockito.argThat((arg) -> DOMAIN_LABEL.equals(arg.getName())));

        watch.close();
    }

    @Test
    void handleDomainServiceEvent_Added() {
        DomainEndpointConfigurationInput expected = DomainEndpointConfigurationInput.newBuilder()
                .name(DOMAIN_LABEL)
                .url("http://my-service:81/custom-graphql")
                .apiVersion("v1")
                .domainVersion("v2")
                .build();
        gatewayConfigService.handleDomainServiceEvent(DOMAIN_LABEL, builderService(), Watcher.Action.ADDED);
        Mockito.verify(configService).saveDomainEndpoint(expected);
    }

    @Test
    void handleDomainServiceEvent_Modified() {
        gatewayConfigService.handleDomainServiceEvent(DOMAIN_LABEL, builderService(), Watcher.Action.MODIFIED);
        Mockito.verify(configService).saveDomainEndpoint(Mockito.argThat((arg) -> DOMAIN_LABEL.equals(arg.getName())));
    }

    @Test
    void handleDomainServiceEvent_Deleted() {
        gatewayConfigService.handleDomainServiceEvent(DOMAIN_LABEL, builderService(), Watcher.Action.DELETED);
        Mockito.verify(configService).removeDeltafiConfigs(Mockito.argThat((arg) -> DOMAIN_LABEL.equals(arg.getName())));
    }

    @Test
    void handleDomainServiceEvent_Error() {
        gatewayConfigService.handleDomainServiceEvent(DOMAIN_LABEL, builderService(), Watcher.Action.ERROR);
        Mockito.verifyNoInteractions(configService);
    }

    private Service builderService() {
        return new ServiceBuilder()
                .withNewMetadata()
                .withName("my-service")
                .withLabels(DOMAIN_LABEL_MAP)
                .withAnnotations(ANNOTATIONS)
                .endMetadata()
                .withNewSpec()
                .withSelector(Collections.singletonMap("app", "MyApp"))
                .addNewPort()
                .withName("test-port")
                .withProtocol("TCP")
                .withPort(81)
                .withTargetPort(new IntOrString(9376))
                .endPort()
                .withType("LoadBalancer")
                .endSpec()
                .build();
    }
}