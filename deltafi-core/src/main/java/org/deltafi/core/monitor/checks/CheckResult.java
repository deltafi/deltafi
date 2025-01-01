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
package org.deltafi.core.monitor.checks;

import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public record CheckResult(String description, int code, String message, OffsetDateTime timestamp) {
    public CheckResult(String description, int code, String message) {
        this(description, code, message, OffsetDateTime.now());
    }

    public static class ResultBuilder {
        @Getter
        private int code = 0;
        private final List<String> messageLines = new ArrayList<>();

        public void code(int code) {
            if (code > this.code) {
                this.code = code;
            }
        }

        public String message() {
            return String.join("\n", messageLines);
        }

        public void addHeader(String header) {
            messageLines.add("#### " + header + "\n");
        }

        public void addLine(String message) {
            messageLines.add(message);
        }
    }
}
