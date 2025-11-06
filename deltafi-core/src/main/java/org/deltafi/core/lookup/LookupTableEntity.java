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
package org.deltafi.core.lookup;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.types.hibernate.StringListType;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "lookup_tables")
@DynamicUpdate // A Hibernate error is thrown if this isn't present!
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LookupTableEntity {
    @Id
    private String name;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private PluginCoordinates sourcePlugin;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    protected List<Variable> variables;

    @Type(StringListType.class)
    @Column(columnDefinition = "text[]")
    private List<String> columns;

    @Type(StringListType.class)
    @Column(columnDefinition = "text[]")
    private List<String> keyColumns;

    @Builder.Default
    private boolean serviceBacked = true;
    @Builder.Default
    private boolean backingServiceActive = true;

    @Builder.Default
    private boolean pullThrough = false;

    private String refreshDuration;
    private OffsetDateTime lastRefresh;

    public static LookupTableEntity fromLookupTable(LookupTable lookupTable) {
        return new LookupTableEntity(lookupTable.getName(), lookupTable.getSourcePlugin(), lookupTable.getVariables(),
                lookupTable.getColumns(), lookupTable.getKeyColumns(), lookupTable.isServiceBacked(),
                lookupTable.isBackingServiceActive(), lookupTable.isPullThrough(), lookupTable.getRefreshDuration(),
                lookupTable.getLastRefresh());
    }

    public LookupTable toLookupTable() {
        return new LookupTable(name, sourcePlugin, variables, columns, keyColumns, serviceBacked, backingServiceActive,
                pullThrough, refreshDuration, lastRefresh, 0);
    }
}
