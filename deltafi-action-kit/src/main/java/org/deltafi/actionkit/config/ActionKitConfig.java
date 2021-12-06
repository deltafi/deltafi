package org.deltafi.actionkit.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "actions")
public interface ActionKitConfig {
    @WithDefault("3000")
    long actionPollingInitialDelayMs();

    @WithDefault("100")
    long actionPollingPeriodMs();

    @WithDefault("1000")
    long actionRegistrationInitialDelayMs();

    @WithDefault("10000")
    long actionRegistrationPeriodMs();

    String hostname();
}
