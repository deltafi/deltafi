package org.deltafi.passthrough.param;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoteEnrichParameters extends ActionParameters {
    @JsonPropertyDescription("Map of enrichment keys to enrichment values that will be added to the DeltaFile")
    private Map<String, String> enrichments;
}
