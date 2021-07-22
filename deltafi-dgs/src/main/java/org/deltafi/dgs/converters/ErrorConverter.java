package org.deltafi.dgs.converters;

import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.generated.types.DeltaFile;
import org.deltafi.dgs.generated.types.ErrorInput;

import java.util.UUID;

public class ErrorConverter {
    private ErrorConverter() {}

    public static ErrorDomain convert(ErrorInput errorInput, DeltaFile deltaFile) {
        String errorDid = UUID.randomUUID().toString();
        return ErrorDomain.newBuilder()
                .did(errorDid)
                .cause(errorInput.getCause())
                .context(errorInput.getContext())
                .fromAction(errorInput.getFromAction())
                .originatorDid(errorInput.getOriginatorDid())
                .originator(deltaFile)
                .build();
    }
}