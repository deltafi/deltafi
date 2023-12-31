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
package org.deltafi.common.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceInfoTest {

    static final String FILENAME = "filename";
    static final String FLOW = "flow";

    @Test
    void getMetadataMap() {
        SourceInfo sut = new SourceInfo(FILENAME, FLOW, Map.of("foo", "bar", "baz", "bam"),
                ProcessingType.NORMALIZATION);

        Map<String, String> map = sut.getMetadata();
        assertThat(map, hasEntry("foo", "bar"));
        assertThat(map, hasEntry("baz", "bam"));
        assertThat(map.size(), equalTo(2));
        assertThat(sut.getMetadata("foo"), equalTo("bar"));
        assertThat(sut.getMetadata("boo"), equalTo(null));
        assertThat(sut.getMetadata("boo","bunny"), equalTo("bunny"));
    }

    @Test
    void addMetadataMap() {
        SourceInfo sut = new SourceInfo(FILENAME, FLOW, new HashMap<>(Map.of("foo", "bar", "baz", "bam")),
                ProcessingType.NORMALIZATION);

        Map<String, String> addedMap = Map.of("larry", "1", "moe", "2", "curly", "3");

        sut.addMetadata(addedMap);

        Map<String, String> expected = new HashMap<>(sut.getMetadata());
        expected.putAll(addedMap);
        assertEquals(expected, sut.getMetadata());
    }

    @ParameterizedTest
    @MethodSource
    void testNormalizedFilename(SourceInfo sourceInfo) {
        assertEquals("TeSt", sourceInfo.getFilename());
        assertEquals("test", sourceInfo.getNormalizedFilename());
    }

    private static Stream<Arguments> testNormalizedFilename() {
        String filename = "TeSt";
        SourceInfo sourceInfo1 = new SourceInfo();
        sourceInfo1.setFilename(filename);
        return Stream.of(
                Arguments.of(sourceInfo1),
                Arguments.of(SourceInfo.builder().filename(filename).build()),
                Arguments.of(new SourceInfo(filename, "flow", Map.of("a", "b"), ProcessingType.NORMALIZATION)),
                Arguments.of(new SourceInfo(filename, "flow", Map.of("a", "b"), ProcessingType.TRANSFORMATION)));
    }
}