package org.deltafi.actionkit.service;

import org.deltafi.dgs.generated.types.ObjectReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InMemoryContentServiceTest {
    InMemoryContentService contentService = new InMemoryContentService();

    @BeforeEach
    void setup() {
        contentService.clear();
    }

    @Test
    void testStorage() {
        byte[] object = "the object".getBytes();
        ObjectReference reference = ObjectReference.newBuilder()
                .name("hi")
                .bucket("hello")
                .offset(0)
                .size(object.length).build();
        contentService.putObject(reference, object);

        assertEquals(new String(object), new String(contentService.retrieveContent(reference)));
    }

    @Test
    void testRetrievePortion() {
        byte[] object = "the object".getBytes();
        ObjectReference reference = ObjectReference.newBuilder()
                .name("hi")
                .bucket("hello")
                .offset(1)
                .size(object.length-1).build();
        contentService.putObject(reference, object);

        assertEquals(new String(object).substring(1, object.length-1), new String(contentService.retrieveContent(reference)));
    }
}
