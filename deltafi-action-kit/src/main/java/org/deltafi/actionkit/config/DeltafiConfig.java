package org.deltafi.actionkit.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "deltafi")
public interface DeltafiConfig {
    @WithDefault("3000")
    long actionPollingInitialDelayMs();

    @WithDefault("100")
    long actionPollingPeriodMs();

    @WithDefault("1000")
    long actionRegistrationInitialDelayMs();

    @WithDefault("10000")
    long actionRegistrationPeriodMs();

    interface DgsConfig {
        String url();
    }

    DgsConfig dgs();
}
