package org.deltafi.core.domain.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "deltafi")
@Data
public class DeltaFiProperties {
    private int requeueSeconds = 30;
    private DeleteConfiguration delete = new DeleteConfiguration();
}