package org.deltafi.core.domain.configuration;

import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
public class DeleteConfiguration {
    private Map<String, DeletePolicyConfiguration> policies = new HashMap<>();
    private Duration frequency = Duration.ofMinutes(10);
    private boolean onCompletion = false;
}