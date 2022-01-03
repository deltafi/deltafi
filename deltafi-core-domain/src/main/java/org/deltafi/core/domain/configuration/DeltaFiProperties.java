package org.deltafi.core.domain.configuration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.core.domain.housekeeping.HousekeepingConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "deltafi")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeltaFiProperties {
    private int requeueSeconds = 30;
    private Duration deltaFileTtl= Duration.ofDays(14);
    private DeleteConfiguration delete = new DeleteConfiguration();
    private Duration actionInactivityThreshold = Duration.ofMinutes(5);
    private HousekeepingConfiguration housekeeping = new HousekeepingConfiguration();
}
