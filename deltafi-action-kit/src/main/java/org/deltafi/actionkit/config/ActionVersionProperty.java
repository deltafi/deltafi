package org.deltafi.actionkit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "quarkus.application")
public class ActionVersionProperty {
    private String version = "missing-value";
}
