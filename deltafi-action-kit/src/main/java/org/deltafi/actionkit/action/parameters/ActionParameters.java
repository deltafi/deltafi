package org.deltafi.actionkit.action.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Name of the action used to track data")
    private String name;

    @JsonPropertyDescription("Static metadata that can be attached to the metadata of the DeltaFile when a DeltaFile is processed by the action")
    @Builder.Default
    private Map<String, String> staticMetadata = new HashMap<>();
}

