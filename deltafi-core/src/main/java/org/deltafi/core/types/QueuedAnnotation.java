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
package org.deltafi.core.types;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Document
public class QueuedAnnotation {
    @Id
    String id;

    String did;
    Map<String, String> annotations;
    boolean allowOverwrites;
    OffsetDateTime time;

    public QueuedAnnotation(String did, Map<String, String> annotations, boolean allowOverwrites) {
        this.id = UUID.randomUUID().toString();
        this.did = did;
        this.annotations = annotations;
        this.allowOverwrites = allowOverwrites;
        this.time = OffsetDateTime.now();
    }
}
