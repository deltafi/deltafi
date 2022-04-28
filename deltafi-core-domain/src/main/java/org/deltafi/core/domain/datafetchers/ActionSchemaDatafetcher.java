package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
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
    public int registerActions(ActionRegistrationInput actionRegistration) {
        return actionSchemaService.saveAll(actionRegistration);
    }

}
