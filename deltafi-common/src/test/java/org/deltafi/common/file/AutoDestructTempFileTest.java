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
package org.deltafi.common.file;

import org.hamcrest.text.MatchesPattern;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AutoDestructTempFileTest {

    @Test
    void getPath() throws IOException {
        byte[] content = "Hello world.".getBytes(StandardCharsets.UTF_8);

        InputStream is = new ByteArrayInputStream(content);
        AutoDestructTempFile sut = new AutoDestructTempFile(is,"foo", "bar");
        assertThat(sut.getPath().getFileName().toString(), MatchesPattern.matchesPattern("foo.*bar"));
        assertArrayEquals(content, Files.readAllBytes(sut.getPath()));
    }

    @Test
    void close() throws IOException {
        AutoDestructTempFile sut = new AutoDestructTempFile();
        assertTrue(sut.getPath().toFile().exists());
        sut.close();
        assertFalse(sut.getPath().toFile().exists());
    }

    @Test
    void resourceTry() throws IOException {
        InputStream is = new ByteArrayInputStream("Hello world.".getBytes(StandardCharsets.UTF_8));
        Path path;
        try(AutoDestructTempFile sut = new AutoDestructTempFile(is)) {
            path = sut.getPath();
            assertTrue(sut.getPath().toFile().exists());
        }
        assertFalse(path.toFile().exists());
    }
}