/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngressEvent {
    @Builder.Default
    boolean executeImmediate = false;
    private String memo;
    @Builder.Default
    private List<IngressEventItem> ingressItems = new ArrayList<>();
    @Builder.Default
    private IngressStatus status = IngressStatus.HEALTHY;
    private String statusMessage;

    public Set<String> segmentObjectNames() {
        Set<String> objectNames = new HashSet<>();
        for (IngressEventItem ingressEventItem : ingressItems) {
            if (ingressEventItem.getContent() != null) {
                for (Content c : ingressEventItem.getContent()) {
                    objectNames.addAll(c.objectNames());
                }
            }
        }
        return objectNames;
    }
}
