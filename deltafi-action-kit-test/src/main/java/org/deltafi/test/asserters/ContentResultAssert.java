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

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.content.ActionContent;

import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Predicate;

public abstract class ContentResultAssert<A extends AbstractAssert<A, T>, T extends ContentResult<T>> extends MetadataResultAssert<A, T> {

    protected ContentResultAssert(T metadataResult, Class<?> selfType) {
        super(metadataResult, selfType);
    }

    public A hasContentMatching(Predicate<ActionContent> contentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).anyMatch(contentMatcher);
        return myself;
    }

    public A hasContentMatchingAt(int index, Predicate<ActionContent> contentMatcher) {
        isNotNull();
        if (actual.getContent() == null || actual.getContent().size() - 1  < index) {
            String contentSize = actual.getContent() == null ? "content list  is null" : "content list has size " + actual.getContent().size();
            failWithMessage("There is no content at index %s (%s)", index, contentSize);
            return myself;
        }

        ActionContent actionContent = actual.getContent().get(index);
        Assertions.assertThat(actionContent).matches(contentMatcher);
        return myself;
    }

    public A contentLoadBytesEquals(List<byte[]> values) {
        if (values == null) {
            Assertions.assertThat(actual.getContent()).isNull();
        } else {
            contentIsNotNull();
            List<byte[]> byteList = actual.getContent().stream()
                    .map(ActionContent::loadBytes)
                    .toList();

            Assertions.assertThat(byteList).isEqualTo(values);
        }

        return myself;
    }

    public A contentLoadStringEquals(List<String> values) {
        return contentLoadStringEquals(values, Charset.defaultCharset());
    }

    public A contentLoadStringEquals(List<String> values, Charset charset) {
        if (values == null) {
            Assertions.assertThat(actual.getContent()).isNull();
        } else {
            contentIsNotNull();
            List<String> content = actual.getContent().stream()
                    .map(actionContent -> actionContent.loadString(charset))
                    .toList();

            Assertions.assertThat(content).isEqualTo(values);
        }
        return myself;
    }

    public A hasContentCount(int count) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).hasSize(count);
        return myself;
    }

    public A contentIsNotNull() {
        isNotNull();
        Assertions.assertThat(actual.getContent()).isNotNull();
        return myself;
    }
}
