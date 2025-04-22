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

import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.analytics.AnalyticEventService.DisabledAnalyticsException;
import org.deltafi.core.services.analytics.AnalyticEventService.SurveyError;
import org.deltafi.core.services.analytics.SurveyEvent;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SurveyRest.class)
@AutoConfigureMockMvc(addFilters = false)
class SurveyRestTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticEventService analyticEventService;
    @MockBean
    private CoreAuditLogger auditLogger;

    @Captor
    ArgumentCaptor<List<SurveyEvent>> eventsCaptor;

    @Test
    void addSurvey() throws Exception {
        @Language("json")
        String json = """
                {
                   "dataSource": "my-source",
                   "files": 1,
                   "ingressBytes": 10,
                   "update_timestamp": "ignore this",
                   "annotations": {"a":  "b"},
                   "annotationA": "value-a",
                   "annotationB": ["b1", "b2"],
                   "annotationC": {"c": 1},
                   "annotationD": 1,
                   "annotationE": true,
                   "annotationF": null
                }
                """;

        mockMvc.perform(post("/api/v2/survey").content(json).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Mockito.verify(analyticEventService).recordSurveys(eventsCaptor.capture());

        List<SurveyEvent> events = eventsCaptor.getValue();
        assertThat(events).hasSize(1);

        SurveyEvent event = events.getFirst();
        assertThat(event.dataSource()).isEqualTo("my-source");
        assertThat(event.files()).isEqualTo(1);
        assertThat(event.ingressBytes()).isEqualTo(10);
        assertThat(event.annotations()).hasSize(7)
                .containsEntry("a", "b") // from standard annotations field
                .containsEntry("annotationA", "value-a")
                .containsEntry("annotationB", "[\"b1\",\"b2\"]")
                .containsEntry("annotationC", "{\"c\":1}")
                .containsEntry("annotationD", "1")
                .containsEntry("annotationE", "true")
                .containsEntry("annotationF", null);
    }

    @Test
    void testInvalidSurvey() throws Exception {
        SurveyError error = new SurveyError("bad", null);

        Mockito.when(analyticEventService.recordSurveys(Mockito.anyList())).thenReturn(List.of(error));
        mockMvc.perform(post("/api/v2/survey").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isArray())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void testDisabledAnalytics() throws Exception {
        Mockito.when(analyticEventService.recordSurveys(Mockito.anyList())).thenThrow(new DisabledAnalyticsException());

        mockMvc.perform(post("/api/v2/survey").content("{}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.error").value("Survey analytics are disabled"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void testBadBody() throws Exception {
        mockMvc.perform(post("/api/v2/survey").content("{null : null}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request body"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}