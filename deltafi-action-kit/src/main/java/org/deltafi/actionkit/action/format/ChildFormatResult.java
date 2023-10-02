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
package org.deltafi.actionkit.action.format;

import lombok.Data;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.ChildFormatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Data
class ChildFormatResult {
    private final String did;
    private FormatResult formatResult;

    /**
     * Create a ChildFormatResult with a random did
     */
    ChildFormatResult() {
        this.did = UUID.randomUUID().toString();
    }

    /**
     * Create a ChildFormatResult with the given formatResult and a random did
     * @param formatResult format result for this child
     */
    ChildFormatResult(@NotNull FormatResult formatResult) {
        this();
        this.formatResult = formatResult;
    }

    ChildFormatEvent toEvent() {
        return ChildFormatEvent.builder()
                .did(did)
                .content(ContentConverter.convert(formatResult.getContent()))
                .metadata(formatResult.getMetadata())
                .deleteMetadataKeys(formatResult.getDeleteMetadataKeys())
                .build();
    }
}