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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ActionExecution(String clazz, String action, UUID did, OffsetDateTime startTime, OffsetDateTime heartbeatTime) {

    public ActionExecution(String clazz, String action, UUID did, OffsetDateTime startTime) {
        this(clazz, action, did, startTime, null);
    }

    public boolean exceedsDuration(Duration duration) {
        return startTime.plus(duration).isBefore(OffsetDateTime.now());
    }

    public String key() {
        return String.format("%s:%s:%s", clazz, action, did);
    }
}
