package org.deltafi.config.server.environment;

import org.apache.logging.log4j.util.Strings;
import org.deltafi.config.server.constants.PropertyConstants;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.FailedToConstructEnvironmentException;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.PassthruEnvironmentRepository;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.deltafi.config.server.constants.PropertyConstants.PROFILE;

public class DeltaFiNativeEnvironmentRepository extends NativeEnvironmentRepository {

    private final String label;

    public DeltaFiNativeEnvironmentRepository(ConfigurableEnvironment environment, NativeEnvironmentProperties properties, String label) {
        super(environment, properties);
        this.label = label;
    }

    public synchronized Environment findOne(Set<String> applications) {
        return findOne(Strings.join(applications, ','), PROFILE, label);
    }

    @Override
    public synchronized Environment findOne(String config, String profile, String label, boolean includeOrigin) {
        try {
            ConfigurableEnvironment environment = getDeltaFiEnvironment(config);
            DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
            ConfigDataEnvironmentPostProcessor.applyTo(environment, resourceLoader, null,
                    StringUtils.commaDelimitedListToSet(PropertyConstants.PROFILE));

            environment.getPropertySources().remove("config-data-setup");
            return new PassthruEnvironmentRepository(environment).findOne(config, PropertyConstants.PROFILE, this.label, includeOrigin);
        }
        catch (Exception e) {
            String msg = String.format("Could not construct context for config=%s profile=%s label=%s includeOrigin=%b",
                    config, PropertyConstants.PROFILE, label, includeOrigin);
            String completeMessage = NestedExceptionUtils.buildMessage(msg,
                    NestedExceptionUtils.getMostSpecificCause(e));
            throw new FailedToConstructEnvironmentException(completeMessage, e);
        }
    }

    private ConfigurableEnvironment getDeltaFiEnvironment(String application) {
        ConfigurableEnvironment environment = new StandardEnvironment();
        Map<String, Object> map = new HashMap<>();
        map.put("spring.profiles.active", PROFILE);
        String config = application;
        if (!config.startsWith(PropertyConstants.DEFAULT_PROPERTY_SETS)) {
            config = PropertyConstants.DEFAULT_PROPERTY_SETS + "," + config;
        }
        map.put("spring.config.name", config);
        map.put("spring.config.location",
                StringUtils.arrayToCommaDelimitedString(getLocations(application, PROFILE, label).getLocations()));
        // globally ignore config files that are not found
        map.put("spring.config.on-not-found", "IGNORE");
        environment.getPropertySources().addFirst(new MapPropertySource("config-data-setup", map));
        return environment;
    }

}
