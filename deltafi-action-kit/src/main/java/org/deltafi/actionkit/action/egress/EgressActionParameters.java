package org.deltafi.actionkit.action.egress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Map;

@Getter
@Setter
public class EgressActionParameters extends ActionParameters {

    public EgressActionParameters() {}

    public EgressActionParameters(String name, Map<String, String> staticMetadata) {
        super(name, staticMetadata);
    }

    @JsonProperty(required = true)
    @JsonPropertyDescription("Name of the egress flow the DeltaFile is flowing through")
    private String egressFlow;
}
