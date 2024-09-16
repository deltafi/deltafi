/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.converters.KeyValueConverter;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class ResumeMetadata {
    private String flow;
    private String action;
    private List<KeyValue> metadata;
    private List<String> deleteMetadataKeys;

    public ResumeMetadata(String flow, String action, Map<String, String> metadata, List<String> deleteMetadataKeys) {
        this.flow = flow;
        this.action = action;
        this.metadata = KeyValueConverter.fromMap(metadata);
        this.deleteMetadataKeys = deleteMetadataKeys;
    }
}