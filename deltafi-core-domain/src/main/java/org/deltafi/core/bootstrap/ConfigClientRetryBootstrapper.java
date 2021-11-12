package org.deltafi.core.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.cloud.config.client.ConfigClientFailFastException;
import org.springframework.cloud.config.client.ConfigServerBootstrapper;
import org.springframework.cloud.config.client.ConfigServerConfigDataResource;

/**
 * Workaround for:
 * https://github.com/spring-cloud/spring-cloud-config/issues/1963
 *
 * This initializer changes the LoaderInterceptor to throw an
 * IllegalStateException instead of bubbling up the ConfigClientFailFastException
 * which is deferred and causes other issues (i.e. can't connect to mongo which takes 30 seconds to fail)
 */
@Slf4j
public class ConfigClientRetryBootstrapper implements BootstrapRegistryInitializer {

    @Override
    public void initialize(BootstrapRegistry registry) {
        registry.register(ConfigServerBootstrapper.LoaderInterceptor.class, context -> loadContext -> {
            ConfigServerConfigDataResource resource = loadContext.getResource();
            try {
                return loadContext.getInvocation().apply(loadContext.getLoaderContext(), resource);
            } catch (ConfigClientFailFastException e) {
                throw new IllegalStateException("Failed to retrieve configuration from the config-server");
            }
        });
    }
}
