package org.deltafi.core.domain.configuration;

import org.deltafi.common.properties.ZipkinProperties;
import org.deltafi.common.trace.ZipkinService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ZipkinProperties.class)
public class ZipkinConfiguration {

    @Bean
    public ZipkinService zipkinService(ZipkinProperties zipkinProperties) {
        return new ZipkinService(zipkinProperties);
    }
}