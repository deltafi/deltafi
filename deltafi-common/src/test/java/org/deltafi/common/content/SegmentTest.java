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
package org.deltafi.common.content;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SegmentTest {
    @Test
    void testCalculateTotalSize() {
        // Test case 1: Empty list of segments
        Set<Segment> emptySet = new HashSet<>();
        assertEquals(0, Segment.calculateTotalSize(emptySet));

        // Test case 2: List with one segment
        Segment segment1 = new Segment("uuid1", 0, 10, "did");
        Set<Segment> oneSegmentList = new HashSet<>(List.of(segment1));
        assertEquals(10, Segment.calculateTotalSize(oneSegmentList));

        // Test case 3: List with two non-overlapping segments with the same objectName
        Segment segment2 = new Segment("uuid1", 20, 5, "did");
        Set<Segment> twoNonOverlappingSegmentsList = new HashSet<>(Arrays.asList(segment1, segment2));
        assertEquals(15, Segment.calculateTotalSize(twoNonOverlappingSegmentsList));

        // Test case 4: List with two overlapping segments with the same objectName
        Segment segment3 = new Segment("uuid1", 5, 15, "did");
        Set<Segment> twoOverlappingSegmentsList = new HashSet<>(Arrays.asList(segment1, segment3));
        assertEquals(20, Segment.calculateTotalSize(twoOverlappingSegmentsList));

        // Test case 5: List with three segments, two overlapping with the same objectName
        Segment segment4 = new Segment("uuid1", 0, 5, "did");
        Set<Segment> threeSegmentsList = new HashSet<>(Arrays.asList(segment1, segment3, segment4));
        assertEquals(20, Segment.calculateTotalSize(threeSegmentsList));

        // Test case 6: List with multiple segments, some overlapping with the same objectName
        Segment segment5 = new Segment("uuid1", 25, 10, "did");
        Segment segment6 = new Segment("uuid2", 10, 20, "did");
        Segment segment7 = new Segment("uuid2", 15, 10, "did");
        Set<Segment> multipleSegmentsList = new HashSet<>(Arrays.asList(segment1, segment2, segment3, segment4, segment5, segment6, segment7));
        assertEquals(55, Segment.calculateTotalSize(multipleSegmentsList));
    }
}
