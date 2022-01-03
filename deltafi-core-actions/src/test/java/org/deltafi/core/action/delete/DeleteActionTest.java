package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeleteActionTest {
    private static final ActionContext actionContext = ActionContext.builder().did("did-1").name("name").build();

    @Test
    void testExecute() {
        ContentStorageService contentStorageService = Mockito.mock(ContentStorageService.class);
        Mockito.when(contentStorageService.deleteAll(Mockito.eq("did-1"))).thenReturn(true);

        DeleteAction deleteAction = new DeleteAction(contentStorageService);

        DeltaFile deltaFile = DeltaFile.newBuilder().did("did-1").build();
        ActionParameters actionParameters = new ActionParameters();

        Result result = deleteAction.execute(deltaFile, actionContext, actionParameters);

        assertTrue(result instanceof DeleteResult);
        assertEquals(deltaFile.getDid(), result.toEvent().getDid());
        assertEquals("name", result.toEvent().getAction());
    }

    @Test
    void testExecuteError() {
        ContentStorageService contentStorageService = Mockito.mock(ContentStorageService.class);
        Mockito.when(contentStorageService.deleteAll(Mockito.eq("did-1"))).thenReturn(false);

        DeleteAction deleteAction = new DeleteAction(contentStorageService);

        DeltaFile deltaFile = DeltaFile.newBuilder().did("did-1").build();
        ActionParameters actionParameters = new ActionParameters();

        Result result = deleteAction.execute(deltaFile, actionContext, actionParameters);

        assertTrue(result instanceof ErrorResult);
        assertEquals(deltaFile.getDid(), result.toEvent().getDid());
        assertEquals("name", result.toEvent().getAction());
        assertEquals("Unable to remove all objects for delta file.", result.toEvent().getError().getCause());
    }
}
