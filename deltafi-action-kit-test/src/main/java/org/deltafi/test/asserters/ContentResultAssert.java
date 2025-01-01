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

import org.assertj.core.api.*;
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.content.ActionContent;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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
     * Verify the size of the content list in the result.
     * @param count expected content list size
     * @return this
     */
    public A hasContentCount(int count) {
        return hasContentCount(count, "Has " + count + " content(s)");
    }

    /**
     * Verify the size of the content list in the result.
     * @param count expected content list size
     * @param description a description to include with the assertion
     * @return this
     */
    public A hasContentCount(int count, String description) {
        Assertions.assertThat(actual.getContent()).describedAs(description).hasSize(count);
        return myself;
    }

    /**
     * Verify that the ActionContent at the given index of the content list satisfies the given predicate.
     * @param index of the ActionContent to check
     * @param contentMatcher predicate used to validate the ActionContent
     * @return this
     */
    public A hasContentMatchingAt(int index, Predicate<ActionContent> contentMatcher) {
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
     * Verify content at an index.
     * @param index the index of the content to check
     * @param contentName the expected content name
     * @param mediaType the expected media type
     * @param content the expected content
     * @return this
     */
    public A hasContentMatchingAt(int index, String contentName, String mediaType, byte[] content) {
        return hasContentMatchingAt(index, contentName, mediaType, content, "Has matching content at index " + index);
    }

    /**
     * Verify content at an index.
     * @param index the index of the content to check
     * @param contentName the expected content name
     * @param mediaType the expected media type
     * @param content the expected content
     * @param description a description to include with the assertion
     * @return this
     */
    public A hasContentMatchingAt(int index, String contentName, String mediaType, byte[] content, String description) {
        if (actual.getContent() == null || index >= actual.getContent().size()) {
            String contentSize = actual.getContent() == null ? "content list  is null" :
                    "content list has size " + actual.getContent().size();

            describedAs(description);
            failWithMessage("There is no content at index %s (%s)", index, contentSize);
            return myself;
        }

        SoftAssertions softAssertions = new SoftAssertions();
        buildActionContentAsserter(contentName, mediaType, content)
                .accept(actual.getContent().get(index), softAssertions);
        try {
            softAssertions.assertAll();
        } catch (AssertionError e) {
            describedAs(description);
            failWithMessage("\nChild at index %d failed %s", index, e.getMessage());
        }
        return myself;
    }

    private BiConsumer<ActionContent, SoftAssertions> buildActionContentAsserter(String contentName, String mediaType,
            byte[] content) {
        return (actionContent, softAssertions) -> {
            softAssertions.assertThat(actionContent.getName()).describedAs("Content name matches").isEqualTo(contentName);
            softAssertions.assertThat(actionContent.getMediaType()).describedAs("Media type matches").isEqualTo(mediaType);
            softAssertions.assertThat(actionContent.loadBytes()).describedAs("Content matches").isEqualTo(content);
        };
    }

    /**
     * Verify content at an index.
     * @param index the index of the content to check
     * @param contentName the expected content name
     * @param mediaType the expected media type
     * @param content the expected content
     * @return this
     */
    public A hasContentMatchingAt(int index, String contentName, String mediaType, String content) {
        return hasContentMatchingAt(index, contentName, mediaType, content, "Has matching content at index " + index);
    }

    /**
     * Verify content at an index.
     * @param index the index of the content to check
     * @param contentName the expected content name
     * @param mediaType the expected media type
     * @param content the expected content
     * @param description a description to include with the assertion
     * @return this
     */
    public A hasContentMatchingAt(int index, String contentName, String mediaType, String content, String description) {
        if (actual.getContent() == null || index >= actual.getContent().size()) {
            String contentSize = actual.getContent() == null ? "content list  is null" :
                    "content list has size " + actual.getContent().size();

            describedAs(description);
            failWithMessage("There is no content at index %s (%s)", index, contentSize);
            return myself;
        }

        SoftAssertions softAssertions = new SoftAssertions();
        buildActionContentAsserter(contentName, mediaType, content)
                .accept(actual.getContent().get(index), softAssertions);
        try {
            softAssertions.assertAll();
        } catch (AssertionError e) {
            describedAs(description);
            failWithMessage("\nChild at index %d failed %s", index, e.getMessage());
        }
        return myself;
    }

    private BiConsumer<ActionContent, SoftAssertions> buildActionContentAsserter(String contentName, String mediaType,
            String content) {
        return (actionContent, softAssertions) -> {
            softAssertions.assertThat(actionContent.getName()).describedAs("Content name matches").isEqualTo(contentName);
            softAssertions.assertThat(actionContent.getMediaType()).describedAs("Media type matches").isEqualTo(mediaType);
            softAssertions.assertThat(actionContent.loadString()).describedAs("Content matches").isEqualTo(content);
        };
    }

    /**
     * Verify that the result includes the key and value in the annotation map
     * @param key to search for
     * @param value that should be set for the key
     * @return this
     */
    public A addedAnnotation(String key, String value) {
        return addedAnnotation(key, value, "Has annotation");
    }

    /**
     * Verify that the result includes the key and value in the annotation map
     * @param key to search for
     * @param value that should be set for the key
     * @param description a description to include with the assertion
     * @return this
     */
    public A addedAnnotation(String key, String value, String description) {
        Assertions.assertThat(actual.getAnnotations()).describedAs(description).containsEntry(key, value);
        return myself;
    }

    /**
     * Verify that the result includes all the given annotations
     * @param annotations that should be included in the result
     * @return this
     */
    public A addedAnnotations(Map<String, String> annotations) {
        return addedAnnotations(annotations, "Has annotations");
    }

    /**
     * Verify that the result includes all the given annotations
     * @param annotations that should be included in the result
     * @param description a description to include with the assertion
     * @return this
     */
    public A addedAnnotations(Map<String, String> annotations, String description) {
        Assertions.assertThat(actual.getAnnotations()).describedAs(description).containsAllEntriesOf(annotations);
        return myself;
    }

    /**
     * Verify that no annotations were added
     * @return this
     */
    public A annotationsIsEmpty() {
        return annotationsIsEmpty("Has no annotations");
    }

    /**
     * Verify that no annotations were added
     * @param description a description to include with the assertion
     * @return this
     */
    public A annotationsIsEmpty(String description) {
        Assertions.assertThat(actual.getAnnotations()).describedAs(description).isEmpty();
        return myself;
    }


    /**
     * Verify that at least one ActionContent in the results content list
     * satisfies the given predicate
     * @param contentMatcher predicate used to find the matching content
     * @return this
     * @deprecated Use {@link ContentResultAssert#hasContentMatchingAt(int, Predicate)}
     */
    @Deprecated
    public A hasContentMatching(Predicate<ActionContent> contentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getContent()).anyMatch(contentMatcher);
        return myself;
    }

    /**
     * Load each ActionContent in the content list of the result as bytes and put them in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing expected content bytes for each ActionContent in the result
     * @return this
     * @deprecated Use {@link ContentResultAssert#hasContentMatchingAt(int, String, String, byte[])}
     */
    @Deprecated
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
     * @deprecated Use {@link ContentResultAssert#hasContentMatchingAt(int, String, String, String)}
     */
    @Deprecated
    public A contentLoadStringEquals(List<String> values) {
        return contentLoadStringEquals(values, Charset.defaultCharset());
    }

    /**
     * Load each ActionContent in the content list of the result as string using the given charset and put them in a list.
     * Verify that list is equal to the list in the values parameter.
     * @param values list containing expected content string for each ActionContent in the result
     * @param charset used when laoding each content as a string
     * @return this
     * @deprecated Use {@link ContentResultAssert#hasContentMatchingAt(int, String, String, String, String)}
     */
    @Deprecated
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
     * Verify that the content list in the result is not null
     * @return this
     * @deprecated Verify content instead
     */
    @Deprecated
    public A contentIsNotNull() {
        isNotNull();
        Assertions.assertThat(actual.getContent()).isNotNull();
        return myself;
    }
}
