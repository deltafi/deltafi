/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.actionkit.action.error;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.AnnotationsResult;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.ErrorEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Specialized result class for ERROR results
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ErrorResult extends AnnotationsResult<ErrorResult> implements EgressResultType, TransformResultType,
        ResultType {
    private final String errorCause;
    private final String errorContext;
    private final String errorSummary;

    /**
     * @param context Execution context of the errored action
     * @param errorMessage Message explaining the error result
     * @param errorDetails Additional details about the error
     * @param throwable An exception that indicates the reason for the error. The stack trace will be appended to errorDetails
     */
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage, String errorDetails, Throwable throwable) {
        super(context, ActionEventType.ERROR);

        this.errorCause = errorMessage;

        String errorContext = errorDetails != null ? errorDetails : "";
        if (throwable != null) {
            StringWriter stackWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stackWriter));
            errorContext += !errorContext.isEmpty() ? "\n" : "";
            errorContext += stackWriter.toString();
        }
        this.errorContext = errorContext;

        String errorSummary = errorMessage + ": " + context.getDid();
        if (!errorContext.isEmpty()) {
            errorSummary += ("\n" + errorContext);
        }
        this.errorSummary = errorSummary;
    }

    /**
     * @param context Execution context of the errored action
     * @param errorMessage Message explaining the error result
     * @param throwable An exception that indicates the reason for the error
     */
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage, @NotNull Throwable throwable) {
        this(context, errorMessage, null, throwable);
    }

    /**
     * @param context Execution context of the errored action
     * @param errorMessage Message explaining the error result
     */
    @SuppressWarnings("unused")
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage) {
        this(context, errorMessage,  null, null);
    }

    /**
     * @param context      Execution context of the errored action
     * @param errorMessage Message explaining the error result
     * @param errorDetails Additional details about the error
     */
    @SuppressWarnings("unused")
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage, @NotNull String errorDetails) {
        this(context, errorMessage,  errorDetails, null);
    }

    /**
     * Log the error summary of the result.  Should be used prior to returning the result
     * @param logger A logger object to log the error summary
     * @return this ErrorResult for continued operations
     */
    public ErrorResult logErrorTo(Logger logger) {
        try (MDC.MDCCloseable ignoredAction = MDC.putCloseable("action", context.getActionName());
             MDC.MDCCloseable ignoredDid = MDC.putCloseable("did", context.getDid().toString())) {
            logger.error(errorSummary);
        }
        return this;
    }

    @Override
    public final ActionEvent toEvent() {
        logError(errorSummary);
        ActionEvent event = super.toEvent();
        event.setError(ErrorEvent.builder()
                .cause(errorCause)
                .context(errorContext)
                .annotations(annotations)
                .build());
        return event;
    }

    @Override
    public String toString() {
        return "ErrorResult: " + errorCause + " : " + errorContext;
    }
}
