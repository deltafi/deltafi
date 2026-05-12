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
package org.deltafi.common.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathScannerTest {

    private static final String DIR = "scanner-test/flows";

    @Test
    void listsFilesUnderDirectoryFromFileUrl() {
        List<URL> resources = ClasspathScanner.list(getClass().getClassLoader(), DIR);

        assertThat(resources).extracting(ClasspathScanner::filenameOf)
                .containsExactlyInAnyOrder("flow1.json", "flow2.yaml", "variables.yaml");
    }

    @Test
    void listReturnsEmptyForMissingDirectory() {
        assertThat(ClasspathScanner.list(getClass().getClassLoader(), "no-such-directory")).isEmpty();
    }

    @Test
    void listsFilesUnderDirectoryFromJarUrl(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Path jarPath = tmp.resolve("flows.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("flows/"));
            jar.closeEntry();
            writeEntry(jar, "flows/flow1.json", "{\"name\":\"flow1\"}");
            writeEntry(jar, "flows/flow2.yaml", "name: flow2");
            writeEntry(jar, "flows/variables.yaml", "name: vars");
            jar.putNextEntry(new JarEntry("flows/nested/"));
            jar.closeEntry();
            writeEntry(jar, "flows/nested/inner.yaml", "name: inner");
        }
        URL[] urls = {jarPath.toUri().toURL()};
        try (URLClassLoader jarLoader = new URLClassLoader(urls, null)) {
            List<URL> resources = ClasspathScanner.list(jarLoader, "flows");

            assertThat(resources).extracting(ClasspathScanner::filenameOf)
                    .containsExactlyInAnyOrder("flow1.json", "flow2.yaml", "variables.yaml");

            URL flow1 = resources.stream()
                    .filter(u -> "flow1.json".equals(ClasspathScanner.filenameOf(u)))
                    .findFirst().orElseThrow();
            try (InputStream in = flow1.openStream()) {
                assertThat(new String(in.readAllBytes())).isEqualTo("{\"name\":\"flow1\"}");
            }
        }
    }

    private static void writeEntry(JarOutputStream jar, String name, String content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content.getBytes());
        jar.closeEntry();
    }
}
