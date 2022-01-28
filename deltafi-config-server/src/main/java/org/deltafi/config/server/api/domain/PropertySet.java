package org.deltafi.config.server.api.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.*;

@Data
public class PropertySet {

    @Id
    private String id;
    private String displayName;
    private String description;
    private Set<Property> properties = new HashSet<>();

}
