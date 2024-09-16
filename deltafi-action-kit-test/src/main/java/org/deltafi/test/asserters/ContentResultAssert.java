/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import java.util.Map;
import java.util.function.Predicate;

/**
 * Base class that provides assertions on the list of content in a result
 * @param <A> The class that extended this
 * @param <T> The expected result type
 */
public abstract class ContentResultAssert<A extends AbstractAssert<A, T>, T extends ContentResult<T>> extends MetadataResultAssert<A, T> {

    protected ContentResultAssert(T metadataResult, Class<?> selfType) {
        super(metadataResult, selfType);
    }

    /**
     * Verify that at least one ActionContent in the results content list
     * satisfies the given predicate
     * @param contentMatcher predicate used to find the matching content
     * @return this
     */
    public A hasContentMatching(Predicate<ActionContent> contentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).anyMatch(contentMatcher);
        return myself;
    }

    /**
     * Verify that the ActionContent at the given index of the content list
     * satisfies the given predicate
     * @param index of the ActionContent to check
     * @param contentMatcher predicate used to validate the ActionContent
     * @return this
     */
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

    /**
     * Load each ActionContent in the content list of the result as bytes and put them in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing expected content bytes for each ActionContent in the result
     * @return this
     */
    public A contentLoadBytesEquals(List<byte[]> values) {
        if (values == null) {
            Assertions.assertThat(actual.getContent()).isNull();
        } else {
            contentIsNotNull();
            int i = 0;
            for (ActionContent content : actual.getContent()) {
                Assertions.assertThat(content.loadBytes()).isEqualTo(values.get(i++));
            }
        }

        return myself;
    }

    /**
     * Load each ActionContent in the content list of the result as string using the default charset and put them in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing expected content string for each ActionContent in the result
     * @return this
     */
    public A contentLoadStringEquals(List<String> values) {
        return contentLoadStringEquals(values, Charset.defaultCharset());
    }

    /**
     * Load each ActionContent in the content list of the result as string using the given charset and put them in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing expected content string for each ActionContent in the result
     * @param charset used when laoding each content as a string
     * @return this
     */
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

    /**
     * Verify the size of the content list in the result
     * @param count expected content list size
     * @return this
     */
    public A hasContentCount(int count) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).hasSize(count);
        return myself;
    }

    /**
     * Verify that the content list in the result is not null
     * @return this
     */
    public A contentIsNotNull() {
        isNotNull();
        Assertions.assertThat(actual.getContent()).isNotNull();
        return myself;
    }

    /**
     * Verify that the result includes the key and value in the annotation map
     * @param key to search for
     * @param value that should be set for the key
     * @return this
     */
    public A addedAnnotation(String key, String value) {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).containsEntry(key, value);
        return myself;
    }

    /**
     * Verify that the result include all the given annotations
     * @param annotations that should be included in the result
     * @return this
     */
    public A addedAnnotations(Map<String, String> annotations) {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).containsAllEntriesOf(annotations);
        return myself;
    }

    /**
     * Verify that no annotations were added
     * @return this
     */
    public A annotationsIsEmpty() {
        isNotNull();
        Assertions.assertThat(actual.getAnnotations()).isEmpty();
        return myself;
    }

}
