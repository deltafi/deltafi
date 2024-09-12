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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.exceptions.ValidationException;
import org.deltafi.core.services.EventService;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerTest {
    UUID EVENT_1 = UUID.randomUUID();
    UUID EVENT_2 = UUID.randomUUID();

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @MockBean
    private CoreAuditLogger auditLogger;

    @Test
    void getEventsTest() throws Exception {
        Event event1 = createEvent(EVENT_1);
        Event event2 = createEvent(EVENT_2);

        String start = "2024-05-22T04:00:00.000Z";
        String end = "2024-05-23T03:59:59.999Z";

        Mockito.when(eventService.getEvents(Map.of())).thenReturn(List.of(event1, event2));
        Mockito.when(eventService.getEvents(Map.of("start", start, "end", end, "acknowledged", "true"))).thenReturn(List.of(event2, event1));

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(event1.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(event2.getId().toString()));

        mockMvc.perform(get("/events?start="+start+"&end="+end+"&acknowledged=true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(event2.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(event1.getId().toString()));
    }

    @Test
    void getEventTest() throws Exception {
        Event event = createEvent();
        Mockito.when(eventService.getEvent(EVENT_1)).thenReturn(event);

        mockMvc.perform(get("/events/" + EVENT_1))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(event.getId().toString()));
    }

    @Test
    void createEventTest() throws Exception {
        Event event = createEvent();
        Mockito.when(eventService.createEvent(event)).thenReturn(event);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(event.getId().toString()));
    }

    @Test
    void acknowledgeEventTest() throws Exception {
        Event event = createEvent();
        Mockito.when(eventService.updateAcknowledgement(EVENT_1, true)).thenReturn(event);

        mockMvc.perform(put("/events/" + EVENT_1 + "/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(event.getId().toString()));
    }

    @Test
    void unacknowledgeEventTest() throws Exception {
        Event event = createEvent();
        Mockito.when(eventService.updateAcknowledgement(EVENT_1, false)).thenReturn(event);

        mockMvc.perform(put("/events/" + EVENT_1 + "/unacknowledge"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(event.getId().toString()));
    }

    @Test
    void deleteEventTest() throws Exception {
        Event event = createEvent();
        Mockito.when(eventService.deleteEvent(EVENT_1)).thenReturn(event);

        mockMvc.perform(delete("/events/" + EVENT_1))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(event.getId().toString()));
    }

    @Test
    void getMissingById() throws Exception {
        Mockito.when(eventService.getEvent(EVENT_1)).thenThrow(new EntityNotFound("missing"));

        mockMvc.perform(get("/events/" + EVENT_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidEvent() throws Exception {
        Mockito.when(eventService.createEvent(Mockito.any())).thenThrow(new ValidationException("bad input"));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Event createEvent() {
        return createEvent(EVENT_1);
    }

    private Event createEvent(UUID id) {
        return Event.builder().id(id).severity(Severity.SUCCESS).content("content").summary("summary").timestamp(Instant.now().atOffset(ZoneOffset.UTC)).build();
    }
}