package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.services.ActionSchemaService;

import java.util.Collection;

@DgsComponent
public class ActionSchemaDatafetcher {

    private final ActionSchemaService actionSchemaService;

    public ActionSchemaDatafetcher(ActionSchemaService actionSchemaService) {
        this.actionSchemaService = actionSchemaService;
    }

    @DgsQuery
    public Collection<ActionSchema> actionSchemas() {
        return actionSchemaService.getAll();
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.DeleteActionSchema registerDeleteSchema(DeleteActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.EgressActionSchema registerEgressSchema(EgressActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.EnrichActionSchema registerEnrichSchema(EnrichActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.FormatActionSchema registerFormatSchema(FormatActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.LoadActionSchema registerLoadSchema(LoadActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.TransformActionSchema registerTransformSchema(TransformActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public org.deltafi.core.domain.generated.types.ValidateActionSchema registerValidateSchema(ValidateActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }
}
