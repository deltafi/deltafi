/**
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
import org.deltafi.actionkit.action.content.ActionContent;

import java.nio.charset.Charset;

public class ContentAssert extends AbstractAssert<ContentAssert, ActionContent> {

    public ContentAssert(ActionContent content) {
        super(content, ContentAssert.class);
    }

    public static ContentAssert assertThat(ActionContent content) {
        return new ContentAssert(content);
    }

    public ContentAssert hasName(String name) {
        isNotNull();
        Assertions.assertThat(actual.getName()).isEqualTo(name);
        return this;
    }

    public ContentAssert hasSize(long size) {
        isNotNull();
        Assertions.assertThat(actual.getSize()).isEqualTo(size);
        return this;
    }

    public ContentAssert hasMediaType(String mediaType) {
        isNotNull();
        Assertions.assertThat(actual.getMediaType()).isEqualTo(mediaType);
        return this;
    }

    public ContentAssert hasSegmentCount(int count) {
        isNotNull();
        Assertions.assertThat(actual.getContent().getSegments()).hasSize(count);
        return this;
    }

    public ContentAssert loadBytesIsEqualTo(byte[] bytes) {
        isNotNull();
        Assertions.assertThat(actual.loadBytes()).isEqualTo(bytes);
        return this;
    }

    public ContentAssert loadStringIsEqualTo(String value) {
        return loadStringIsEqualTo(value, Charset.defaultCharset());
    }

    public ContentAssert loadStringIsEqualTo(String value, Charset charset) {
        isNotNull();
        Assertions.assertThat(actual.loadString(charset)).isEqualTo(value);
        return this;
    }
}
