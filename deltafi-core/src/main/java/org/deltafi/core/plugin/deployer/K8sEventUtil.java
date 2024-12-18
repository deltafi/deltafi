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
package org.deltafi.core.plugin.deployer;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.MicroTime;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.util.TimeFormatter;

import java.time.OffsetDateTime;
import java.util.List;

public class K8sEventUtil {

    private static final String UNKNOWN_TIME = "<unknown>";
    public static final List<String> EVENT_COLUMNS = List.of("Type", "Reason", "Age", "From", "Message");

    private K8sEventUtil(){}

    public static List<Event> sortEvents(List<Event> events) {
        events.sort(K8sEventUtil::compareEventTimes);
        return events;
    }

    public static List<String> formatEvent(Event event) {
        String eventTime = event.getEventTime() != null ? event.getEventTime().getTime() : null;
        String firstTimestampSince = getFirstTimestampSince(eventTime, event.getFirstTimestamp());

        String interval;
        if (event.getSeries() != null) {
            interval = java.lang.String.format("%s (x%d over %s)", humanReadableTimeSince(event.getSeries().getLastObservedTime()), event.getSeries().getCount(), firstTimestampSince);
        } else if (event.getCount() != null && event.getCount() > 1) {
            interval = java.lang.String.format("%s (x%d over %s)", humanReadableTimeSince(event.getLastTimestamp()), event.getCount(), firstTimestampSince);
        } else {
            interval = firstTimestampSince;
        }

        String source = getEventSource(event);
        String message = event.getMessage() != null ? event.getMessage().trim() : "";

        return List.of(event.getType(), event.getReason(), interval, source, message);
    }

    private static String getEventSource(Event event) {
        return event.getSource() == null || StringUtils.isBlank(event.getSource().getComponent()) ?
                event.getReportingComponent() : event.getSource().getComponent();
    }

    private static String getFirstTimestampSince(String eventTime, String firstTimestamp) {
        if (eventTime == null && firstTimestamp == null) {
            return UNKNOWN_TIME;
        }

        return eventTime != null ? humanReadableTimeSince(eventTime) : humanReadableTimeSince(firstTimestamp);
    }

    private static String humanReadableTimeSince(MicroTime microTime) {
        return microTime != null && microTime.getTime() != null ?
                humanReadableTimeSince(microTime.getTime()) : UNKNOWN_TIME;
    }

    private static String humanReadableTimeSince(String time) {
        return TimeFormatter.humanReadableTimeSince(OffsetDateTime.parse(time));
    }

    private static int compareEventTimes(Event a, Event b) {
        return Long.compare(fromTimeString(a.getLastTimestamp()), fromTimeString(b.getLastTimestamp()));
    }

    private static long fromTimeString(String time) {
        return time != null ? OffsetDateTime.parse(time).toEpochSecond() : -1;
    }
}