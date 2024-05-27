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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.Objects;

@Builder
@Document(collection = "events")
public record Event(@Id @JsonProperty("_id") String id, String severity, String summary, String content, String source,
                    OffsetDateTime timestamp, boolean notification, boolean acknowledged) {

    public Event(String id, String severity, String summary, String content, String source,
                 OffsetDateTime timestamp, boolean notification, boolean acknowledged) {
        this.id = id;
        this.severity = severity;
        this.summary = summary;
        this.content = content;
        this.source = source;
        this.timestamp = Objects.requireNonNullElseGet(timestamp, OffsetDateTime::now);
        this.notification = notification;
        this.acknowledged = acknowledged;
    }

    public static class Severity {
        public static final String ERROR = "error";
        public static final String WARN = "warn";
        public static final String INFO = "info";
        public static final String SUCCESS = "success";

        private Severity() {}
    }
}
