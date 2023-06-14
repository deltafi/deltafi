/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.domain.DomainResultType;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.enrich.EnrichResultType;
import org.deltafi.actionkit.action.format.FormatResultType;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.actionkit.action.validate.ValidateResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.ErrorEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Specialized result class for ERROR results
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ErrorResult extends Result<ErrorResult> implements DomainResultType, EgressResultType, EnrichResultType,
        FormatResultType, LoadResultType, TransformResultType, ValidateResultType, ResultType {
    private final String errorCause;
    private final String errorContext;
    private final String errorSummary;

    /**
     * @param context Execution context of the errored action
     * @param errorMessage Message explaining the error result
     * @param throwable An exception that indicates the reason for the error
     */
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage, @NotNull Throwable throwable) {
        super(context, ActionEventType.ERROR);

        this.errorCause = errorMessage;

        StringWriter stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        this.errorContext = throwable + "\n" + stackWriter;
        this.errorSummary = errorMessage + ": " + context.getDid() + "\n" + errorContext;
    }

    /**
     * @param context Execution context of the errored action
     * @param errorMessage Message explaining the error result
     */
    @SuppressWarnings("unused")
    public ErrorResult(@NotNull ActionContext context, @NotNull String errorMessage) {
        super(context, ActionEventType.ERROR);

        this.errorCause = errorMessage;
        this.errorContext = "";
        this.errorSummary = errorMessage + ": " + context.getDid();
    }

    /**
     * Log the error summary of the result.  Should be used prior to returning the result
     * @param logger A logger object to log the error summary
     * @return this ErrorResult for continued operations
     */
    public ErrorResult logErrorTo(Logger logger) {
        logger.error(errorSummary);
        return this;
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setError(ErrorEvent.builder()
                .cause(errorCause)
                .context(errorContext)
                .build());
        return event;
    }
}
