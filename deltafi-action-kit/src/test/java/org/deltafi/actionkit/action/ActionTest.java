package org.deltafi.actionkit.action;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventType;
import org.deltafi.core.domain.generated.types.SourceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
public class ActionTest {
    private static class TestAction extends SimpleAction {
        @Override
        public Result execute(DeltaFile deltaFile, ActionParameters params) {
            return null;
        }
    }

    @InjectMocks
    TestAction testAction;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testLogFilesProcessedMetric() {
        MockedStatic<MetricLogger> mockedMetricLogger = Mockito.mockStatic(MetricLogger.class);

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .did("did")
                .sourceInfo(SourceInfo.newBuilder().flow("flow").build())
                .build();

        testAction.logFilesProcessedMetric(ActionEventType.FORMAT, deltaFile);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        mockedMetricLogger.verify(() -> MetricLogger.logMetric(eq("format"), eq("did"), eq("flow"),
                eq("files_processed"), eq(1L), mapArgumentCaptor.capture()));

        Map<String, String> tags = (Map<String, String>) mapArgumentCaptor.getValue();
        assertEquals(1, tags.size());
        assertEquals(TestAction.class.getSimpleName(), tags.get("action"));
    }
}
