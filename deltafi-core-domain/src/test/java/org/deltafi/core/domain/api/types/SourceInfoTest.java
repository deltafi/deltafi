/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.api.types;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SourceInfoTest {

    static final String FILENAME = "filename";
    static final String FLOW = "flow";

    @Test
    void getMetadataMap() {
        SourceInfo sut = new SourceInfo(FILENAME, FLOW, List.of(new KeyValue("foo", "bar"),
                new KeyValue("baz", "bam")));

        Map<String, String> map = sut.getMetadataAsMap();
        assertThat(map, hasEntry("foo", "bar"));
        assertThat(map, hasEntry("baz", "bam"));
        assertThat(map.size(), equalTo(2));
        assertThat(sut.getMetadata("foo"), equalTo("bar"));
        assertThat(sut.getMetadata("boo"), equalTo(null));
        assertThat(sut.getMetadata("boo","bunny"), equalTo("bunny"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addMetadataMap() {
        SourceInfo sut = new SourceInfo(FILENAME, FLOW, new ArrayList<>(List.of(new KeyValue("foo", "bar"),
                new KeyValue("baz", "bam"))));

        Map<String, String> initialMap = sut.getMetadataAsMap();
        Map<String, String> addedMap = Map.of("larry", "1", "moe", "2", "curly", "3");

        sut.addMetadata(addedMap);

        Map<String, String> result = sut.getMetadataAsMap();
        assertThat(result.entrySet(), (Matcher)hasItems(addedMap.entrySet().toArray()));
        assertThat(result.entrySet(), (Matcher)hasItems(initialMap.entrySet().toArray()));
    }
}