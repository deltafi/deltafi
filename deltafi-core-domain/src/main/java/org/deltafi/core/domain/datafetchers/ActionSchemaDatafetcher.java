package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsTypeResolver;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.core.domain.generated.types.EnrichActionSchema;
import org.deltafi.core.domain.generated.types.EnrichActionSchemaInput;
import org.deltafi.core.domain.generated.types.FormatActionSchema;
import org.deltafi.core.domain.generated.types.FormatActionSchemaInput;
import org.deltafi.core.domain.generated.types.GenericActionSchema;
import org.deltafi.core.domain.generated.types.GenericActionSchemaInput;
import org.deltafi.core.domain.generated.types.LoadActionSchema;
import org.deltafi.core.domain.generated.types.LoadActionSchemaInput;
import org.deltafi.core.domain.generated.types.TransformActionSchema;
import org.deltafi.core.domain.generated.types.TransformActionSchemaInput;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.EnrichActionSchemaImpl;
import org.deltafi.core.domain.api.types.FormatActionSchemaImpl;
import org.deltafi.core.domain.api.types.GenericActionSchemaImpl;
import org.deltafi.core.domain.api.types.LoadActionSchemaImpl;
import org.deltafi.core.domain.api.types.TransformActionSchemaImpl;
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
        if (actionSchema instanceof EnrichActionSchemaImpl) {
            return "EnrichActionSchema";
        } else if (actionSchema instanceof FormatActionSchemaImpl) {
            return "FormatActionSchema";
        } else if (actionSchema instanceof GenericActionSchemaImpl) {
            return "GenericActionSchema";
        } else if (actionSchema instanceof LoadActionSchemaImpl) {
            return "LoadActionSchema";
        } else if (actionSchema instanceof TransformActionSchemaImpl) {
            return "TransformActionSchema";
        } else {
            throw new RuntimeException("RESOLVE-ACTION Invalid type: " + actionSchema.getClass().getName());
        }
    }

    @DgsQuery
    public Collection<ActionSchema> actionSchemas() {
        return actionSchemaService.getAll();
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
    public GenericActionSchema registerGenericSchema(GenericActionSchemaInput actionSchema) {
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
}
