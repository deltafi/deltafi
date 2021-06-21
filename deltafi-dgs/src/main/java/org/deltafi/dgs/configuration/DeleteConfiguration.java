package org.deltafi.dgs.configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class DeleteConfiguration {
    private Map<String, DeletePolicyConfiguration> policies = new HashMap<>();
    private Duration frequency = Duration.ofMinutes(10);
    private boolean onCompletion = false;

    public Map<String, DeletePolicyConfiguration> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, DeletePolicyConfiguration> policies) {
        this.policies = policies;
    }

    public Duration getFrequency() { return this.frequency; }
    public void setFrequency(Duration frequency) { this.frequency = frequency; }

    public boolean isOnCompletion() { return onCompletion; }
    public void setOnCompletion(boolean onCompletion) { this.onCompletion = onCompletion; }
}
