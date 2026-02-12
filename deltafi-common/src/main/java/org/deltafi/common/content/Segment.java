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
package org.deltafi.common.content;

import lombok.*;
import org.deltafi.common.storage.s3.ObjectReference;

import java.util.*;

/** Represents a byte range within a content object stored in S3. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Segment {
    private UUID uuid;
    private long offset;
    private long size;
    private UUID did;

    /**
     * Create a segment with a random UUID for the given DeltaFile.
     * @param did DeltaFile ID that owns this segment
     */
    public Segment(UUID did) {
        this(UUID.randomUUID(), did);
    }

    /**
     * Copy constructor.
     * @param other segment to copy
     */
    public Segment(Segment other) {
        this(other.getUuid(), other.getOffset(), other.getSize(), other.getDid());
    }

    /**
     * Create a segment with the given UUID and DeltaFile ID, starting at offset 0 with unknown size.
     * @param uuid segment identifier
     * @param did DeltaFile ID that owns this segment
     */
    public Segment(UUID uuid, UUID did) {
        this(uuid, 0, ObjectReference.UNKNOWN_SIZE, did);
    }

    /**
     * Build the S3 object name for this segment.
     * @return the object name derived from the DeltaFile ID and segment UUID
     */
    public String objectName() {
        return objectName(did, uuid);
    }

    /**
     * Create a segment by parsing an S3 object name.
     * @param objectName S3 object name in the format "prefix/did/uuid"
     * @param size size of the segment in bytes
     */
    public Segment(String objectName, long size) {
        String[] parts = objectName.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid object name format: " + objectName);
        }

        Segment segment = new Segment();
        segment.setDid(UUID.fromString(parts[1]));
        segment.setUuid(UUID.fromString(parts[2]));
        segment.setOffset(0);
        segment.setSize(size);
    }

    /**
     * Build the S3 object name for the given DeltaFile ID and object ID.
     * @param did DeltaFile ID
     * @param objectId object identifier
     * @return the S3 object name
     */
    public static String objectName(UUID did, UUID objectId) {
        return did.toString().substring(0, 3) + "/" + did + "/" + objectId;
    }

    /**
     * Calculate the total non-overlapping byte size across all segments.
     * @param segments set of segments to measure
     * @return total size in bytes, accounting for overlapping ranges
     */
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
