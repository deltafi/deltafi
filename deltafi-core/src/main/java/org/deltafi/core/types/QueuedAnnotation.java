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

import com.fasterxml.uuid.Generators;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "queued_annotations")
@NoArgsConstructor
public class QueuedAnnotation {
    @Id
    @GeneratedValue
    UUID id;

    @Column(nullable = false)
    UUID did;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    Map<String, String> annotations;

    @Column(nullable = false)
    boolean allowOverwrites;

    @Column(nullable = false)
    OffsetDateTime time;

    public QueuedAnnotation(UUID did, Map<String, String> annotations, boolean allowOverwrites) {
        this.id = Generators.timeBasedEpochGenerator().generate();
        this.did = did;
        this.annotations = annotations;
        this.allowOverwrites = allowOverwrites;
        this.time = OffsetDateTime.now();
    }
}
