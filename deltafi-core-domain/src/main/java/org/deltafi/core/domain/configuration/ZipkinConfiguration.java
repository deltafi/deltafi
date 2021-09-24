package org.deltafi.core.domain.configuration;

import lombok.Data;
import org.deltafi.common.trace.ZipkinConfig;
import org.deltafi.common.trace.ZipkinService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "zipkin")
@Data
public class ZipkinConfiguration implements ZipkinConfig {
    boolean enabled;
    String url;
    long sendInitialDelayMs;
    long sendPeriodMs;
    int maxBatchSize;

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public long sendInitialDelayMs() {
        return sendInitialDelayMs;
    }

    @Override
    public long sendPeriodMs() {
        return sendPeriodMs;
    }

    @Override
    public int maxBatchSize() {
        return maxBatchSize;
    }

    @Bean
    public ZipkinService zipkinService(ZipkinConfiguration config) {
        return new ZipkinService(config);
    }
}