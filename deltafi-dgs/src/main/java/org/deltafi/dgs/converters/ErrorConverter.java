package org.deltafi.dgs.converters;

import org.deltafi.dgs.api.types.ErrorDomain;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.DeltaFile;
import org.deltafi.dgs.generated.types.ErrorInput;

import java.util.UUID;

public class ErrorConverter {
    private ErrorConverter() {}

    public static ErrorDomain convert(ActionEventInput event, DeltaFile deltaFile) {
        String errorDid = UUID.randomUUID().toString();
        return ErrorDomain.newBuilder()
                .did(errorDid)
                .cause(event.getError().getCause())
                .context(event.getError().getContext())
                .fromAction(event.getAction())
                .originatorDid(event.getDid())
                .originator(deltaFile)
                .build();
    }

    public static ErrorDomain convert(org.deltafi.dgs.generated.types.ErrorDomain errorDomain) {
        return ErrorDomain.newBuilder()
                .did(errorDomain.getDid())
                .cause(errorDomain.getCause())
                .context(errorDomain.getContext())
                .fromAction(errorDomain.getFromAction())
                .originatorDid(errorDomain.getOriginatorDid())
                .originator(errorDomain.getOriginator())
                .build();
    }
}