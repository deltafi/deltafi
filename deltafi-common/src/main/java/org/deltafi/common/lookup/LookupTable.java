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
package org.deltafi.common.lookup;

import lombok.*;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LookupTable {
    private String name;

    private PluginCoordinates sourcePlugin;

    protected List<Variable> variables;

    private List<String> columns;

    private List<String> keyColumns;

    @Builder.Default
    private boolean serviceBacked = true;
    @Builder.Default
    private boolean backingServiceActive = true;

    @Builder.Default
    private boolean pullThrough = false;

    private String refreshDuration;
    private OffsetDateTime lastRefresh;

    private int totalRows;
}
