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
package org.deltafi.actionkit.action.load;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.actionkit.exception.MissingSourceMetadataException;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFile;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Builder
@Data
public class LoadInput {
    String sourceFilename;
    String ingressFlow;
    Map<String, String> sourceMetadata;
    List<Content> contentList;
    Map<String, String> metadata;

    @SuppressWarnings("unused")
    public String sourceMetadata(String key) {
        if (sourceMetadata.containsKey(key)) {
            return sourceMetadata.get(key);
        } else {
            throw new MissingSourceMetadataException(key);
        }
    }

    @SuppressWarnings("unused")
    public String sourceMetadata(String key, String defaultValue) {
        return sourceMetadata.getOrDefault(key, defaultValue);
    }

    public String metadata(String key) {
        if (metadata.containsKey(key)) {
            return metadata.get(key);
        } else {
            throw new MissingMetadataException(key);
        }
    }

    public String metadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public boolean hasContent() {
        return !contentList.isEmpty();
    }

    public Content contentAt(int index) {
        return contentList.get(index);
    }

    @SuppressWarnings("unused")
    public Content firstContent() {
        if (!hasContent()) {
            throw new ExpectedContentException();
        }
        return contentAt(0);
    }

    public static LoadInput fromDeltaFile(DeltaFile deltaFile) {
        return LoadInput.builder()
                .sourceFilename(deltaFile.getSourceInfo().getFilename())
                .ingressFlow(deltaFile.getSourceInfo().getFlow())
                .sourceMetadata(deltaFile.getSourceInfo().getMetadata())
                .contentList(deltaFile.getLastProtocolLayerContent())
                .metadata(deltaFile.getLastProtocolLayerMetadata())
                .build();
    }
}
