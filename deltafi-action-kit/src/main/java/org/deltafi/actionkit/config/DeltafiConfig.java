package org.deltafi.actionkit.config;

import io.quarkus.arc.config.ConfigProperties;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.trace.ZipkinConfig;

import java.util.Map;

@Slf4j
@ConfigProperties(prefix = "deltafi")
@ConfigGroup
public class DeltafiConfig {

    // Quarkus config items...just terrible
    // https://github.com/quarkusio/quarkus/issues/2765
    // You have to specify and initialize the defaultValue for this to work...
    @ConfigItem(defaultValue = "3000")
    public int action_polling_start_delay_ms = 3000;

    @ConfigItem(defaultValue = "100")
    public int action_polling_frequency_ms = 100;

    public ZipkinConfig zipkin = new ZipkinConfig();

    public ZipkinConfig getZipkin() {
        return zipkin;
    }

    public void setZipkin(ZipkinConfig zipkin) {
        this.zipkin = zipkin;
    }

    public static class ActionSpec {
        @ConfigItem
        public String name;

        @ConfigItem
        public String type;

        @ConfigItem
        public Map<String, Object> parameters;
    }
}