package org.deltafi.config.server.environment;

import org.deltafi.config.server.constants.PropertyConstants;
import org.deltafi.config.server.domain.PropertySet;
import org.deltafi.config.server.service.PropertyService;
import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Component
public class MongoEnvironmentRepository implements EnvironmentRepository, Ordered {

    private final PropertyService propertiesService;
    private final StateHolderService stateHolderService;
    private final String label;

    public MongoEnvironmentRepository(PropertyService propertiesService, StateHolderService stateHolderService, ConfigServerProperties properties) {
        this.propertiesService = propertiesService;
        this.stateHolderService = stateHolderService;
        this.label = properties.getDefaultLabel();
    }

    @Override
    public Environment findOne(String application, String profile, String labelIn) {
        String[] profiles = parseProfiles(profile);

        Environment environment = new Environment(application, profiles, label, null, stateHolderService.getConfigStateIdString());

        List<String> applications = parseApplication(application);

        // multiple queries to ensure correct PropertySource precedence, typically 2 queries
        for (String app : applications) {
            propertiesService.findById(app)
                    .map(props -> toPropertySource(app, props))
                    .ifPresent(environment::addFirst);
        }
        return environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private PropertySource toPropertySource(String app, PropertySet props) {
        return new PropertySource(app, props.getPropertiesAsMap());
    }

    private List<String> parseApplication(String application) {
        String config = defaultIfEmpty(application, PropertyConstants.DEFAULT_PROPERTY_SETS);
        if (!config.startsWith(PropertyConstants.DEFAULT_PROPERTY_SETS)) {
            config = PropertyConstants.DEFAULT_PROPERTY_SETS + "," + config;
        }
        return Arrays.asList(StringUtils.commaDelimitedListToStringArray(config));
    }

    private String[] parseProfiles(String profile) {
        return StringUtils.hasText(profile) ?
                StringUtils.commaDelimitedListToStringArray(profile) : new String[]{PropertyConstants.PROFILE};
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
