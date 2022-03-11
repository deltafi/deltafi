package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;


@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class DecompressionTransformParameters extends ActionParameters {
    @JsonProperty(required = true)
    @JsonPropertyDescription("Decompression type: tar, zip, gzip, tar.gz")
    public DecompressionType decompressionType;
}