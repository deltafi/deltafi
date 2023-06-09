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
package org.deltafi.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AutoDestructTempFile implements AutoCloseable {
    private final Path file;

    public AutoDestructTempFile() throws IOException {
        this(null, null);
    }

    public AutoDestructTempFile(String prefix, String postfix) throws IOException {
        file = Files.createTempFile(prefix, postfix);
    }

    public AutoDestructTempFile(final InputStream in, String prefix, String postfix) throws IOException {
        this(prefix, postfix);
        Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public AutoDestructTempFile(final InputStream in) throws IOException {
        this(in, null, null);
    }

    public Path getPath() { return file; }

    @Override
    public void close() throws IOException {
        Files.delete(file);
    }
}