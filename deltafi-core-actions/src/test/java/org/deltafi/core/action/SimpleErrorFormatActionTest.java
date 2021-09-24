package org.deltafi.core.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.service.InMemoryObjectStorageService;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ErrorDomain;
import org.deltafi.core.domain.generated.types.KeyValue;
import org.deltafi.core.domain.generated.types.ObjectReference;
import org.deltafi.core.domain.generated.types.SourceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleErrorFormatActionTest {

    InMemoryObjectStorageService inMemoryObjectStorageService = new InMemoryObjectStorageService();

    SimpleErrorFormatAction action = new SimpleErrorFormatAction(inMemoryObjectStorageService);

    static final String ACTION = "MyErrorFormatAction";

    static final String ORIGINATOR_DID = UUID.randomUUID().toString();
    static final String DID = UUID.randomUUID().toString();

    static final String FLOW = "theFlow";
    static final String FILENAME = "origFilename";

    ErrorDomain errorDomain = ErrorDomain.newBuilder()
            .fromAction("errored action")
            .originator(DeltaFile.newBuilder().did(ORIGINATOR_DID).build())
            .originatorDid(ORIGINATOR_DID)
            .cause("something bad")
            .context("more details")
            .build();

    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    DeltaFile deltaFile = DeltaFile.newBuilder()
            .did(DID)
            .sourceInfo(SourceInfo.newBuilder()
                    .filename(FILENAME)
                    .flow(FLOW)
                    .build())
            .build();
    ActionParameters params = new ActionParameters(ACTION, Collections.emptyMap());

    @BeforeEach
    void setup() throws JsonProcessingException {
        inMemoryObjectStorageService.clear();
        deltaFile.setDomains(Collections.singletonList(KeyValue.newBuilder().key("error").value(objectMapper.writeValueAsString(errorDomain)).build()));
    }

    @Test
    void execute() throws JsonProcessingException {
        Result result = action.execute(deltaFile, params);

        assertTrue(result instanceof FormatResult);
        FormatResult formatResult = (FormatResult) result;
        assertEquals(DID, formatResult.toEvent().getDid());
        assertEquals(ACTION, formatResult.toEvent().getAction());
        assertEquals(ORIGINATOR_DID + "." + FILENAME + ".error", formatResult.getFilename());
        ObjectReference objectReference = objectMapper.convertValue(formatResult.getObjectReference(), ObjectReference.class);
        ErrorDomain actual = objectMapper.readValue(new String(inMemoryObjectStorageService.getObject(
                objectReference.getBucket(), objectReference.getName(), objectReference.getOffset(), objectReference.getSize())), ErrorDomain.class);
        assertEquals(ORIGINATOR_DID, actual.getOriginatorDid());
    }
}