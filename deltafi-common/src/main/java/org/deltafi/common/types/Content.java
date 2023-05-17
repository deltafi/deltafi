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
package org.deltafi.common.types;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.Segment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A data object that defines a named content reference
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Content {
  private String name;
  private String mediaType;
  private List<Segment> segments = new ArrayList<>();

  /**
   * Create content with no segments
   * @param name name
   * @param mediaType mediaType
   */
  public Content(String name, String mediaType) {
    this.name = name;
    this.mediaType = mediaType;
  }

  /**
   * Create content with a single segment
   * @param name name
   * @param mediaType mediaType
   * @param segment segment
   */
  public Content(String name, String mediaType, Segment segment) {
    this.name = name;
    this.mediaType = mediaType;
    this.segments.add(segment);
  }

  /**
   * Provide an all-args constructor including size, which is a field in graphql but not on the object
   * @param name name
   * @param mediaType mediaType
   * @param segments segments
   * @param ignored placeholder for size
   */
  public Content(String name, String mediaType, List<Segment> segments, long ignored) {
    this.name = name;
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
    return sumSegmentSizes(segments);
  }

  /**
   * Returns a new Content that is a copy of a portion of this Content, at the given offset and size
   * Handles piecing together the underlying segments properly
   * @param offset Number of bytes at which to offset the new Content
   * @param size Size in bytes of the new Content
   * @param name the name for the new Content
   * @param mediaType the mediaType for the new Content
   * @return A trimmed down Content
   */
  public Content subcontent(long offset, long size, String name, String mediaType) {
    return new Content(name, mediaType, subreferenceSegments(offset, size));
  }

  /**
   * Returns a new Content that is a copy of a portion of this Content, at the given offset and size
   * Handles piecing together the underlying segments properly
   * @param offset Number of bytes at which to offset the new Content
   * @param size Size in bytes of the new Content
   * @return A trimmed down Content
   */
  public Content subcontent(long offset, long size) {
    return subcontent(offset, size, name, mediaType);
  }

  /**
   * Returns a list of Segments that copy portions of this Content, at the given offset and size
   * Handles piecing together the underlying segments properly
   * @param offset Number of bytes at which to offset the new segments
   * @param size Size in bytes of the new segments
   * @return A subset of segments in the Content
   */
  public List<Segment> subreferenceSegments(long offset, long size) {
    if (offset < 0) {
      throw new IllegalArgumentException("subreference offset must be positive, got " + offset);
    }

    if (size < 0) {
      throw new IllegalArgumentException("subreference size must be positive, got " + size);
    }

    if (size + offset > getSize()) {
      throw new IllegalArgumentException("Size + offset ( " + size + " + " + offset + ") exceeds total Content size of " + getSize());
    }

    if (size == 0) {
      return Collections.emptyList();
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

    return newSegments;
  }

  /**
   * Calculates the sum of the sizes of the provided segments.
   *
   * @param segments The list of segments whose sizes to sum.
   * @return The sum of the sizes of the segments. If the list of segments is null, returns 0.
   */
  private static long sumSegmentSizes(List<Segment> segments) {
    return segments != null ? segments.stream().mapToLong(Segment::getSize).sum() : 0;
  }

  /**
   * Creates and returns a copy of the current Content object.
   *
   * @return A new Content object that is a copy of the current object. The segments are copied into a new ArrayList.
   */
  public Content copy() {
    return new Content(name, mediaType, new ArrayList<>(segments));
  }
}
