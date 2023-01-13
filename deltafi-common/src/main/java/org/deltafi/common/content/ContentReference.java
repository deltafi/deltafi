/**
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ContentReference {
    private String mediaType;
    private List<Segment> segments;

    public ContentReference(String mediaType, Segment... segments) {
        this.mediaType = mediaType;
        this.segments = Arrays.stream(segments).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Provide an all-args constructor including size, which is a field in graphql but not on the object
     * @param mediaType mediaType
     * @param segments segments
     * @param ignored placeholder for size
     */
    public ContentReference(String mediaType, List<Segment> segments, long ignored) {
        this.mediaType = mediaType;
        this.segments = Collections.unmodifiableList(segments);
    }

    /**
     * Empty method in place for object mappers trying to set the size from JSON
     * @param ignored ignored field
     */
    public void setSize(long ignored) {}

    /**
     * Calculate size of all the segments
     * @return total size
     */
    public long getSize() {
        return segments.stream().map(Segment::getSize).reduce(0L, Long::sum);
    }

    /**
     * Returns a new ContentReference that is a copy of a portion of this ContentReference, at the given offset and size
     * Handles piecing together the underlying segments properly
     * @param offset Number of bytes at which to offset the new ContentReference
     * @param size Size in bytes of the new ContentReference
     * @return A trimmed down ContentReference
     */
    public ContentReference subreference(long offset, long size) {
        if (offset < 0) {
            throw new IllegalArgumentException("subreference offset must be positive, got " + offset);
        }

        if (size < 0) {
            throw new IllegalArgumentException("subreference size must be positive, got " + size);
        }

        if (size + offset > getSize()) {
            throw new IllegalArgumentException("Size + offset ( " + size + " + " + offset + ") exceeds total ContentReference size of " + getSize());
        }

        if (size == 0) {
            return new ContentReference(getMediaType(), Collections.emptyList());
        }

        List<Segment> newSegments = new ArrayList<>();
        long offsetRemaining = offset;
        long sizeRemaining = size;

        for (Segment segment : segments) {
            Segment newSegment = new Segment(segment);

            if (offsetRemaining > 0) {
                if (newSegment.getSize() < offsetRemaining) {
                    // the first offset is past this segment, skip it
                    offsetRemaining -= newSegment.getSize();
                    continue;
                } else {
                    // chop off the front of this segment
                    newSegment.setOffset(newSegment.getOffset() + offsetRemaining);
                    newSegment.setSize(newSegment.getSize() - offsetRemaining);
                    offsetRemaining = 0;
                }
            }

            if (sizeRemaining < newSegment.getSize()) {
                // chop off the back of this segment
                newSegment.setSize(sizeRemaining);
            }
            sizeRemaining -= newSegment.getSize();
            newSegments.add(newSegment);
            if (sizeRemaining == 0) {
                break;
            }
        }

        return new ContentReference(getMediaType(), newSegments);
    }
}
