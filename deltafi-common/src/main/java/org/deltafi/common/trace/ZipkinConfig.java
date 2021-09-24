package org.deltafi.common.trace;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "zipkin")
public interface ZipkinConfig {
    @WithDefault("true")
    boolean enabled();

    String url();

    @WithDefault("500")
    long sendInitialDelayMs();

    @WithDefault("500")
    long sendPeriodMs();

    @WithDefault("10000")
    int maxBatchSize();
}
