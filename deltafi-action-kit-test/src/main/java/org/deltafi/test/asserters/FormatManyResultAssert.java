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
import org.deltafi.actionkit.action.format.FormatManyResult;
import org.deltafi.actionkit.action.format.FormatResult;

import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Predicate;

/**
 * Assertions for FormatManyResults
 */
public class FormatManyResultAssert extends ResultAssert<FormatManyResultAssert, FormatManyResult> {
    public FormatManyResultAssert(FormatManyResult formatManyResult) {
        super(formatManyResult, FormatManyResultAssert.class);
    }

    /**
     * Create a new FormatManyResultAssert with the given result
     * @param formatManyResult to validate
     * @return a new FormatManyResultAssert
     */
    public static FormatManyResultAssert assertThat(FormatManyResult formatManyResult) {
        return new FormatManyResultAssert(formatManyResult);
    }

    /**
     * Verify that at least one child FormatResult in the results childFormatResult list
     * satisfies the given predicate
     * @param childMatcher predicate used to find the matching child
     * @return this
     */
    public FormatManyResultAssert hasChildMatching(Predicate<FormatResult> childMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getChildFormatResults()).anyMatch(childMatcher);
        return this;
    }

    /**
     * Verify that the childFormatResult list has a size equal to the given count
     * @param count expected size
     * @return this
     */
    public FormatManyResultAssert hasChildrenSize(int count) {
        isNotNull();
        Assertions.assertThat(actual.getChildFormatResults()).hasSize(count);
        return this;
    }

    /**
     * Load the formatted content as bytes from each child result and put the byte arrays in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing the expected formatted content bytes from each child result
     * @return this
     */
    public FormatManyResultAssert hasFormattedContentEqualToBytes(List<byte[]> values) {
        if (values == null) {
            Assertions.assertThat(actual.getChildFormatResults()).isNull();
        } else {
            List<byte[]> content = actual.getChildFormatResults().stream()
                    .map(this::getContentAsBytes).toList();

            Assertions.assertThat(content).isEqualTo(values);
        }
        return this;
    }

    /**
     * Load the formatted content as strings using the {@code Charset.defaultCharset()} from each child result and put the strings in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing the expected formatted content string from each child result
     * @return this
     */
    public FormatManyResultAssert hasFormattedContentEqualTo(List<String> values) {
        return this.hasFormattedContentEqualTo(values, Charset.defaultCharset());
    }

    /**
     * Load the formatted content as strings using the given charset from each child result and put the strings in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing the expected formatted content string from each child result
     * @return this
     */
    public FormatManyResultAssert hasFormattedContentEqualTo(List<String> values, Charset charset) {
        if (values == null) {
            Assertions.assertThat(actual.getChildFormatResults()).isNull();
        } else {
            List<String> content = actual.getChildFormatResults().stream()
                    .map(formatResult -> getContentAsString(formatResult, charset)).toList();

            Assertions.assertThat(content).isEqualTo(values);
        }
        return this;
    }

    private byte[] getContentAsBytes(FormatResult result) {
        return hasContent(result) ? result.getContent().loadBytes() : null;
    }

    private String getContentAsString(FormatResult result, Charset charset) {
        return hasContent(result) ? result.getContent().loadString(charset) : null;
    }

    private boolean hasContent(FormatResult result) {
        return result != null && result.getContent() != null;
    }
}
