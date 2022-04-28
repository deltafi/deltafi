package org.deltafi.core.domain.types;

import lombok.Data;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.EgressActionConfiguration;
import org.deltafi.core.domain.generated.types.EnrichActionConfiguration;
import org.deltafi.core.domain.generated.types.FormatActionConfiguration;
import org.deltafi.core.domain.generated.types.ValidateActionConfiguration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document("egressFlowPlan")
public class EgressFlowPlan {
    @Id
    private String name;
    private String description;
    private PluginCoordinates sourcePlugin;
    private EgressActionConfiguration egressAction;
    private FormatActionConfiguration formatAction;
    private List<EnrichActionConfiguration> enrichActions;
    private List<ValidateActionConfiguration> validateActions;
    private List<String> includeIngressFlows;
    private List<String> excludeIngressFlows;
}
