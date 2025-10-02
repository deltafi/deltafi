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
package org.deltafi.common.types.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyValueChecksTest {

    @Test
    void testValidation() {
        // missing name and data
        KeyValueChecks testObj = KeyValueChecks.builder()
                .containsKeys(List.of("", "2", ""))
                .keyValueMatchers(List.of(
                        new KeyValueMatcher("", "", null),
                        new KeyValueMatcher("name", null, true)
                )).build();
        List<String> expectedErrors = List.of(
                "KeyValueChecks contains an invalid key",
                "KeyValueMatcher missing name",
                "KeyValueMatcher non-empty pattern value is required",
                "KeyValueMatcher value is null"
        );
        assertEquals(expectedErrors, testObj.validate());

        testObj = KeyValueChecks.builder()
                .containsKeys(List.of("1", "2", "3"))
                .keyValueMatchers(List.of(
                        new KeyValueMatcher("name1", "val1", true),
                        new KeyValueMatcher("name2", "1.*", false)
                )).build();
        assertTrue(testObj.validate().isEmpty());
    }

    @Test
    void testMatchesContainsKeys() {
        KeyValueChecks testObj = KeyValueChecks.builder()
                .containsKeys(List.of("key1", "key2"))
                .build();
        assertTrue(testObj.matches(Map.of(
                "key1", "val1",
                "key2", "2222",
                "key3", ""), "label").isEmpty());

        List<String> expectedErrors = List.of(
                "label is missing key: key1",
                "label is missing key: key2");
        assertEquals(expectedErrors, testObj.matches(Map.of("key3", ""), "label"));
    }

    @Test
    void testKeyValueMatchers() {
        KeyValueChecks testObj = KeyValueChecks.builder()
                .keyValueMatchers(List.of(
                        new KeyValueMatcher("key1", "val1", true)
                )).build();
        assertTrue(testObj.matches(Map.of(
                "key1", "val1",
                "key2", "2222",
                "key3", ""), "label").isEmpty());

        testObj = KeyValueChecks.builder()
                .containsKeys(List.of("key1", "key2"))
                .keyValueMatchers(List.of(
                        new KeyValueMatcher("key1", "val1", true),
                        new KeyValueMatcher("key2", "2+", false),
                        new KeyValueMatcher("key3", "", true),
                        new KeyValueMatcher("key3", "^$", false)
                )).build();
        assertTrue(testObj.matches(Map.of(
                "key1", "val1",
                "key2", "2222",
                "key3", ""), "label").isEmpty());

        List<String> expectedErrors = List.of(
                "label value of key key1 (wrongValue) does not match expected value: (val1)",
                "label value of key key2 (abcd) does not match pattern: (2+)",
                "label is missing key/name: key3",
                "label is missing key/name: key3");
        assertEquals(expectedErrors, testObj.matches(Map.of(
                "key1", "wrongValue",
                "key2", "abcd",
                "key4", "4"), "label"));
    }
}
