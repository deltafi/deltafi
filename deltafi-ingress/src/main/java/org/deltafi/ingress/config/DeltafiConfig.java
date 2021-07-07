package org.deltafi.ingress.config;

import io.quarkus.arc.config.ConfigProperties;
import io.quarkus.runtime.annotations.ConfigItem;
import org.deltafi.common.trace.ZipkinConfig;

@ConfigProperties(prefix = "deltafi")
public class DeltafiConfig {

    @ConfigItem
    private ZipkinConfig zipkin = new ZipkinConfig();

    public ZipkinConfig getZipkin() {
        return zipkin;
    }

    public void setZipkin(ZipkinConfig zipkin) {
        this.zipkin = zipkin;
    }
}
