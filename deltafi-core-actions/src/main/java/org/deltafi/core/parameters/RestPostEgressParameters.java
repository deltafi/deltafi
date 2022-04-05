package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.egress.EgressActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RestPostEgressParameters extends EgressActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonProperty(required = true)
    @JsonPropertyDescription("Send metadata as JSON in this HTTP header field")
    public String metadataKey;

    @JsonPropertyDescription("Number of times to retry a failing HTTP request")
    public Integer retryCount = 3;

    @JsonPropertyDescription("Number milliseconds to wait for an HTTP retry")
    public Integer retryDelayMs = 150;

    public RestPostEgressParameters(String egressFlow, String url, String metadataKey, Integer retryCount, Integer retryDelayMs) {
        super(egressFlow);

        this.url = url;
        this.metadataKey = metadataKey;
        this.retryCount = retryCount;
        this.retryDelayMs = retryDelayMs;
    }
}
