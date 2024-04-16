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

import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.metrics.MetricService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class SurveyRestTest {
    @Mock
    CoreAuditLogger auditLogger;

    @Mock
    MetricService metricService;

    @InjectMocks
    SurveyRest testObj;

    @Test
    public void testSurvey() {
        String flow = "myFlow";
        Long bytes = 5000L;
        Long count = 5L;

        ResponseEntity<String> response = testObj.survey(flow, bytes, count, null,null,null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("Deprecated")).contains("True");

        Map<String, String> tags = Map.of("surveyFlow", "myFlow", "surveyDirection", "none");
        Mockito.verify(auditLogger).logSurvey("system", flow, null, "none", bytes, count);
        Mockito.verify(metricService).increment(eq(DeltaFiConstants.SURVEY_FILES), eq(tags), eq(count));
        Mockito.verify(metricService).increment(eq(DeltaFiConstants.SURVEY_BYTES), eq(tags), eq(bytes));
        Mockito.verifyNoMoreInteractions(metricService);
    }

    @Test
    public void testSurveySubflow() {
        String flow = "myFlow";
        Long bytes = 5000L;
        Long count = 5L;
        String subflow = "mySubflow";
        String direction = "myDirection";

        ResponseEntity<String> response = testObj.survey(flow, bytes, count, subflow,direction,"Narf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("Deprecated")).contains("True");

        Map<String, String> tags = Map.of("surveyFlow", flow, "surveyDirection", direction);
        Mockito.verify(auditLogger).logSurvey("Narf", flow, subflow, direction, bytes, count);
        Mockito.verify(metricService).increment(eq(DeltaFiConstants.SURVEY_FILES), eq(tags), eq(count));
        Mockito.verify(metricService).increment(eq(DeltaFiConstants.SURVEY_BYTES), eq(tags), eq(bytes));
        Map<String, String> subflowTags = Map.of("surveyFlow", flow, "surveyDirection", direction, "surveySubflow", subflow);
        Mockito.verify(metricService).increment(eq(DeltaFiConstants.SURVEY_SUBFLOW_FILES), eq(subflowTags), eq(count));
        Mockito.verify(metricService).increment(eq(DeltaFiConstants.SURVEY_SUBFLOW_BYTES), eq(subflowTags), eq(bytes));
        Mockito.verifyNoMoreInteractions(metricService);
    }

    @Test
    public void testSurveyMissingFlow() {
        Long bytes = 5000L;
        Long count = 5L;

        ResponseEntity<String> response = testObj.survey(null, bytes, count, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().get("Deprecated")).contains("True");

        Mockito.verifyNoInteractions(auditLogger);
        Mockito.verifyNoInteractions(metricService);
    }
}
