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
package org.deltafi.core.rest;

import lombok.AllArgsConstructor;
import org.deltafi.core.types.Event;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.EventService;
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

import javax.ws.rs.Produces;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/events")
@Produces(MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    @NeedsPermission.EventRead
    public List<Event> getEvents(@RequestParam Map<String, String> filters) {
        return eventService.getEvents(filters);
    }

    @GetMapping("/{id}")
    @NeedsPermission.EventRead
    public Event getEvent(@PathVariable String id) {
        return eventService.getEvent(UUID.fromString(id));
    }

    @PostMapping
    @NeedsPermission.EventCreate
    public Event createEvent(@RequestBody Event event) {
        return eventService.createEvent(event);
    }

    @PutMapping("/{id}/acknowledge")
    @NeedsPermission.EventUpdate
    public Event acknowledgeEvent(@PathVariable String id) {
        return eventService.updateAcknowledgement(UUID.fromString(id), true);
    }

    @PutMapping("/{id}/unacknowledge")
    @NeedsPermission.EventUpdate
    public Event unacknowledgeEvent(@PathVariable String id) {
        return eventService.updateAcknowledgement(UUID.fromString(id), false);
    }

    @DeleteMapping("{id}")
    @NeedsPermission.EventDelete
    public Event deleteEvent(@PathVariable String id) {
        return eventService.deleteEvent(UUID.fromString(id));
    }
}
