package org.deltafi.core.domain.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ErrorDomain;

public class ErrorConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ErrorConverter() {}

    public static ErrorDomain convert(ActionEventInput event, DeltaFile deltaFile) {
        return ErrorDomain.newBuilder()
                .cause(event.getError().getCause())
                .context(event.getError().getContext())
                .fromAction(event.getAction())
                .originatorDid(event.getDid())
                .originator(deltaFile)
                .build();
    }

    public static ErrorDomain convert(Object errorDomain) {
        return objectMapper.convertValue(errorDomain, ErrorDomain.class);
    }
}