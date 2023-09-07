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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.error.ErrorResult;

import java.util.regex.Pattern;

public class ErrorResultAssert extends ResultAssert<ErrorResultAssert, ErrorResult> {

    public ErrorResultAssert(ErrorResult errorResult) {
        super(errorResult, ErrorResultAssert.class);
    }

    public ErrorResultAssert hasCause(String exactMatch) {
        Assertions.assertThat(actual.getErrorCause()).isEqualTo(exactMatch);
        return this;
    }

    public ErrorResultAssert hasCauseLike(String regexPattern) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getErrorCause()).matches(pattern);
        return this;
    }

    public ErrorResultAssert hasContext(String exactMatch) {
        Assertions.assertThat(actual.getErrorContext()).isEqualTo(exactMatch);
        return this;
    }

    public ErrorResultAssert hasContextLike(String regexPattern) {
        Pattern pattern = Pattern.compile(regexPattern);
        Assertions.assertThat(actual.getErrorContext()).matches(pattern);
        return this;
    }
}
