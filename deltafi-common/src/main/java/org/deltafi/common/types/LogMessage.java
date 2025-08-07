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

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogMessage {
    private LogSeverity severity;
    private OffsetDateTime created;
    // may be action name or username
    private String source;
    private String message;

    public static LogMessage createTrace(String source, String message) {
        return new LogMessage(LogSeverity.TRACE, OffsetDateTime.now(), source, message);
    }

    public static LogMessage createInfo(String source, String message) {
        return new LogMessage(LogSeverity.INFO, OffsetDateTime.now(), source, message);
    }

    public static LogMessage createWarning(String source, String message) {
        return new LogMessage(LogSeverity.WARNING, OffsetDateTime.now(), source, message);
    }

    public static LogMessage createError(String source, String message) {
        return new LogMessage(LogSeverity.ERROR, OffsetDateTime.now(), source, message);
    }
}
