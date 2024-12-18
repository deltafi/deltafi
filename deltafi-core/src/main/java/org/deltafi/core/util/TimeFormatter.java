/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.util;

import java.time.Duration;
import java.time.OffsetDateTime;

public class TimeFormatter {
    private TimeFormatter() {}

    public static String humanReadableTimeSince(OffsetDateTime time) {
        return formattedDuration(Duration.ofSeconds(OffsetDateTime.now().toEpochSecond() - time.toEpochSecond()));
    }

    public static String formattedDuration(Duration duration) {
        long milliseconds = duration.toMillis();

        long days = milliseconds / 86400000L;
        milliseconds -= days * 86400000L;

        long hours = milliseconds / 3600000L;
        milliseconds -= hours * 3600000L;

        long minutes = milliseconds / 60000L;
        milliseconds -= minutes * 60000L;

        long seconds = milliseconds / 1000L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append('d');
        }
        if (hours > 0) {
            sb.append(hours).append('h');
        }
        if (minutes > 0) {
            sb.append(minutes).append('m');
        }
        if (seconds > 0) {
            sb.append(seconds).append('s');
        }

        return sb.toString();
    }
}
