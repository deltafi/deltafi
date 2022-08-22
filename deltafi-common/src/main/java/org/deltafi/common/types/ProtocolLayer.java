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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.ContentReference;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProtocolLayer {
    // deprecated
    private String type;

    private String action;
    private List<Content> content;
    private List<KeyValue> metadata;

    // remove once deprecated type is removed
    public ProtocolLayer(String action, List<Content> content, List<KeyValue> metadata) {
        this.action = action;
        this.content = content;
        this.metadata = metadata;
    }

    @JsonIgnore
    public ContentReference getContentReference() {
        return content.isEmpty() ? null : content.get(0).getContentReference();
    }
}