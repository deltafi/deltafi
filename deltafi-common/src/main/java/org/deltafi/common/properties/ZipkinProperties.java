package org.deltafi.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zipkin")
public class ZipkinProperties {
    boolean enabled = true;
    String url = "http://deltafi-zipkin:9411/api/v2/spans";
    long sendInitialDelayMs = 500L;
    long sendPeriodMs = 500L;
    int maxBatchSize = 10_000;
}
