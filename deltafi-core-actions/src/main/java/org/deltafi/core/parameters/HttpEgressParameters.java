package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.actionkit.action.egress.EgressActionParameters;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class HttpEgressParameters extends EgressActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonPropertyDescription("Number of times to retry a failing HTTP request")
    public Integer retryCount = 3;

    @JsonPropertyDescription("Number milliseconds to wait for an HTTP retry")
    public Integer retryDelayMs = 150;

    public HttpEgressParameters(String egressFlow, String url, Integer retryCount, Integer retryDelayMs) {
        super(egressFlow);

        this.url = url;
        this.retryCount = retryCount;
        this.retryDelayMs = retryDelayMs;
    }
}
