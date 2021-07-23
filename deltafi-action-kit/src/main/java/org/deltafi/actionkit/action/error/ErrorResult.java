package org.deltafi.actionkit.action.error;

import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.ErrorInput;
import org.deltafi.dgs.generated.types.ActionEventType;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ErrorResult extends Result {

    final String errorCause;
    final String errorContext;
    final private String errorSummary;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ErrorResult(String action, DeltaFile deltafile, String errorMessage, Throwable throwable) {
        super(action, deltafile.getDid());
        this.errorCause = errorMessage;

        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        this.errorContext = throwable + "\n" + stackWriter;
        this.errorSummary = errorMessage + ": " + deltafile.getDid() + "\n" + errorContext;
    }

    @SuppressWarnings("unused")
    public ErrorResult(String action, DeltaFile deltafile, String errorMessage) {
        super(action, deltafile.getDid());
        this.errorCause = errorMessage;
        this.errorContext = "";
        this.errorSummary = errorMessage + ": " + deltafile.getDid();
    }

    public ErrorResult logErrorTo(Logger logger) {
        logger.error(errorSummary);
        return this;
    }

    private ErrorInput errorInput() {
        return ErrorInput.newBuilder()
                .cause(errorCause)
                .context(errorContext)
                .build();
    }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.ERROR; }

    @Override
    final public ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setError(errorInput());
        return event;
    }
}
