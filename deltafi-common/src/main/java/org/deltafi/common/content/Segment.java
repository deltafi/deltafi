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

import lombok.*;
import org.deltafi.common.storage.s3.ObjectReference;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Segment {
    private UUID uuid;
    private long offset;
    private long size;
    private UUID did;

    public Segment(UUID did) {
        this(UUID.randomUUID(), did);
    }

    public Segment(Segment other) {
        this(other.getUuid(), other.getOffset(), other.getSize(), other.getDid());
    }

    public Segment(UUID uuid, UUID did) {
        this(uuid, 0, ObjectReference.UNKNOWN_SIZE, did);
    }

    public String objectName() {
        return objectName(did, uuid);
    }

    public static String objectName(UUID did, UUID objectId) {
        return did.toString().substring(0, 3) + "/" + did + "/" + objectId;
    }

    public static long calculateTotalSize(Set<Segment> segments) {
        Map<UUID, List<Segment>> segmentsByUuid = new HashMap<>();

        // Group segments by objectName
        for (Segment segment : segments) {
            segmentsByUuid.computeIfAbsent(segment.getUuid(), k -> new ArrayList<>()).add(segment);
        }

        // Calculate total size, minus overlap between segments with the same objectName
        long totalSize = 0;
        for (List<Segment> uuidSegments : segmentsByUuid.values()) {
            totalSize += calculateNonOverlappingSize(uuidSegments);
        }

        return totalSize;
    }

    // Helper function to get the non-overlapping segments for a given object name
    private static long calculateNonOverlappingSize(List<Segment> uuidSegments) {
        if (uuidSegments.size() == 1) {
            return uuidSegments.getFirst().getSize();
        }

        // Create a copy of the original list and sort by offset
        List<Segment> sortedSegments = new ArrayList<>(uuidSegments);
        sortedSegments.sort(Comparator.comparingLong(Segment::getOffset));

        // Merge overlapping segments and calculate total size
        long totalSize = 0;
        Segment mergedSegment = sortedSegments.getFirst();
        for (int i = 1; i < sortedSegments.size(); i++) {
            Segment segment = sortedSegments.get(i);
            if (segment.getOffset() >= mergedSegment.getOffset() + mergedSegment.getSize()) {
                // Non-overlapping segment found
                totalSize += mergedSegment.getSize();
                mergedSegment = segment;
            } else {
                // Merge overlapping segments
                long endPosition = Math.max(mergedSegment.getOffset() + mergedSegment.getSize(), segment.getOffset() + segment.getSize());
                mergedSegment = new Segment(mergedSegment.getUuid(), mergedSegment.getOffset(), endPosition - mergedSegment.getOffset(), mergedSegment.getDid());
            }
        }
        totalSize += mergedSegment.getSize();

        return totalSize;
    }
}
