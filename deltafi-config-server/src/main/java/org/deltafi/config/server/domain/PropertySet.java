package org.deltafi.config.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * Return properties as key/value map where the value is set
     * @return - map of property keys to values
     */
    @JsonIgnore
    public Map<String, String> getPropertiesAsMap() {
        Map<String, String> props = new HashMap<>();
        properties.stream().filter(property -> Objects.nonNull(property.getValue()))
                .forEach(property -> props.put(property.getKey(), property.getValue()));
        return props;
    }

}
