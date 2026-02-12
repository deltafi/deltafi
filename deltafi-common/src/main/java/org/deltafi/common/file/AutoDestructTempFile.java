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
package org.deltafi.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A temporary file that deletes itself when closed. Implements {@link AutoCloseable}
 * for use in try-with-resources blocks.
 */
public class AutoDestructTempFile implements AutoCloseable {
    private final Path file;

    /**
     * Creates an empty temporary file with no prefix or suffix.
     * @throws IOException if the temporary file cannot be created
     */
    public AutoDestructTempFile() throws IOException {
        this(null, null);
    }

    /**
     * Creates an empty temporary file with the given prefix and suffix.
     * @param prefix the prefix for the temporary file name, may be null
     * @param postfix the suffix for the temporary file name, may be null
     * @throws IOException if the temporary file cannot be created
     */
    public AutoDestructTempFile(String prefix, String postfix) throws IOException {
        file = Files.createTempFile(prefix, postfix);
    }

    /**
     * Creates a temporary file populated with data from the given input stream.
     * @param in the input stream to copy into the temporary file
     * @param prefix the prefix for the temporary file name, may be null
     * @param postfix the suffix for the temporary file name, may be null
     * @throws IOException if the temporary file cannot be created or the stream cannot be read
     */
    public AutoDestructTempFile(final InputStream in, String prefix, String postfix) throws IOException {
        this(prefix, postfix);
        Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Creates a temporary file populated with data from the given input stream,
     * using no prefix or suffix.
     * @param in the input stream to copy into the temporary file
     * @throws IOException if the temporary file cannot be created or the stream cannot be read
     */
    public AutoDestructTempFile(final InputStream in) throws IOException {
        this(in, null, null);
    }

    /**
     * Returns the path to the temporary file.
     * @return the path to the temporary file
     */
    public Path getPath() { return file; }

    @Override
    public void close() throws IOException {
        Files.delete(file);
    }
}