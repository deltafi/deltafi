package org.deltafi.actionkit.config;

import org.deltafi.common.trace.ZipkinService;

import javax.enterprise.inject.Produces;

public class ZipkinConfig {
    @Produces
    public ZipkinService zipkinService(org.deltafi.common.trace.ZipkinConfig config) {
        return new ZipkinService(config);
    }
}
