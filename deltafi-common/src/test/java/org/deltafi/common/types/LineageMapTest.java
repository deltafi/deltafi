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
package org.deltafi.common.types;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineageMapTest {

    @Test
    void testLoadAndFind() throws JsonProcessingException {
        String json = """
                {
                  "file1": {
                    "fullName": "file1",
                    "parentContentName": "parent.tar",
                    "modifiedContentName": false
                  },
                  "file2": {
                    "fullName": "file2",
                    "parentContentName": "parent.tar",
                    "modifiedContentName": false
                  },
                  "file2.zip:file2": {
                    "fullName": "file2",
                    "parentContentName": "file2.zip",
                    "modifiedContentName": true
                  },
                  "file2.zip": {
                    "fullName": "file2.zip",
                    "parentContentName": "parent.tar",
                    "modifiedContentName": false
                  }
                  }""";

        LineageMap lineageMap = new LineageMap();
        assertTrue(lineageMap.isEmpty());

        lineageMap.readMapFromString(json);
        assertFalse(lineageMap.isEmpty());
        assertEquals(4, lineageMap.size());

        assertNull(lineageMap.findEntry("blah"));
        assertNull(lineageMap.findParentEntry("blah"));

        LineageData entry = lineageMap.findEntry("file2.zip:file2");
        assertEquals("file2.zip", entry.getParentContentName());

        LineageData parent = lineageMap.findParentEntry("file2.zip:file2");
        assertEquals("parent.tar", parent.getParentContentName());

        List<String> file1Matches = lineageMap.findAllFullNameMatches("file1");
        assertEquals(1, file1Matches.size());
        assertTrue(file1Matches.contains("file1"));

        List<String> file2Matches = lineageMap.findAllFullNameMatches("file2");
        assertEquals(2, file2Matches.size());
        assertTrue(file2Matches.containsAll(List.of("file2", "file2.zip:file2")));

        assertTrue(lineageMap.findAllFullNameMatches("file3").isEmpty());

        LineageMap emptyMap = new LineageMap();
        assertTrue(emptyMap.findAllFullNameMatches("file3").isEmpty());
    }

    @Test
    void testPutAndWrite() throws JsonProcessingException {
        LineageMap lineageMap = new LineageMap();
        String key = lineageMap.add("name", "", "parent1");
        assertEquals("name", key);
        key = lineageMap.add("name", "", "parent2");
        assertEquals("parent2:name", key);
        assertEquals(2, lineageMap.size());

        String json =
                "{\"name\":{\"fullName\":\"name\",\"parentContentName\":\"parent1\",\"modifiedContentName\":false}," +
                        "\"parent2:name\":{\"fullName\":\"name\",\"parentContentName\":\"parent2\",\"modifiedContentName\":true}}";

        assertEquals(json, lineageMap.writeMapAsString());

        key = lineageMap.add("name", "", "parent2");
        String expectedPrefix = "parent2:name_";
        assertTrue(key.startsWith(expectedPrefix));
        assertEquals(expectedPrefix.length() + 7, key.length());
        assertEquals(3, lineageMap.size());
    }
}
