package org.deltafi.core.domain.types;

import lombok.Data;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.LoadActionConfiguration;
import org.deltafi.core.domain.generated.types.TransformActionConfiguration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document("ingressFlowPlan")
public class IngressFlowPlan {

    @Id
    private String name;
    private String description;
    private PluginCoordinates sourcePlugin;
    private String type;
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private LoadActionConfiguration loadAction;
}
