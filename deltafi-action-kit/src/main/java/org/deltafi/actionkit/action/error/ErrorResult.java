package org.deltafi.actionkit.action.error;

import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ErrorInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

@EqualsAndHashCode(callSuper = true)
public class ErrorResult extends Result {
    private final String errorCause;
    private final String errorContext;
    private final String errorSummary;

    public ErrorResult(DeltaFile deltaFile, ActionParameters params, String errorMessage, Throwable throwable) {
        super(deltaFile, params);

        this.errorCause = errorMessage;

        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        this.errorContext = throwable + "\n" + stackWriter;
        this.errorSummary = errorMessage + ": " + deltaFile.getDid() + "\n" + errorContext;
    }

    @SuppressWarnings("unused")
    public ErrorResult(DeltaFile deltaFile, ActionParameters params, String errorMessage) {
        super(deltaFile, params);

        this.errorCause = errorMessage;
        this.errorContext = "";
        this.errorSummary = errorMessage + ": " + deltaFile.getDid();
    }

    public ErrorResult logErrorTo(Logger logger) {
        logger.error(errorSummary);
        return this;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.ERROR;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setError(ErrorInput.newBuilder()
                .cause(errorCause)
                .context(errorContext)
                .build());
        return event;
    }
}