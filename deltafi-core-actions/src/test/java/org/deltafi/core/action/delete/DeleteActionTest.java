/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.delete.DeleteResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class DeleteActionTest {
    private static final ActionContext context = ActionContext.builder().did("did-1").name("name").build();

    @Mock
    ContentStorageService contentStorageService;

    @InjectMocks
    DeleteAction deleteAction;

    @Test
    void testExecute() {
        Mockito.when(contentStorageService.deleteAll(Mockito.eq("did-1"))).thenReturn(true);

        DeltaFile deltaFile = DeltaFile.newBuilder().did("did-1").build();
        ActionParameters actionParameters = new ActionParameters();

        Result result = deleteAction.execute(deltaFile, context, actionParameters);

        assertTrue(result instanceof DeleteResult);
        assertEquals(deltaFile.getDid(), result.toEvent().getDid());
        assertEquals("name", result.toEvent().getAction());
    }

    @Test
    void testExecuteError() {
        Mockito.when(contentStorageService.deleteAll(Mockito.eq("did-1"))).thenReturn(false);

        DeltaFile deltaFile = DeltaFile.newBuilder().did("did-1").build();
        ActionParameters actionParameters = new ActionParameters();

        Result result = deleteAction.execute(deltaFile, context, actionParameters);

        assertTrue(result instanceof ErrorResult);
        assertEquals(deltaFile.getDid(), result.toEvent().getDid());
        assertEquals("name", result.toEvent().getAction());
        assertEquals("Unable to delete all objects for delta file.", result.toEvent().getError().getCause());
    }
}
