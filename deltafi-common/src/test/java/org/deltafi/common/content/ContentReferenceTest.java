/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ContentReferenceTest {
    final static String MEDIA_TYPE = "text/plain";
    final static Segment SEGMENT_A = new Segment("a", 0, 500, "a");
    final static Segment SEGMENT_B = new Segment("b", 1000, 1000, "b");
    final static Segment SEGMENT_C = new Segment("c", 0, 500, "c");
    final static List<Segment> SEGMENT_LIST = List.of(SEGMENT_A, SEGMENT_B, SEGMENT_C);
    final static ContentReference CONTENT_REFERENCE = new ContentReference(MEDIA_TYPE, SEGMENT_LIST);

    @Test
    void testSubreferenceOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> CONTENT_REFERENCE.subreference(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> CONTENT_REFERENCE.subreference(1, -1));
        assertThrows(IllegalArgumentException.class, () -> CONTENT_REFERENCE.subreference(5, 1996));
    }

    @Test
    void testSubreferenceZeroSize() {
        assertEquals(new ContentReference(MEDIA_TYPE, Collections.emptyList()),
                CONTENT_REFERENCE.subreference(50, 0));
    }

    @Test void testSubreferenceAll() {
        assertEquals(CONTENT_REFERENCE, CONTENT_REFERENCE.subreference(0, 2000));
    }

    @Test void testSubreferencePartial() {
        Segment subSegmentA = new Segment("a", 250, 250, "a");
        Segment subSegmentC = new Segment("c", 0, 100, "c");
        assertEquals(new ContentReference(MEDIA_TYPE, List.of(subSegmentA, SEGMENT_B, subSegmentC)),
                CONTENT_REFERENCE.subreference(250, 1350));
    }
}
