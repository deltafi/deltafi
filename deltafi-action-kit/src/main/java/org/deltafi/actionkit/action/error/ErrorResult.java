package org.deltafi.actionkit.action.error;

import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.ErrorInput;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

@EqualsAndHashCode(callSuper = true)
public class ErrorResult extends Result {
    private final String errorCause;
    private final String errorContext;
    private final String errorSummary;

    public ErrorResult(ActionContext actionContext, String errorMessage, Throwable throwable) {
        super(actionContext);

        this.errorCause = errorMessage;

        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        this.errorContext = throwable + "\n" + stackWriter;
        this.errorSummary = errorMessage + ": " + actionContext.getDid() + "\n" + errorContext;
    }

    @SuppressWarnings("unused")
    public ErrorResult(ActionContext actionContext, String errorMessage) {
        super(actionContext);

        this.errorCause = errorMessage;
        this.errorContext = "";
        this.errorSummary = errorMessage + ": " + actionContext.getDid();
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