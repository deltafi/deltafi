package org.deltafi.core.domain.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "deltafi")
@Data
public class DeltaFiProperties {
    private int requeueSeconds = 30;
    private Duration deltaFileTtl= Duration.ofDays(14);
    private DeleteConfiguration delete = new DeleteConfiguration();
    private Duration actionInactivityThreshold = Duration.ofMinutes(5);
}
