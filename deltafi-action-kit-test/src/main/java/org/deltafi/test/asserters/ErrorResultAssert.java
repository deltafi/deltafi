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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;

import java.util.regex.Pattern;

/**
 * Assertions for ErrorResults
 */
public class ErrorResultAssert extends AnnotationResultAssert<ErrorResultAssert, ErrorResult> {
    /**
     * Create a new ErrorResultAssert with the given result, asserting that the result is not null and an instance
     * of ErrorResult.
     * @param result to validate
     * @return a new ErrorResultAssert
     */
    public static ErrorResultAssert assertThat(ResultType result) {
        return assertThat(result, "Is non-null ErrorResult");
    }

    /**
     * Create a new ErrorResultAssert with the given result, asserting that the result is not null and an instance
     * of ErrorResult.
     * @param result to validate
     * @param description a description to include with the not null and instance of assertions
     * @return a new ErrorResultAssert
     */
    public static ErrorResultAssert assertThat(ResultType result, String description) {
        ResultAssertions.assertNonNullResult(result, ErrorResult.class, description);
        return new ErrorResultAssert((ErrorResult) result);
    }

    private ErrorResultAssert(ErrorResult errorResult) {
        super(errorResult, ErrorResultAssert.class);
    }

    /**
     * Verify the error cause is equal to the given cause
     * @param exactMatch expected error cause
     * @return this
     */
    public ErrorResultAssert hasCause(String exactMatch) {
        return hasCause(exactMatch, "Has cause");
    }

    /**
     * Verify the error cause is equal to the given cause
     * @param exactMatch expected error cause
     * @param description a description to include with the assertion
     * @return this
     */
    public ErrorResultAssert hasCause(String exactMatch, String description) {
        Assertions.assertThat(actual.getErrorCause()).describedAs(description).isEqualTo(exactMatch);
        return this;
    }

    /**
     * Verify the error cause matches the given regex pattern
     * @param regexPattern to match against the error cause
     * @return this
     */
    public ErrorResultAssert hasCauseLike(String regexPattern) {
        return hasCauseLike(regexPattern, "Has cause like");
    }

    /**
     * Verify the error cause matches the given regex pattern
     * @param regexPattern to match against the error cause
     * @param description a description to include with the assertion
     * @return this
     */
    public ErrorResultAssert hasCauseLike(String regexPattern, String description) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getErrorCause()).describedAs(description).matches(pattern);
        return this;
    }

    /**
     * Verify the error context is equal to the given context
     * @param exactMatch expected errorContext
     * @return this
     */
    public ErrorResultAssert hasContext(String exactMatch) {
        return hasContext(exactMatch, "Has context");
    }

    /**
     * Verify the error context is equal to the given context
     * @param exactMatch expected errorContext
     * @param description a description to include with the assertion
     * @return this
     */
    public ErrorResultAssert hasContext(String exactMatch, String description) {
        Assertions.assertThat(actual.getErrorContext()).describedAs(description).isEqualTo(exactMatch);
        return this;
    }

    /**
     * Verify the error context matches the given regex pattern
     * @param regexPattern to match against the error context
     * @return this
     */
    public ErrorResultAssert hasContextLike(String regexPattern) {
        return hasContextLike(regexPattern, "Has context like");
    }

    /**
     * Verify the error context matches the given regex pattern
     * @param regexPattern to match against the error context
     * @param description a description to include with the assertion
     * @return this
     */
    public ErrorResultAssert hasContextLike(String regexPattern, String description) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getErrorContext()).describedAs(description).matches(pattern);
        return this;
    }


    /**
     * Verify the error context contains the given text
     * @param text to search for in context
     * @return this
     */
    public ErrorResultAssert hasContextContaining(String text) {
        return hasContextContaining(text, "Has context contains");
    }

    /**
     * Verify the error context contains the given text
     * @param text to search for in context
     * @param description a description to include with the assertion
     * @return this
     */
    public ErrorResultAssert hasContextContaining(String text, String description) {
        Assertions.assertThat(actual.getErrorContext()).describedAs(description).contains(text);
        return this;
    }
}
