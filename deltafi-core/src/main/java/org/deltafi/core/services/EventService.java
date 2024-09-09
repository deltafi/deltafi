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

import com.networknt.schema.utils.StringUtils;
import lombok.AllArgsConstructor;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.ValidationException;
import org.deltafi.core.repo.EventRepo;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@AllArgsConstructor
public class EventService {
    private static final Set<String> VALID_SEVERITIES = Set.of(Severity.ERROR, Severity.INFO, Severity.SUCCESS, Severity.WARN);

    private final EventRepo eventRepo;

    public List<Event> getEvents(Map<String, String> filters) {
        return eventRepo.findEvents(filters);
    }

    public Event getEvent(UUID id) {
        return eventRepo.findById(id).orElseThrow(notFound(id));
    }

    public Event createEvent(Event event) {
        validateNewEvent(event);
        return eventRepo.save(event);
    }

    public void createEvents(List<Event> events) {
        events.forEach(this::validateNewEvent);
        eventRepo.saveAll(events);
    }

    public Event updateAcknowledgement(UUID id, boolean acknowledged) {
        return eventRepo.updateAcknowledged(id, acknowledged).orElseThrow(notFound(id));
    }

    public Event deleteEvent(UUID id) {
        Event event = getEvent(id);
        eventRepo.deleteById(id);
        return event;
    }

    public long notificationCount() {
        return eventRepo.notificationCount(OffsetDateTime.now().minusDays(7));
    }

    private void validateNewEvent(Event event) {
        if (StringUtils.isBlank(event.getSummary()) || event.getSeverity() == null) {
            throw new ValidationException("Event summary and severity are required");
        }

        if (StringUtils.isBlank(event.getSeverity()) || !VALID_SEVERITIES.contains(event.getSeverity())) {
            throw new ValidationException("Severity must be info, success, warn, or error (given severity: " + event.getSeverity() + ")");
        }
    }

    private Supplier<EntityNotFound> notFound(UUID id) {
        return () -> new EntityNotFound("Event with ID '" + id + "' not found.");
    }
}
