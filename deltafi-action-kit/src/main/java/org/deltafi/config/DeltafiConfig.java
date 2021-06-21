package org.deltafi.config;

import io.quarkus.arc.config.ConfigProperties;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@ConfigProperties(prefix = "deltafi")
@ConfigGroup
public class DeltafiConfig {

    @ConfigItem
    public List<ActionSpec> actions;

    // Quarkus config items...just terrible
    // https://github.com/quarkusio/quarkus/issues/2765
    // You have to specify and initialize the defaultValue for this to work...
    @ConfigItem(defaultValue = "3000")
    public int action_polling_start_delay_ms = 3000;

    @ConfigItem(defaultValue = "100")
    public int action_polling_frequency_ms = 100;

    public static class ActionSpec {
        @ConfigItem
        public String name;

        @ConfigItem
        public String type;

        @ConfigItem
        public Map<String, Object> parameters;
    }
}