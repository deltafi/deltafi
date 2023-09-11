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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.format.FormatResult;

import java.nio.charset.Charset;
import java.util.function.Predicate;

/**
 * Assertions for FormatResults
 */
public class FormatResultAssert extends MetadataResultAssert<FormatResultAssert, FormatResult> {

    public FormatResultAssert(FormatResult formatResult) {
        super(formatResult, FormatResultAssert.class);
    }

    /**
     * Create a new FormatResultAssert with the given result
     * @param formatResult to validate
     * @return new FormatResultAssert
     */
    public static FormatResultAssert assertThat(FormatResult formatResult) {
        return new FormatResultAssert(formatResult);
    }

    /**
     * Verify that the formatted content satisfies the given predicate
     * @param contentMatcher used to validate the formatted content
     * @return this
     */
    public FormatResultAssert formattedContentMatches(Predicate<ActionContent> contentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).matches(contentMatcher);
        return this;
    }

    /**
     * Load the formatted content into a byte array and verify
     * that it is equal to the given byte array
     * @param expected formatted content as a byte array
     * @return this
     */
    public FormatResultAssert formattedContentBytesEquals(byte[] expected) {
        isNotNull();
        byte[] content = actual.getContent() != null ? actual.getContent().loadBytes() : null;
        Assertions.assertThat(content).isEqualTo(expected);
        return this;
    }

    /**
     * Load the formatted content into a string using the default charset
     * and verify it is equal to the expected string
     * @param expected formatted content as a string
     * @return this
     */
    public FormatResultAssert formattedContentEquals(String expected) {
        return this.formattedContentEquals(expected, Charset.defaultCharset());
    }

    /**
     * Load the formatted content into a string using the given charset
     * and verify it is equal to the expected string
     * @param expected formatted content as a string
     * @param charset used when reading the content as a string
     * @return this
     */
    public FormatResultAssert formattedContentEquals(String expected, Charset charset) {
        isNotNull();
        String content = actual.getContent() != null ? actual.getContent().loadString(charset) : null;
        Assertions.assertThat(content).isEqualTo(expected);
        return this;
    }

    /**
     * Verify that the formatted content has the given name, mediaType and
     * loading the content into a string with the default charset is equal to
     * the expected content string
     * @param name
     * @param content
     * @param mediaType
     * @return
     */
    public FormatResultAssert hasFormattedContent(String name, String content, String mediaType) {
        isNotNull();
        ContentAssert.assertThat(actual.getContent())
                .hasName(name)
                .loadStringIsEqualTo(content)
                .hasMediaType(mediaType);

        return this;
    }

}
