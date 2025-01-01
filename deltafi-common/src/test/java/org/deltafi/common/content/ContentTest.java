/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.content;

import org.deltafi.common.types.Content;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ContentTest {
    static final String NAME = "name";
    static final String MEDIA_TYPE = "text/plain";
    static final UUID DID_A = UUID.randomUUID();
    static final UUID DID_B = UUID.randomUUID();
    static final UUID DID_C = UUID.randomUUID();
    static final UUID UUID_A = UUID.randomUUID();
    static final UUID UUID_B = UUID.randomUUID();
    static final UUID UUID_C = UUID.randomUUID();
    static final Segment SEGMENT_A = new Segment(UUID_A, 0, 500, DID_A);
    static final Segment SEGMENT_B = new Segment(UUID_B, 1000, 1000, DID_B);
    static final Segment SEGMENT_C = new Segment(UUID_C, 0, 500, DID_C);
    static final List<Segment> SEGMENT_LIST = List.of(SEGMENT_A, SEGMENT_B, SEGMENT_C);
    static final Content CONTENT = new Content(NAME, MEDIA_TYPE, SEGMENT_LIST);

    @Test
    void testsubcontentOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> CONTENT.subcontent(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> CONTENT.subcontent(1, -1));
        assertThrows(IllegalArgumentException.class, () -> CONTENT.subcontent(5, 1996));
    }

    @Test
    void testsubcontentZeroSize() {
        assertEquals(new Content(NAME, MEDIA_TYPE, Collections.emptyList()),
                CONTENT.subcontent(50, 0));
    }

    @Test
    void testsubcontentAll() {
        assertEquals(CONTENT, CONTENT.subcontent(0, 2000));
    }

    @Test
    void testsubcontentPartial() {
        Segment subSegmentA = new Segment(UUID_A, 250, 250, DID_A);
        Segment subSegmentC = new Segment(UUID_C, 0, 100, DID_C);
        assertEquals(new Content(NAME, MEDIA_TYPE, List.of(subSegmentA, SEGMENT_B, subSegmentC)),
                CONTENT.subcontent(250, 1350));
    }

    @Test
    void testObjectNames() {
        Segment subSegmentA = new Segment(UUID_A, 250, 250, DID_A);
        Segment subSegmentC = new Segment(UUID_C, 0, 100, DID_C);
        Content c = new Content(NAME, MEDIA_TYPE, List.of(subSegmentA, SEGMENT_B, subSegmentC));
        List<String> objectNames = c.objectNames();
        assertEquals(3, objectNames.size());
        assertTrue(objectNames.containsAll(List.of(
                makeObjectName(DID_A, UUID_A),
                makeObjectName(DID_B, UUID_B),
                makeObjectName(DID_C, UUID_C))));
    }

    private String makeObjectName(UUID did, UUID uuid) {
        return did.toString().substring(0, 3) + "/" + did + "/" + uuid;
    }

}
