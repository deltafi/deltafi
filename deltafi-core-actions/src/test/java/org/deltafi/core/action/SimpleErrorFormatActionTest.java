package org.deltafi.core.action;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.SourceInfo;
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
import java.util.List;
import java.util.UUID;

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
        Mockito.when(contentStorageService.save(Mockito.eq(DID), (byte[]) Mockito.any())).thenReturn(contentReference);

        ErrorDomain errorDomain = ErrorDomain.newBuilder()
                .fromAction("errored action")
                .originator(DeltaFile.newBuilder().did(ORIGINATOR_DID).build())
                .originatorDid(ORIGINATOR_DID)
                .cause("something bad")
                .context("more details")
                .build();
        DeltaFile deltaFile = DeltaFile.newBuilder()
                .did(DID)
                .sourceInfo(new SourceInfo(FILENAME, FLOW, List.of()))
                .domains(List.of(new Domain(ERROR_DOMAIN, OBJECT_MAPPER.writeValueAsString(errorDomain), "application/json")))
                .build();
        ActionContext actionContext = ActionContext.builder().did(DID).name(ACTION).build();
        FormatResult formatResult = (FormatResult) simpleErrorFormatAction.execute(deltaFile, actionContext, new ActionParameters());

        assertEquals(DID, formatResult.toEvent().getDid());
        assertEquals(ACTION, formatResult.toEvent().getAction());
        assertEquals(ORIGINATOR_DID + "." + FILENAME + ".error", formatResult.toEvent().getFormat().getFilename());

        ArgumentCaptor<byte[]> fileContentArgumentCaptor = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(contentStorageService).save(Mockito.eq(DID), fileContentArgumentCaptor.capture());
        ErrorDomain actual = OBJECT_MAPPER.readValue(fileContentArgumentCaptor.getValue(), ErrorDomain.class);
        assertEquals(ORIGINATOR_DID, actual.getOriginatorDid());
    }
}