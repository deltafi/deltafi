package org.deltafi.config.server.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property {
    private String key;
    private String value;
    private String description;
    private String defaultValue;
    private transient PropertySource propertySource;
    private boolean refreshable;
    private boolean editable;
    private boolean hidden;
}
