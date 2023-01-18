/**
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
import org.deltafi.common.types.ActionType;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.metrics.MetricsUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.deltafi.common.constant.DeltaFiConstants.SURVEY_ACTION;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
public class SurveyRestTest {
    @Mock
    CoreAuditLogger auditLogger;

    @Mock
    MetricRepository metricRepository;

    @InjectMocks
    SurveyRest testObj;

    @Test
    public void testSurvey() {
        String flow = "myFlow";
        Long bytes = 5000L;
        Long count = 5L;

        ResponseEntity<String> response = testObj.survey(flow, bytes, count, null);

        Assertions.assertEquals(200, response.getStatusCode().value());

        Map<String, String> tags = MetricsUtil.tagsFor(ActionType.INGRESS.name(), SURVEY_ACTION, flow, null);
        Mockito.verify(auditLogger).logSurvey("system", flow, bytes, count);
        Mockito.verify(metricRepository).increment(eq(DeltaFiConstants.FILES_IN), eq(tags), eq(count));
        Mockito.verify(metricRepository).increment(eq(DeltaFiConstants.BYTES_IN), eq(tags), eq(bytes));
    }

    @Test
    public void testSurveyMissingFlow() {
        Long bytes = 5000L;
        Long count = 5L;

        ResponseEntity<String> response = testObj.survey(null, bytes, count, null);

        Assertions.assertEquals(400, response.getStatusCode().value());

        Mockito.verifyNoInteractions(auditLogger);
        Mockito.verifyNoInteractions(metricRepository);
    }
}
