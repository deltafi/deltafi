package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.deltafi.actionkit.action.egress.EgressActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RestPostEgressParameters extends EgressActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonPropertyDescription("Send metadata as JSON in this HTTP header field")
    public String metadataKey;

    public RestPostEgressParameters() {}

    public RestPostEgressParameters(String egressFlow, String url, String metadataKey) {
        super(egressFlow);

        this.url = url;
        this.metadataKey = metadataKey;
    }
}
