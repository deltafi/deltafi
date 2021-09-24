package org.deltafi.actionkit.service;

import org.deltafi.common.storage.s3.ObjectStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InMemoryObjectStorageServiceTest {
    InMemoryObjectStorageService inMemoryObjectStorageService = new InMemoryObjectStorageService();

    @BeforeEach
    void setup() {
        inMemoryObjectStorageService.clear();
    }

    @Test
    void testStorage() throws ObjectStorageException {
        byte[] object = "the object".getBytes();
        inMemoryObjectStorageService.putObject("hello", "hi", object);

        assertEquals(new String(object), new String(inMemoryObjectStorageService.getObject("hello", "hi", 0, object.length)));
    }

    @Test
    void testRetrievePortion() throws ObjectStorageException {
        byte[] object = "the object".getBytes();
        inMemoryObjectStorageService.putObject("hello", "hi", new ByteArrayInputStream(object), object.length - 1);

        assertEquals(new String(object).substring(1, object.length - 1),
                new String(inMemoryObjectStorageService.getObject("hello", "hi", 1, object.length - 2)));
    }
}
