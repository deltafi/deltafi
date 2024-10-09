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
package org.deltafi.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.analytics.AnalyticEventService.DisabledAnalyticsException;
import org.deltafi.core.services.analytics.AnalyticEventService.SurveyError;
import org.deltafi.core.services.analytics.SurveyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v2/survey")
@AllArgsConstructor
@Slf4j
public class SurveyRest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private final AnalyticEventService analyticEventService;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @NeedsPermission.SurveyCreate
    public ResponseEntity<SurveyErrors> addSurveys(@RequestBody String body) throws JsonProcessingException {
        // parse string body here to support single json entry as a list
        List<SurveyEvent> surveys = OBJECT_MAPPER.readValue(body, new TypeReference<>() {});

        List<SurveyError> errors = analyticEventService.recordSurveys(surveys);
        return errors.isEmpty() ? ResponseEntity.ok().body(null) :
                ResponseEntity.badRequest().body(new SurveyErrors(errors));
    }

    @ExceptionHandler(DisabledAnalyticsException.class)
    public ResponseEntity<Error> handleDisabledAnalytics() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new Error("Survey analytics are disabled"));
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Error> handleInvalidBody() {
        return ResponseEntity.badRequest().body(new Error("Invalid request body"));
    }

    public record SurveyErrors(List<SurveyError> error, OffsetDateTime timestamp) {
        public SurveyErrors(List<SurveyError> error) {
            this(error, OffsetDateTime.now());
        }
    }

    public record Error(String error, OffsetDateTime timestamp) {
        public Error(String error) {
            this(error, OffsetDateTime.now());
        }
    }
}
