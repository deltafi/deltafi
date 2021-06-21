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