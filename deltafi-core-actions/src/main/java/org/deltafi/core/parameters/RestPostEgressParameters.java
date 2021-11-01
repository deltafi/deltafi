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
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonPropertyDescription("The prefix to use in the metadata file keys")
    public String metadataPrefix;

    public RestPostEgressParameters() {}

    public RestPostEgressParameters(String name, Map<String, String> staticMetadata, String egressFlow, String url, String metadataPrefix) {
        super(name, staticMetadata, egressFlow);

        this.url = url;
        this.metadataPrefix = metadataPrefix;
    }
}