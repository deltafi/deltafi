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
package org.deltafi.core.types;

import lombok.Builder;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.Content;

import java.util.ArrayList;
import java.util.List;

@Builder
public record ContentRequest(String name, String mediaType, long size, List<Segment>segments) {

    /**
     * Create a new content object trimmed to the requested size
     * @return content object that is less than or equal to the requested size
     */
    public Content trimmedContent() {
        List<Segment> trimmedSegments = new ArrayList<>();

        long bytesLeft = size;
        for (Segment segment : segments) {
            if (segment.getSize() > bytesLeft) {
                Segment trimmedSegment = new Segment(segment.getUuid(), segment.getOffset(), bytesLeft, segment.getDid());
                trimmedSegments.add(trimmedSegment);
                break;
            }

            trimmedSegments.add(segment);
            bytesLeft -= segment.getSize();
        }

        return new Content(name, mediaType, trimmedSegments);
    }

}
