/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.json;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class NdjsonDetectorTest {
    @Test
    void detectsNdjson() throws IOException {
        Assertions.assertTrue(runTest("""
                { "a": "1", "b": {"c": "2", "d": "3"}}
                """));

        Assertions.assertTrue(runTest("""
                { "a": "1", "b": {"c": "2", "d": "3"}}
                { "e": "4", "f": {"g": "5", "h": "6"}}
                """));

        Assertions.assertFalse(runTest("not ndjson"));
    }

    private boolean runTest(String input) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(input.getBytes())) {
            return NdjsonDetector.isNdjson(byteArrayInputStream);
        }
    }
}
