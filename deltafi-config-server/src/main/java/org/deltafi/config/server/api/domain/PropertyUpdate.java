package org.deltafi.config.server.api.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyUpdate {
    private String propertySetId;
    private String key;
    private String value;
}
