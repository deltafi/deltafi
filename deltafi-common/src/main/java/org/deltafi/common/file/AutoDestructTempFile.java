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