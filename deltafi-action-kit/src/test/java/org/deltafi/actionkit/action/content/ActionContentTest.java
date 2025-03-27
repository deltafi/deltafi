/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.content;

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.error.ErrorResultException;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ActionContentTest {

    @Mock
    ActionContentStorageService actionContentStorageService;

    @Test
    void testExceptionDetails() throws ObjectStorageException {
        UUID did = UUID.randomUUID();
        ActionContext context = ActionContext.builder()
                .contentStorageService(actionContentStorageService)
                .did(did)
                .build();

        byte[] bytes = "abcd".getBytes();
        String mediaType = "text/plan";
        String name = "NAME";

        Mockito.when(actionContentStorageService.save(did, bytes, name, mediaType))
                .thenThrow(new ObjectStorageException("No MINIO space left"));

        ErrorResultException ex = assertThrows(ErrorResultException.class,
                () -> ActionContent.saveContent(context, bytes, name, mediaType));

        ErrorResult errorResult = ex.toErrorResult(context);
        assertEquals("Failed to store content", errorResult.getErrorCause());
        assertTrue(errorResult.getErrorContext().contains("An error occurred when trying to save content: " + name));
        assertTrue(errorResult.getErrorContext().contains("ObjectStorageException"));
        assertTrue(errorResult.getErrorContext().contains("No MINIO space left"));
    }
}