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

package org.deltafi.core.services;

import lombok.AllArgsConstructor;
import org.deltafi.core.services.api.DeltafiApiClient;
import org.deltafi.core.services.api.model.Event;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

/**
 * Service object used to post events to the DeltaFi event stream
 */
@Service
@AllArgsConstructor
public class EventService {
    private DeltafiApiClient deltafiApiClient;

    /**
     * Publish an event to the DeltaFi event API:
     *
     * <ul>
     *     <li>Set notification to true on the event object in order for the event to appear in the UI notification list</li>
     *     <li>Severity, source and content can also be added to event</li>
     * </ul>
     *
     * @param event Event object representing the event to post to the event API
     */
    public void publishEvent(@NotNull Event event) {
        deltafiApiClient.createEvent(event.asJson());
    }

    /**
     * Publish a simple info event to the DeltaFi event API
     * @param eventSummary the summary string that will appear in the event
     */
    public void publishInfo(@NotNull String eventSummary) {
        deltafiApiClient.createEvent(Event.builder(eventSummary).build().asJson());
    }
}
