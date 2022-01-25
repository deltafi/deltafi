package org.deltafi.actionkit.action.metrics;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.core.domain.api.types.ActionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;

public class ActionMetricsLoggerTest {
    private static final MockedStatic<MetricLogger> MOCKED_METRIC_LOGGER = Mockito.mockStatic(MetricLogger.class);

    private static final Result EGRESS_RESULT = new EgressResult(
            new ActionContext("did", "egressaction", "flow", "flow", "host", "1.0.0"), "destination", 123L);
    private static final Result ERROR_RESULT = new ErrorResult(
            new ActionContext("did", "erroraction", "flow", "flow", "host", "1.0.0"), "error mesg", new Throwable("exception"));
    private static final Result FILTER_RESULT = new FilterResult(
            new ActionContext("did", "filteraction", "flow", "flow", "host", "1.0.0"), "message");
    private static final Result FORMAT_RESULT = new FormatResult(
            new ActionContext("did", "formataction", "flow", "flow", "host", "1.0.0"), "filename");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void logsDefaultMetrics() {
        MOCKED_METRIC_LOGGER.clearInvocations();
        ActionMetricsLogger.logMetrics(FORMAT_RESULT);
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verifyCoreMetrics(mapArgumentCaptor, "format", "files_completed");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void logsEgressMetrics() {
        MOCKED_METRIC_LOGGER.clearInvocations();

        ActionMetricsLogger.logMetrics(EGRESS_RESULT);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verifyCoreMetrics(mapArgumentCaptor, "egress", "files_completed");

        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("egress"),
                eq("did"), eq("flow"), eq("files_out"), eq(1L),
                mapArgumentCaptor.capture()));
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("egress"),
                eq("did"), eq("flow"), eq("bytes_out"), eq(123L),
                mapArgumentCaptor.capture()));

        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(2, tags.size());
        assertEquals("egressaction", tags.get("action"));
        assertEquals("destination", tags.get("endpoint"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void logsErrorMetrics() {
        MOCKED_METRIC_LOGGER.clearInvocations();
        ActionMetricsLogger.logMetrics(ERROR_RESULT);
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verifyCoreMetrics(mapArgumentCaptor, "error", "files_errored");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void logsFilterMetrics() {
        MOCKED_METRIC_LOGGER.clearInvocations();
        ActionMetricsLogger.logMetrics(FILTER_RESULT);
        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verifyCoreMetrics(mapArgumentCaptor, "filter", "files_filtered");
    }

    private void verifyCoreMetrics(ArgumentCaptor<Map> mapArgumentCaptor, String actionType, String metric) {
        String actionName = actionType + "action";
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq(actionType),
                eq("did"), eq("flow"), eq("files_in"), eq(1L),
                mapArgumentCaptor.capture()));
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq(actionType),
                eq("did"), eq("flow"), eq(metric), eq(1L),
                mapArgumentCaptor.capture()));

        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(1, tags.size());
        assertEquals(actionName, tags.get("action"));
    }
}
