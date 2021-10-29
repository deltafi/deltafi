package org.deltafi.ingress.config;

import org.deltafi.common.properties.ZipkinProperties;
import org.deltafi.common.trace.ZipkinService;

import javax.enterprise.inject.Produces;

public class ZipkinConfig {
    @Produces
    public ZipkinService zipkinService(ZipkinProperties zipkinProperties) {
        return new ZipkinService(zipkinProperties);
    }
}
