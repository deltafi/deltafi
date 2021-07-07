package org.deltafi.dgs.configuration;

import org.deltafi.common.trace.ZipkinConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix="deltafi")
public class DeltaFiProperties {
    private ConfigSource configSource = new ConfigSource();
    private IngressConfiguration ingress = new IngressConfiguration();
    private Map<String, TransformActionConfiguration> transformActions = new HashMap<>();
    private Map<String, LoadActionConfiguration> loadActions = new HashMap<>();
    private Map<String, List<String>> loadGroups = new HashMap<>();
    private Map<String, EnrichActionConfiguration> enrichActions = new HashMap<>();
    private Map<String, FormatActionConfiguration> formatActions = new HashMap<>();
    private List<String> validateActions = new ArrayList<>();
    private EgressConfiguration egress = new EgressConfiguration();
    private int defaultFeedLimit = 25;
    private int feedTimeoutSeconds = 30;
    private DeleteConfiguration delete = new DeleteConfiguration();
    private ZipkinConfig zipkin = new ZipkinConfig();

    public ConfigSource getConfigSource() {
        return configSource;
    }

    public void setConfigSource(ConfigSource configSource) {
        this.configSource = configSource;
    }

    public IngressConfiguration getIngress() {
        return ingress;
    }

    public void setIngress(IngressConfiguration ingress) {
        this.ingress = ingress;
    }

    public Map<String, TransformActionConfiguration> getTransformActions() {
        return transformActions;
    }

    public void setTransformActions(Map<String, TransformActionConfiguration> transformActions) {
        this.transformActions = transformActions;
    }

    public Map<String, LoadActionConfiguration> getLoadActions() {
        return loadActions;
    }

    public void setLoadActions(Map<String, LoadActionConfiguration> loadActions) {
        this.loadActions = loadActions;
    }

    public Map<String, List<String>> getLoadGroups() {
        return loadGroups;
    }

    public void setLoadGroups(Map<String, List<String>> loadGroups) {
        this.loadGroups = loadGroups;
    }

    public Map<String, EnrichActionConfiguration> getEnrichActions() {
        return enrichActions;
    }

    public void setEnrichActions(Map<String, EnrichActionConfiguration> enrichActions) {
        this.enrichActions = enrichActions;
    }

    public Map<String, FormatActionConfiguration> getFormatActions() {
        return formatActions;
    }

    public void setFormatActions(Map<String, FormatActionConfiguration> formatActions) {
        this.formatActions = formatActions;
    }

    public List<String> getValidateActions() {
        return validateActions;
    }

    public void setValidateActions(List<String> validateActions) {
        this.validateActions = validateActions;
    }

    public EgressConfiguration getEgress() {
        return egress;
    }

    public void setEgress(EgressConfiguration egress) {
        this.egress = egress;
    }

    public int getDefaultFeedLimit() { return defaultFeedLimit; }

    public void setDefaultFeedLimit(int defaultFeedLimit) { this.defaultFeedLimit = defaultFeedLimit; }

    public int getFeedTimeoutSeconds() { return feedTimeoutSeconds; }

    public void setFeedTimeoutSeconds(int feedTimeoutSeconds) { this.feedTimeoutSeconds = feedTimeoutSeconds; }

    public DeleteConfiguration getDelete() {
        return delete;
    }

    public void setDelete(DeleteConfiguration delete) {
        this.delete = delete;
    }

    public ZipkinConfig getZipkin() {
        return zipkin;
    }

    public void setZipkinConfig(ZipkinConfig zipkin) {
        this.zipkin = zipkin;
    }
}
