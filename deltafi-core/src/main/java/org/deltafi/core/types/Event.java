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

import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Entity
@Table(name = "events")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Event {
    @Id
    @Builder.Default
    private UUID id = Generators.timeBasedEpochGenerator().generate();

    private String severity;
    private String summary;

    @Column(length = 100_000)
    private String content;

    private String source;
    private OffsetDateTime timestamp;
    private boolean notification;
    private boolean acknowledged;

    public static class Severity {
        public static final String ERROR = "error";
        public static final String WARN = "warn";
        public static final String INFO = "info";
        public static final String SUCCESS = "success";

        private Severity() {}

        public static String mapSeverity(String severity) {
            String downcased = severity != null ? severity.toLowerCase() : "";
            return switch (downcased) {
                case ERROR, "failure", "red" -> ERROR;
                case WARN, "warning", "yellow" -> WARN;
                case SUCCESS, "successful", "green" -> SUCCESS;
                default -> INFO;
            };
        }
    }
}
