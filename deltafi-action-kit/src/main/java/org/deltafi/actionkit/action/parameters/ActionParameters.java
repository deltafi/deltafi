package org.deltafi.actionkit.action.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ActionParameters {

    @JsonProperty(required = true)
    @JsonPropertyDescription("Name of the action used to track data")
    private String name;

    @JsonPropertyDescription("Static metadata that can be attached to the metadata of the DeltaFile when a DeltaFile is processed by the action")
    private Map<String, String> staticMetadata = new HashMap<>();

    public ActionParameters() {}

    public ActionParameters(String name, Map<String, String> staticMetadata) {
        this.name = name;
        if (Objects.nonNull(staticMetadata)) {
            this.staticMetadata = staticMetadata;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getStaticMetadata() {
        return staticMetadata;
    }

    public void setStaticMetadata(Map<String, String> staticMetadata) {
        this.staticMetadata = staticMetadata;
    }
}
