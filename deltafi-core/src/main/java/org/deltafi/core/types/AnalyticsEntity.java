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
package org.deltafi.core.types;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "analytics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEntity {
    @EmbeddedId
    private AnalyticsEntityId id;
    private Integer flowId;
    private Integer dataSourceId;
    private Integer eventGroupId;
    @Column(name = "action_id")
    private Integer actionId;
    private Integer causeId;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "event_type_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EventTypeEnum eventType;
    private long bytesCount;
    private int fileCount;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "analytic_ingress_type_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AnalyticIngressTypeEnum analyticIngressType;
    private OffsetDateTime updated = OffsetDateTime.now();
}
