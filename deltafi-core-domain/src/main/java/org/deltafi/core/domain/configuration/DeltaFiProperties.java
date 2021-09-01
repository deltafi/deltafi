package org.deltafi.core.domain.configuration;

import lombok.Getter;
import lombok.Setter;
import org.deltafi.common.trace.ZipkinConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="deltafi")
@Getter
@Setter
public class DeltaFiProperties {
    private int requeueSeconds = 30;
    private DeleteConfiguration delete = new DeleteConfiguration();
    private ZipkinConfig zipkin = new ZipkinConfig();
}