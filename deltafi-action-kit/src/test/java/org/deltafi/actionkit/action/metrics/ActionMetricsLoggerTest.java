package org.deltafi.actionkit.action.metrics;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;

public class ActionMetricsLoggerTest {
    private static class TestAction extends Action<ActionParameters> {
        public TestAction() {
            super(ActionParameters.class, ActionEventType.FORMAT);
        }

        @Override
        public Result execute(DeltaFile deltaFile, ActionContext actionContext, ActionParameters params) {
            return null;
        }
    }

    private static class TestActionWithCustomMetrics extends Action<ActionParameters> {
        public TestActionWithCustomMetrics() {
            super(ActionParameters.class, ActionEventType.FORMAT);
        }

        @Override
        public Result execute(DeltaFile deltaFile, ActionContext actionContext, ActionParameters params) {
            return null;
        }

        @Override
        public Collection<Metric> generateMetrics(Result result) {
            return List.of(Metric.builder().name("metric1").value(5).tags(Map.of("a", "1", "b", "2")).build(),
                    Metric.builder().name("metric2").value(10).build());
        }
    }

    private static final MockedStatic<MetricLogger> MOCKED_METRIC_LOGGER = Mockito.mockStatic(MetricLogger.class);
    private static final Result RESULT = new FormatResult(ActionContext.builder().did("did").name("action").ingressFlow("flow").build(), "filename");

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void logsDefaultMetrics() {
        MOCKED_METRIC_LOGGER.clearInvocations();

        ActionMetricsLogger actionMetricsLogger = new ActionMetricsLogger(new TestAction());

        actionMetricsLogger.logMetrics(RESULT);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("format"), eq("did"), eq("flow"),
                eq("files_processed"), eq(1L), mapArgumentCaptor.capture()));

        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(1, tags.size());
        assertEquals("action", tags.get("action"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void logsOverriddenMetrics() {
        MOCKED_METRIC_LOGGER.clearInvocations();

        ActionMetricsLogger actionMetricsLogger = new ActionMetricsLogger(new TestActionWithCustomMetrics());

        actionMetricsLogger.logMetrics(RESULT);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);

        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("format"), eq("did"), eq("flow"),
                eq("metric1"), eq(5L), mapArgumentCaptor.capture()));
        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(3, tags.size());
        assertEquals("action", tags.get("action"));
        assertEquals("1", tags.get("a"));
        assertEquals("2", tags.get("b"));

        MOCKED_METRIC_LOGGER.verify(() -> MetricLogger.logMetric(eq("format"), eq("did"), eq("flow"),
                eq("metric2"), eq(10L), mapArgumentCaptor.capture()));
        tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(1, tags.size());
        assertEquals("action", tags.get("action"));
    }
}
