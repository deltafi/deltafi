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

public class FormatResultAssert extends MetadataResultAssert<FormatResultAssert, FormatResult> {

    public FormatResultAssert(FormatResult formatResult) {
        super(formatResult, FormatResultAssert.class);
    }

    public static FormatResultAssert assertThat(FormatResult formatResult) {
        return new FormatResultAssert(formatResult);
    }

    public FormatResultAssert formattedContentMatches(Predicate<ActionContent> contentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).matches(contentMatcher);
        return this;
    }

    public FormatResultAssert formattedContentBytesEquals(byte[] expected) {
        isNotNull();
        byte[] content = actual.getContent() != null ? actual.getContent().loadBytes() : null;
        Assertions.assertThat(content).isEqualTo(expected);
        return this;
    }

    public FormatResultAssert formattedContentEquals(String expected) {
        return this.formattedContentEquals(expected, Charset.defaultCharset());
    }

    public FormatResultAssert formattedContentEquals(String expected, Charset charset) {
        isNotNull();
        String content = actual.getContent() != null ? actual.getContent().loadString(charset) : null;
        Assertions.assertThat(content).isEqualTo(expected);
        return this;
    }

}
