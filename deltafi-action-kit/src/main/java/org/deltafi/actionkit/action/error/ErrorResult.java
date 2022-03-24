package org.deltafi.actionkit.action.error;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.ErrorInput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ErrorResult extends Result {
    private final String errorCause;
    private final String errorContext;
    private final String errorSummary;

    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage, @NotNull Throwable throwable) {
        super(context);

        this.errorCause = errorMessage;

        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        this.errorContext = throwable + "\n" + stackWriter;
        this.errorSummary = errorMessage + ": " + context.getDid() + "\n" + errorContext;
    }

    @SuppressWarnings("unused")
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage) {
        super(context);

        this.errorCause = errorMessage;
        this.errorContext = "";
        this.errorSummary = errorMessage + ": " + context.getDid();
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