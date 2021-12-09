package org.deltafi.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "deltafi")
public class DeltaFiSystemProperties {
    private String systemName = "deltafi";
}
