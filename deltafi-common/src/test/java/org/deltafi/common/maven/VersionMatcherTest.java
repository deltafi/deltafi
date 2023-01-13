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
package org.deltafi.common.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionMatcherTest {
    @Test
    public void exactMatch() {
        assertTrue(VersionMatcher.matches("1.0.0", "1.0.0"));
    }

    @Test
    public void newerInstalled() {
        assertFalse(VersionMatcher.matches("1.0.1", "1.0.0"));
    }

    @Test
    public void inRange() {
        assertTrue(VersionMatcher.matches("1.3.2.1", "[1.0,2.0)"));
    }

    @Test
    public void rangeEdgeCase() {
        assertFalse(VersionMatcher.matches("2.0", "[1.0,2.0)"));
    }

    @Test
    public void extraText() {
        assertTrue(VersionMatcher.matches("v2.0.2+9a7b0bc", "[v2.0,v3.0)"));
    }

    @Test
    public void dateVersion() {
        assertTrue(VersionMatcher.matches("RELEASE.2021-02-14T04-01-33Z", "[RELEASE.2021-01, RELEASE.2021-03)"));
        assertFalse(VersionMatcher.matches("RELEASE.2021-02-14T04-01-33Z", "[RELEASE.2021-01, RELEASE.2021-02)"));
    }
}
