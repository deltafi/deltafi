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
package org.deltafi.core.rest;

import lombok.AllArgsConstructor;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.types.Event;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.EventService;
import org.deltafi.core.types.EventsWithCount;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v2/events", produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class EventController {

    private final EventService eventService;
    private final CoreAuditLogger auditLogger;

    @GetMapping
    @NeedsPermission.EventRead
    public List<Event> getEvents(@RequestParam Map<String, String> filters,
                                 @RequestParam(defaultValue = "0") int offset,
                                 @RequestParam(defaultValue = "20") int size) {
        return eventService.getEvents(filters, offset, size);
    }

    @GetMapping("/with-count")
    @NeedsPermission.EventRead
    public EventsWithCount getEventsWithCount(@RequestParam Map<String, String> filters,
                                              @RequestParam(defaultValue = "0") int offset,
                                              @RequestParam(defaultValue = "20") int size) {
        return eventService.getEventsWithCount(filters, offset, size);
    }

    @GetMapping("/{id}")
    @NeedsPermission.EventRead
    public Event getEvent(@PathVariable String id) {
        return eventService.getEvent(UUID.fromString(id));
    }

    @PostMapping
    @NeedsPermission.EventCreate
    public Event createEvent(@RequestBody Event event) {
        Event persisted = eventService.createEvent(event);
        auditLogger.audit("created event {}", event.getId());
        return persisted;
    }

    @PutMapping("/{id}/acknowledge")
    @NeedsPermission.EventUpdate
    public Event acknowledgeEvent(@PathVariable String id) {
        auditLogger.audit("acknowledged event {}", id);
        return eventService.updateAcknowledgement(UUID.fromString(id), true);
    }

    @PutMapping("/{id}/unacknowledge")
    @NeedsPermission.EventUpdate
    public Event unacknowledgeEvent(@PathVariable String id) {
        auditLogger.audit("unacknowledged event {}", id);
        return eventService.updateAcknowledgement(UUID.fromString(id), false);
    }

    @DeleteMapping("/{id}")
    @NeedsPermission.EventDelete
    public Event deleteEvent(@PathVariable String id) {
        auditLogger.audit("deleted event {}", id);
        return eventService.deleteEvent(UUID.fromString(id));
    }
}
