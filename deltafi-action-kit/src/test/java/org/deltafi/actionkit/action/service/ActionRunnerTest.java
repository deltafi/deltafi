/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.common.content.ActionContentStorageService;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ActionRunnerTest {
    protected static final UUID DID = UUID.randomUUID();
    protected static final String HOSTNAME = "hostname";

    @Captor
    ArgumentCaptor<ActionEvent> actionEventCaptor;
    @Mock
    private ActionEventQueue actionEventQueue;
    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private ActionRunner actionRunner;

    private ActionContentStorageService actionContentStorageService;

    @BeforeEach
    public void preTest() {
        actionContentStorageService = new ActionContentStorageService(
                new ContentStorageService(objectStorageService));
    }

    @Test
    public void testListen() throws JsonProcessingException, ObjectStorageException {
        List<ActionContent> inputs = List.of(new ActionContent(content(), actionContentStorageService));

        ErrorTestAction action = new ErrorTestAction();

        DeltaFileMessage deltaFileMessage = deltaFileMessage(Collections.emptyMap(), inputs);

        ActionInput actionInput =
                ActionInput.builder()
                        .deltaFileMessages(List.of(deltaFileMessage))
                        .actionContext(context())
                        .build();

        byte[] bytes = "test".getBytes();
        Mockito.when(objectStorageService.putObject(Mockito.any(), Mockito.any()))
                .thenReturn(new ObjectReference("storage", "did/uuid", 0, bytes.length));

        actionInput.getActionContext().setActionVersion("1");
        actionInput.getActionContext().setHostname("host");
        actionInput.getActionContext().setStartTime(OffsetDateTime.now());
        actionInput.getActionContext().setContentStorageService(actionContentStorageService);
        actionRunner.executeAction(action, actionInput, actionInput.getReturnAddress());

        Mockito.verify(actionEventQueue)
                .putResult(actionEventCaptor.capture(), Mockito.eq(actionInput.getReturnAddress()));
        ActionEvent actionEvent = actionEventCaptor.getValue();

        assertEquals(1, actionEvent.getSavedContent().size());
    }

    private Content content() {
        return new Content("name", "text/plain", Collections.emptyList());
    }

    protected DeltaFileMessage deltaFileMessage(Map<String, String> metadata, List<ActionContent> content) {
        return DeltaFileMessage.builder()
                .metadata(metadata == null ? new HashMap<>() : metadata)
                .contentList(content.stream().map(ContentConverter::convert).toList())
                .build();
    }

    public ActionContext context() {
        return ActionContext.builder()
                .did(DID)
                .actionName("name")
                .deltaFileName("filename")
                .hostname(HOSTNAME)
                .systemName("systemName")
                .actionVersion("1.0")
                .startTime(OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .contentStorageService(actionContentStorageService)
                .build();
    }

}