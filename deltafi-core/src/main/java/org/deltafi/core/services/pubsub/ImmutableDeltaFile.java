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
package org.deltafi.core.services.pubsub;

import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds a copy of the DeltaFile metadata and content list to prevent
 * SpEL expressions from modifying the original DeltaFile
 * @param metadata to use when evaluating conditions
 * @param content to use when evaluating conditions
 */
public record ImmutableDeltaFile(Map<String, String> metadata, List<Content> content) {
    public ImmutableDeltaFile(DeltaFile deltaFile) {
        this(deltaFile.getImmutableMetadata(), deltaFile.getImmutableContent());
    }

    /**
     * Helper that can be used to check if DeltaFile contains content with the given mediaType
     * @param mediaType to find in the list of content
     * @return true if there is a content with the given mediaType
     */
    public boolean hasMediaType(String mediaType) {
        return this.content.stream().anyMatch(content -> Objects.equals(mediaType, content.getMediaType()));
    }
}
