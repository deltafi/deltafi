package org.deltafi.actionkit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.api.types.ActionInput;
import org.deltafi.dgs.generated.types.ActionEventInput;

import java.util.Collections;
import java.util.List;

public interface ActionEventService {
    default void putAction(String actionClassName, ActionInput actionEventInput) {}
    ActionInput getAction(String actionClassName) throws JsonProcessingException, InterruptedException;
    void submitResult(Result result) throws JsonProcessingException;
    default List<ActionEventInput> getResults(String actionClassName) { return Collections.emptyList(); }
}
