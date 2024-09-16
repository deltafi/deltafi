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
package org.deltafi.common.resource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceTest {
    @Test
    public void readsResource() throws IOException {
        assertEquals("This is the content", Resource.read("/resource/resource.txt"));
    }

    @Test
    public void readsResourceUtf8() throws IOException {
        assertEquals("This is the content €", Resource.read("/resource/resource-utf-8.txt", StandardCharsets.UTF_8));
    }

    @Test
    public void badEncoding() throws IOException {
        assertEquals("This is the content â\u0082¬", Resource.read("/resource/resource-utf-8.txt",
                StandardCharsets.ISO_8859_1));
    }
}
