package org.deltafi.dgs.k8s;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.deltafi.dgs.configuration.DomainEndpointConfiguration;
import org.deltafi.dgs.generated.types.ConfigQueryInput;
import org.deltafi.dgs.generated.types.ConfigType;
import org.deltafi.dgs.generated.types.DomainEndpointConfigurationInput;
import org.deltafi.dgs.services.DeltaFiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;

@Profile("k8s")
@org.springframework.stereotype.Service
public class GatewayConfigService {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigService.class);

    public static final String DOMAIN_SERVICE_LABEL_KEY = "deltafi-domain-service";
    public static final String SERVICE_LIST_KEY = "SERVICE_LIST";
    public static final String APOLLO_GATEWAY_CONFIG = "apollo-gateway-config";
    public static final String DELTAFI_DGS = "dgs";
    public static final String APOLLO_DEPLOYMENT = "deltafi-apollo-gateway";
    public static final String API_VERSION_ANNOTATION_KEY = "apiVersion";
    public static final String DOMAIN_VERSION_ANNOTATION_KEY = "domainVersion";
    public static final String GRAPHQL_ENDPOINT_ANNOTATION_KEY = "graphql-endpoint";

    private static final DomainEndpointConfigurationInput DELTAFI_DGS_SERVICE = DomainEndpointConfigurationInput.newBuilder()
            .name(DELTAFI_DGS)
            .url("http://deltafi-dgs-service/graphql")
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${deltafi.namespace}")
    public String namespace = "deltafi";

    private final KubernetesClient k8s;
    private final DeltaFiConfigService configService;

    public GatewayConfigService(KubernetesClient k8s, DeltaFiConfigService configService) {
        this.k8s = k8s;
        this.configService = configService;
    }

    /**
     * Watch for any new services and update the apollo-gateway ConfigMap for any
     * services labeled as a deltafi-domain-service
     */
    @PostConstruct
    public Watch startServiceWatcher() {
        // TODO - should we sync the ConfigMap at startup?
        Watch watch = k8s.services().inNamespace(namespace).watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Service resource) {
                domainServiceName(resource).ifPresent(name -> handleDomainServiceEvent(name, resource, action));
            }

            @Override
            public void onClose(WatcherException cause) {
                log.warn("K8S service watcher is shutting down.", cause);
            }
        });

        log.info("Watching for new services");

        return watch;
    }

    public void handleDomainServiceEvent(String name, Service service, Watcher.Action action) {
        switch (action) {
            case ADDED:
            case MODIFIED:
                addOrModify(name, service);
                break;
            case DELETED:
                deleteService(name);
                break;
            case ERROR:
                log.warn("Domain service {} was moved to an error state", name);
                break;
        }
    }

    public void addOrModify(String name, Service service) {
        DomainEndpointConfigurationInput.Builder endpointConfigurationInput = DomainEndpointConfigurationInput.newBuilder()
                .name(name)
                .url(serviceToUrl(service));

        getAnnotation(service, API_VERSION_ANNOTATION_KEY).ifPresent(endpointConfigurationInput::apiVersion);
        getAnnotation(service, DOMAIN_VERSION_ANNOTATION_KEY).ifPresent(endpointConfigurationInput::domainVersion);

        log.info("Add or update service for {}", name);
        configService.saveDomainEndpoint(endpointConfigurationInput.build());
    }

    protected void deleteService(String name) {
        ConfigQueryInput query = ConfigQueryInput.newBuilder().configType(ConfigType.DOMAIN_ENDPOINT).name(name).build();

        log.info("Remove service for {}", name);
        configService.removeDeltafiConfigs(query);
    }

    public void refreshApolloConfig() {
        Collection<DomainEndpointConfiguration> currentEndpoints = configService.getDomainEndpoints();
        addDeltaFiDgsServiceIfMissing(currentEndpoints);

        List<Map<String, String>> serviceMaps = currentEndpoints.stream().map(this::fromDomainEndpointConfig).collect(Collectors.toList());

        try {
            String serviceJson = mapper.writeValueAsString(serviceMaps);

            k8s.configMaps().inNamespace(namespace)
                    .withName(APOLLO_GATEWAY_CONFIG)
                    .edit(cm -> new ConfigMapBuilder(cm)
                            .removeFromData(SERVICE_LIST_KEY)
                            .addToData(SERVICE_LIST_KEY, serviceJson)
                            .build());

            k8s.apps().deployments().inNamespace(namespace).withName(APOLLO_DEPLOYMENT).rolling().restart();
        } catch (JsonProcessingException e) {
            log.error("Failed to update the apollo-gateway-config", e);
        }
    }

    private Map<String, String> fromDomainEndpointConfig(DomainEndpointConfiguration domainEndpointConfiguration) {
        Map<String, String> map = new HashMap<>();
        map.put("name", domainEndpointConfiguration.getName());
        map.put("url", domainEndpointConfiguration.getUrl());
        return map;
    }

    public String serviceToUrl(Service service) {
        Integer port = service.getSpec().getPorts().get(0).getPort();
        String serviceName = service.getMetadata().getName();

        StringBuilder urlBuilder = new StringBuilder("http://");
        urlBuilder.append(serviceName);

        if (Objects.nonNull(port) && 80 != port) {
            urlBuilder.append(":").append(port);
        }

        urlBuilder.append("/").append(graphqlEndpoint(service));
        return urlBuilder.toString();
    }

    public String graphqlEndpoint(Service service) {
        return getAnnotation(service, GRAPHQL_ENDPOINT_ANNOTATION_KEY).orElse("graphql");
    }

    /**
     * Return the the value of the deltafi-domain-service label if it is set
     * otherwise return empty
     *
     * @param service - K8S service to build the url from
     * @return - Name of the deltafi-domain-service to register
     */
    private Optional<String> domainServiceName(Service service) {
        if (Objects.nonNull(service.getMetadata()) && Objects.nonNull(service.getMetadata().getLabels())) {
            return Optional.ofNullable(service.getMetadata().getLabels().get(DOMAIN_SERVICE_LABEL_KEY));
        }
        return Optional.empty();
    }

    private Optional<String> getAnnotation(Service service, String key) {
        if (Objects.isNull(service.getMetadata()) || Objects.isNull(service.getMetadata().getAnnotations())) {
            return Optional.empty();
        }

        return Optional.ofNullable(service.getMetadata().getAnnotations().get(key));
    }

    private void addDeltaFiDgsServiceIfMissing(Collection<DomainEndpointConfiguration> endpoints) {
        if (endpoints.stream().filter(this::isDeltaFiDgsService).findFirst().isEmpty()) {
            endpoints.add(configService.saveDomainEndpoint(DELTAFI_DGS_SERVICE));
        }
    }

    private boolean isDeltaFiDgsService(DomainEndpointConfiguration domainEndpointConfiguration) {
        return DELTAFI_DGS.equals(domainEndpointConfiguration.getName());
    }

    @PreDestroy
    public void closeK8sClient() {
        k8s.close();
    }
}
