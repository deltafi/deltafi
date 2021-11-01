package org.deltafi.actionkit.action.egress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class EgressActionParameters extends ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Name of the egress flow the DeltaFile is flowing through")
    private String egressFlow;

    public EgressActionParameters() {}

    public EgressActionParameters(String name, Map<String, String> staticMetadata, String egressFlow) {
        super(name, staticMetadata);

        this.egressFlow = egressFlow;
    }
}
