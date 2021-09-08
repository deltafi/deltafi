package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.deltafi.actionkit.action.egress.EgressActionParameters;

import java.util.Map;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RestPostEgressParameters extends EgressActionParameters {

    public RestPostEgressParameters() {}

    public RestPostEgressParameters(String name, String egressFlow, Map<String, String> staticMetadata, String url, String metadataPrefix) {
        super(name, staticMetadata);

        setEgressFlow(egressFlow);
        this.url = url;
        this.metadataPrefix = metadataPrefix;
    }

    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonPropertyDescription("The prefix to use in the metadata file keys")
    public String metadataPrefix;

}