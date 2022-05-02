/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.metrics;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.actionkit.action.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;

public class ActionMetricsLoggerTest {
    private static final MockedStatic<MetricLogger> MOCKED_METRIC_LOGGER = Mockito.mockStatic(MetricLogger.class);

    @BeforeEach
    public void beforeEach() {
        MOCKED_METRIC_LOGGER.clearInvocations();
    }

    @Test
    public void logsDefaultMetrics() {
        Result result = new FormatResult(
                new ActionContext("did", "TestFormatActionName", "flow", "flow", "host", "1.0.0"), "filename");
        ActionMetricsLogger.logMetrics(ActionType.FORMAT, result);

        verifyCoreMetrics(result.getContext().getName(), "format", "files_completed");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void logsEgressMetrics() {
        Result result = new EgressResult(
                new ActionContext("did", "TestEgressActionName", "flow", "flow", "host", "1.0.0"), "destination", 123L);
        ActionMetricsLogger.logMetrics(ActionType.EGRESS, result);

        verifyCoreMetrics(result.getContext().getName(), "egress", "files_completed");

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("egress"), eq("did"), eq("flow"), eq("files_out"),
                eq(1L), mapArgumentCaptor.capture()));
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("egress"), eq("did"), eq("flow"), eq("bytes_out"),
                eq(123L), mapArgumentCaptor.capture()));

        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(2, tags.size());
        assertEquals(result.getContext().getName(), tags.get("action"));
        assertEquals("destination", tags.get("endpoint"));
    }

    @Test
    public void logsErrorMetrics() {
        Result result = new ErrorResult(new ActionContext("did", "TestEgressActionName", "flow", "flow", "host",
                "1.0.0"), "error mesg", new Throwable("exception"));
        ActionMetricsLogger.logMetrics(ActionType.EGRESS, result);

        verifyCoreMetrics(result.getContext().getName(), "egress", "files_errored");
    }

    @Test
    public void logsFilterMetrics() {
        Result result = new FilterResult(
                new ActionContext("did", "TestEgressActionName", "flow", "flow", "host", "1.0.0"), "message");
        ActionMetricsLogger.logMetrics(ActionType.EGRESS, result);

        verifyCoreMetrics(result.getContext().getName(), "egress", "files_filtered");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyCoreMetrics(String actionName, String actionType, String metric) {
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);

        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq(actionType), eq("did"), eq("flow"), eq("files_in"),
                eq(1L), mapArgumentCaptor.capture()));
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq(actionType), eq("did"), eq("flow"), eq(metric),
                eq(1L), mapArgumentCaptor.capture()));

        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(1, tags.size());
        assertEquals(actionName, tags.get("action"));
    }
}
