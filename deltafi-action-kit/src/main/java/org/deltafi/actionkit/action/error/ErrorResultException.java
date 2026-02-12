/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Exception that can be thrown by actions to signal an error result.
// ABOUTME: Converts to an ErrorResult with optional annotations and metrics.
package org.deltafi.actionkit.action.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Metric;

import java.util.*;

/**
 * Exception that is mapped to an ErrorResult
 */
@Builder(builderMethodName = "errorCause")
@AllArgsConstructor
@SuppressWarnings("unused")
public class ErrorResultException extends RuntimeException {
    /** Summary message explaining the error */
    /** Summary message explaining the error */
    private final String errorCause;
    /** Detailed error context */
    private final String errorContext;
    /** The underlying exception that caused the error */
    private final Throwable cause;
    /** Searchable annotations to attach to the DeltaFile */
    @Singular
    private final Map<String, String> annotations;
    /** Custom metrics to include with the error result */
    @Singular
    private final List<Metric> metrics;

    /**
     * Create a new ErrorResultException
     * @param errorCause Message explaining the error result
     */
    public ErrorResultException(String errorCause) {
        this(errorCause, null, null);
    }

    /**
     * Create a new ErrorResultException
     * @param errorCause Message explaining the error result
     * @param errorContext Additional details about the error
     */
    public ErrorResultException(String errorCause, String errorContext) {
        this(errorCause, errorContext, null);
    }

    /**
     * Create a new ErrorResultException
     * @param errorCause Message explaining the error result
     * @param errorContext Additional details about the error
     * @param cause An exception that indicates the reason for the error. The stack trace will be appended to errorDetails
     */
    public ErrorResultException(String errorCause, String errorContext, Throwable cause) {
        super(errorCause, cause);
        this.errorCause = errorCause;
        this.errorContext = errorContext;
        this.cause = cause;
        this.annotations = new HashMap<>();
        this.metrics = new ArrayList<>();
    }


    /** Lombok-generated builder for ErrorResultException */
    public static class ErrorResultExceptionBuilder {}

    /**
     * Create a new ErrorResultExceptionBuilder with the required errorCause field populated
     * @param errorCause Message explaining the error result
     * @return new ErrorResultExceptionBuilder
     */
    public static ErrorResultExceptionBuilder errorCause(String errorCause) {
        return new ErrorResultExceptionBuilder().errorCause(errorCause);
    }

    /**
     * Convert this exception to an ErrorResult with the given action context.
     * @param context execution context of the action that threw this exception
     * @return an ErrorResult populated from this exception's fields
     */
    public ErrorResult toErrorResult(ActionContext context) {
        ErrorResult errorResult = new ErrorResult(context, errorCause, errorContext, cause);
        if (annotations != null) {
            errorResult.addAnnotations(annotations);
        }
        if (metrics != null) {
            metrics.forEach(errorResult::add);
        }
        return errorResult;
    }
}
