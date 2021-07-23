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
    private int requeueSeconds = 30;
    private DeleteConfiguration delete = new DeleteConfiguration();
    private ZipkinConfig zipkin = new ZipkinConfig();

    public ConfigSource getConfigSource() {
        return configSource;
    }

    public void setConfigSource(ConfigSource configSource) {
        this.configSource = configSource;
    }

    public int getRequeueSeconds() { return requeueSeconds; }

    public void setRequeueSeconds(int requeueSeconds) { this.requeueSeconds = requeueSeconds; }

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
