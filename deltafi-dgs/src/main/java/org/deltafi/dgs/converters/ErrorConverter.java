package org.deltafi.dgs.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.DeltaFile;
import org.deltafi.dgs.generated.types.ErrorDomain;

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