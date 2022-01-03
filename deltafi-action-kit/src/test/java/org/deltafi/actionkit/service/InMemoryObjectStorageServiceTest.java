package org.deltafi.actionkit.service;

import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class InMemoryObjectStorageServiceTest {
    private final InMemoryObjectStorageService inMemoryObjectStorageService = new InMemoryObjectStorageService();

    private final byte[] object = "the object".getBytes();

    @BeforeEach
    void beforeEach() throws ObjectStorageException {
        inMemoryObjectStorageService.clear();
        inMemoryObjectStorageService.putObjectAsByteArray(new ObjectReference("hello", "hi"), object);
    }

    @Test
    void testGetsObjectAsByteArray() throws ObjectStorageException, IOException {
        assertArrayEquals(object, inMemoryObjectStorageService.getObjectAsByteArray(new ObjectReference("hello", "hi")));
    }

    @Test
    void testGetsPortionOfObjectAsByteArray() throws ObjectStorageException, IOException {
        assertArrayEquals(Arrays.copyOfRange(object, 1, object.length - 1),
                inMemoryObjectStorageService.getObjectAsByteArray(new ObjectReference("hello", "hi", 1, object.length - 2)));
    }
}
