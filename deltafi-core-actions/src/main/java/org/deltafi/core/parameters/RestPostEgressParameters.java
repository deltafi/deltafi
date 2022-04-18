package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class RestPostEgressParameters extends HttpEgressParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Send metadata as JSON in this HTTP header field")
    public String metadataKey;

    public RestPostEgressParameters(String egressFlow, String url, String metadataKey, Integer retryCount, Integer retryDelayMs) {
        super(egressFlow, url, retryCount, retryDelayMs);
        this.metadataKey = metadataKey;
    }
}
