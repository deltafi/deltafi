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
package org.deltafi.core.action;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.generated.types.Domain;
import org.deltafi.core.domain.generated.types.ErrorDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.deltafi.core.domain.api.Constants.ERROR_DOMAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class SimpleErrorFormatActionTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String DID = UUID.randomUUID().toString();
    private static final String ACTION = "MyErrorFormatAction";
    private static final String ORIGINATOR_DID = UUID.randomUUID().toString();
    private static final String FILENAME = "origFilename";
    private static final String FLOW = "theFlow";
    private static final String CONTENT_TYPE = "text/plain";

    @Mock
    private ContentStorageService contentStorageService;

    @InjectMocks
    private SimpleErrorFormatAction simpleErrorFormatAction;

    @Test
    void execute() throws IOException, ObjectStorageException {
        ContentReference contentReference = new ContentReference(FILENAME, DID, CONTENT_TYPE);
        Mockito.when(contentStorageService.save(Mockito.eq(DID), (byte[]) Mockito.any(), Mockito.eq(APPLICATION_JSON))).thenReturn(contentReference);

        ErrorDomain errorDomain = ErrorDomain.newBuilder()
                .fromAction("errored action")
                .originator(DeltaFile.newBuilder().did(ORIGINATOR_DID).build())
                .originatorDid(ORIGINATOR_DID)
                .cause("something bad")
                .context("more details")
                .build();
        Domain domain = new Domain(ERROR_DOMAIN, OBJECT_MAPPER.writeValueAsString(errorDomain), "application/json");
        SourceInfo sourceInfo = new SourceInfo(FILENAME, FLOW, List.of());
        ActionContext context = ActionContext.builder().did(DID).name(ACTION).build();
        FormatResult formatResult = (FormatResult) simpleErrorFormatAction.format(context, sourceInfo, new Content(), Collections.emptyMap(), Map.of(ERROR_DOMAIN, domain), Collections.emptyMap());

        assertEquals(DID, formatResult.toEvent().getDid());
        assertEquals(ACTION, formatResult.toEvent().getAction());
        assertEquals(ORIGINATOR_DID + "." + FILENAME + ".error", formatResult.toEvent().getFormat().getFilename());

        ArgumentCaptor<byte[]> fileContentArgumentCaptor = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(contentStorageService).save(Mockito.eq(DID), fileContentArgumentCaptor.capture(), Mockito.eq(APPLICATION_JSON));
        ErrorDomain actual = OBJECT_MAPPER.readValue(fileContentArgumentCaptor.getValue(), ErrorDomain.class);
        assertEquals(ORIGINATOR_DID, actual.getOriginatorDid());
    }
}