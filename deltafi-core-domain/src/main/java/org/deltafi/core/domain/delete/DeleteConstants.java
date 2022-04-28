package org.deltafi.core.domain.delete;

import org.deltafi.core.domain.configuration.DeleteActionConfiguration;
import org.deltafi.core.domain.generated.types.ActionFamily;

import java.util.List;

public class DeleteConstants {

    private DeleteConstants() {}

    public static final String DELETE_ACTION = "DeleteAction";
    public static final DeleteActionConfiguration DELETE_ACTION_CONFIGURATION = new DeleteActionConfiguration(DELETE_ACTION, "org.deltafi.core.action.delete.DeleteAction");
    public static final ActionFamily DELETE_FAMILY = ActionFamily.newBuilder().family("delete").actionNames(List.of(DELETE_ACTION)).build();
}