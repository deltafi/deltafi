package org.deltafi.actionkit.endpoint;

import io.quarkus.arc.profile.UnlessBuildProfile;
import org.deltafi.actionkit.service.ActionEventService;
import org.deltafi.dgs.api.types.ActionInput;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.ActionEventInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Path("/action-event/{actionName}")
@UnlessBuildProfile("prod")
public class ActionEventEndpoint {
    @PathParam("actionName")
    private String actionName;

    @Inject
    ActionEventService actionEventService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ActionInput add(ActionInput actionInput) {
        validateActionInput(actionInput);
        actionEventService.putAction(actionName, actionInput);
        return actionInput;
    }

    private void validateActionInput(ActionInput actionInput) {
        validateActionParams(actionInput.getActionParams());
        validateDeltaFile(actionInput.getDeltaFile());
    }

    private void validateDeltaFile(DeltaFile deltaFile) {
        if (Objects.isNull(deltaFile)) {
            throw new IllegalArgumentException("deltaFile cannot be null");
        }

        if (Objects.isNull(deltaFile.getDid())) {
            throw new IllegalArgumentException("deltaFile.did cannot be null");
        }
    }

    private void validateActionParams(Map<String, Object> actionParams) {
        if (Objects.isNull(actionParams)) {
            throw new IllegalArgumentException("actionParams cannot be null");
        }

        if (!actionParams.containsKey("name")) {
            throw new IllegalArgumentException("actionParams.name cannot be null");
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ActionEventInput> getResults() {
        return actionEventService.getResults(actionName);
    }
}