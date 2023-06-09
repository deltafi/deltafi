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

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceInfoTest {

    static final String FILENAME = "filename";
    static final String FLOW = "flow";

    @Test
    void getMetadataMap() {
        SourceInfo sut = new SourceInfo(FILENAME, FLOW, Map.of("foo", "bar", "baz", "bam"));

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
        SourceInfo sut = new SourceInfo(FILENAME, FLOW, new HashMap<>(Map.of("foo", "bar", "baz", "bam")));

        Map<String, String> addedMap = Map.of("larry", "1", "moe", "2", "curly", "3");

        sut.addMetadata(addedMap);

        Map<String, String> expected = new HashMap<>(sut.getMetadata());
        expected.putAll(addedMap);
        assertEquals(expected, sut.getMetadata());
    }
}