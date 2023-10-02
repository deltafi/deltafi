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
package org.deltafi.actionkit.action.load;

import lombok.Data;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.ChildLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
public class ChildLoadResult {
    private final String did;
    private LoadResult loadResult;

    /**
     * Create a ChildLoadResult with a random did
     */
    public ChildLoadResult() {
        this.did = UUID.randomUUID().toString();
    }

    /**
     * Create a ChildLoadResult with the given loadResult and a random did
     * @param loadResult load result for this child
     */
    ChildLoadResult(@NotNull LoadResult loadResult) {
        this();
        this.loadResult = loadResult;
    }

    ChildLoadEvent toEvent() {
        return ChildLoadEvent.builder()
                .did(did)
                .domains(loadResult.getDomains())
                .content(ContentConverter.convert(loadResult.getContent()))
                .annotations(loadResult.getAnnotations())
                .metadata(loadResult.getMetadata())
                .deleteMetadataKeys(loadResult.getDeleteMetadataKeys())
                .build();
    }

}