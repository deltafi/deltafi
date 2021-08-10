package org.deltafi.commonactions.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.actionkit.action.parameters.ActionParameters;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class RestPostEgressParameters extends ActionParameters {

    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to post the DeltaFile to")
    public String url;

    @JsonPropertyDescription("The prefix to use in the metadata file keys")
    public String metadataPrefix;

}