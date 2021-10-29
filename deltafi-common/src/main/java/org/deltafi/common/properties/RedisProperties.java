package org.deltafi.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Optional;

@Data
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {
    private String url;
    private Optional<String> password;
}