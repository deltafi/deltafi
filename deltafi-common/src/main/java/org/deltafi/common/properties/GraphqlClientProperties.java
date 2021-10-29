package org.deltafi.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "graphql.urls")
public class GraphqlClientProperties {
    private String gateway;
    private String coreDomain;
}