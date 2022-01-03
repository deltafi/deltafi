package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.DgsTypeResolver;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.services.ActionSchemaService;

import java.util.Collection;

@DgsComponent
public class ActionSchemaDatafetcher {

    private final ActionSchemaService actionSchemaService;

    public ActionSchemaDatafetcher(ActionSchemaService actionSchemaService) {
        this.actionSchemaService = actionSchemaService;
    }

    @DgsTypeResolver(name = "ActionSchema")
    public String resolveActionSchema(ActionSchema actionSchema) {
        if (actionSchema instanceof DeleteActionSchemaImpl) {
            return "DeleteActionSchema";
        } else if (actionSchema instanceof EgressActionSchemaImpl) {
            return "EgressActionSchema";
        } else if (actionSchema instanceof EnrichActionSchemaImpl) {
            return "EnrichActionSchema";
        } else if (actionSchema instanceof FormatActionSchemaImpl) {
            return "FormatActionSchema";
        } else if (actionSchema instanceof LoadActionSchemaImpl) {
            return "LoadActionSchema";
        } else if (actionSchema instanceof TransformActionSchemaImpl) {
            return "TransformActionSchema";
        } else if (actionSchema instanceof ValidateActionSchemaImpl) {
            return "ValidateActionSchema";
        } else {
            throw new RuntimeException("RESOLVE-ACTION Invalid type: " + actionSchema.getClass().getName());
        }
    }

    @DgsQuery
    public Collection<ActionSchema> actionSchemas() {
        return actionSchemaService.getAll();
    }

    @DgsMutation
    public DeleteActionSchema registerDeleteSchema(DeleteActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public EgressActionSchema registerEgressSchema(EgressActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public EnrichActionSchema registerEnrichSchema(EnrichActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public FormatActionSchema registerFormatSchema(FormatActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public LoadActionSchema registerLoadSchema(LoadActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public TransformActionSchema registerTransformSchema(TransformActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

    @DgsMutation
    public ValidateActionSchema registerValidateSchema(ValidateActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }
}
