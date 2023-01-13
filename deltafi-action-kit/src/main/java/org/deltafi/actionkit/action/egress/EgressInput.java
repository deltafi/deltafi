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
package org.deltafi.actionkit.action.egress;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.deltafi.actionkit.exception.MissingSourceMetadataException;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.FormattedData;

import java.util.Map;

@AllArgsConstructor
@Builder
@Data
public class EgressInput {
    String sourceFilename;
    String ingressFlow;
    Map<String, String> sourceMetadata;
    FormattedData formattedData;

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

    public static EgressInput fromDeltaFile(DeltaFile deltaFile) {
        return EgressInput.builder()
                .sourceFilename(deltaFile.getSourceInfo().getFilename())
                .ingressFlow(deltaFile.getSourceInfo().getFlow())
                .sourceMetadata(deltaFile.getSourceInfo().getMetadata())
                .formattedData(deltaFile.getFormattedData().get(0))
                .build();
    }
}
