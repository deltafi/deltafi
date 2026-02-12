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
package org.deltafi.test.action;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Base class for test case configuration used by the deprecated test framework.
 * @param <A> the action type being tested
 * @deprecated Use the DeltaFiTestRunner to set up the test and run the action directly.
 * The result can be verified using {@link org.deltafi.test.asserters.ActionResultAssertions}.
 */
@Deprecated
@Data
@EqualsAndHashCode
@SuperBuilder
public class TestCaseBase<A extends Action<?, ? extends ActionParameters, ? extends ResultType>> {

    private Class<? extends Result<?>> expectedResultType;

    /** The action instance under test. */
    public A action;

    /** The action parameters to use during the test. */
    public Map<String, Object> parameters;

    /** The name of this test case, used for locating test resources. */
    public String testName;

    @Builder.Default
    private List<IOContent> inputs = Collections.emptyList();

    private Map<String, String> sourceMetadata;

    @Builder.Default
    private List<? extends IOContent> outputs = Collections.emptyList();

    @Builder.Default
    private Map<String, String> resultMetadata = Collections.emptyMap();

    @Builder.Default
    private List<String> resultDeleteMetadataKeys = Collections.emptyList();

    private Pattern regex;

    @Builder.Default
    private Throwable exception = null;

    @Builder.Default
    private ExceptionLocation exceptionLocation = ExceptionLocation.NONE;

    /** Identifies where a simulated exception should be thrown during test execution. */
    public enum ExceptionLocation {
        /** No exception will be thrown. */
        NONE,
        /** Exception thrown during content storage read. */
        STORAGE_READ,
        /** Exception thrown during content storage write. */
        STORAGE_WRITE
    }

    /**
     * Builder base class for constructing TestCaseBase instances.
     * @param <A> the action type being tested
     * @param <C> the concrete TestCaseBase subclass
     * @param <B> the concrete builder subclass (for fluent API)
     */
    public static abstract class TestCaseBaseBuilder<A extends Action<?, ? extends ActionParameters, ? extends ResultType>, C extends TestCaseBase<A>, B extends TestCaseBase.TestCaseBaseBuilder<A, C, B>> {

        /** Configure the test case to expect an ErrorResult matching the given regex string.
         * @param errorRegex the regex to match against the error cause
         * @return this builder */
        public B expectError(String errorRegex) {
            return expectError(Pattern.compile(errorRegex));
        }

        /** Configure the test case to expect an ErrorResult matching the given pattern.
         * @param errorRegex the pattern to match against the error cause
         * @return this builder */
        public B expectError(Pattern errorRegex) {
            expectedResultType(ErrorResult.class);
            regex(errorRegex);

            return self();
        }

        /** Configure the test case to expect a FilterResult matching the given regex string.
         * @param filterRegex the regex to match against the filter cause
         * @return this builder */
        public B expectFilter(String filterRegex) { return expectFilter(Pattern.compile(filterRegex)); }

        /** Configure the test case to expect a FilterResult matching the given pattern.
         * @param filterRegex the pattern to match against the filter cause
         * @return this builder */
        public B expectFilter(Pattern filterRegex) {
            expectedResultType(FilterResult.class);
            regex(filterRegex);

            return self();
        }
    }
}
