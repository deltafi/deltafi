package org.deltafi.dgs.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.dgs.api.types.ActionSchemaImpl;
import org.deltafi.dgs.generated.types.ActionSchemaInput;
import org.deltafi.dgs.services.ActionSchemaService;

import java.util.Collection;

@DgsComponent
public class ActionSchemaDatafetcher {

    private final ActionSchemaService actionSchemaService;

    public ActionSchemaDatafetcher(ActionSchemaService actionSchemaService) {
        this.actionSchemaService = actionSchemaService;
    }

    @DgsQuery
    public Collection<ActionSchemaImpl> actionSchemas() {
        return actionSchemaService.getAll();
    }

    @DgsMutation
    public ActionSchemaImpl registerAction(ActionSchemaInput actionSchema) {
        return actionSchemaService.save(actionSchema);
    }

}
