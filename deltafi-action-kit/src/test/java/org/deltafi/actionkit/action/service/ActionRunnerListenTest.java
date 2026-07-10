/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.BuildInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Deterministic tests of the polling loop's retry wiring. No wall-clock dependence:
 * the reschedule is stubbed via the {@code scheduleListen} seam (which breaks the otherwise
 * infinite self-rescheduling loop), the queue is mocked to fail immediately, and the loop is
 * driven with a zero delay so {@code Thread.sleep(0)} is an instant no-op.
 */
class ActionRunnerListenTest {

    private ActionEventQueue actionEventQueue;
    private ActionRunner runner; // spy
    private final TransformTestAction action = new TransformTestAction();
    private ListAppender<ILoggingEvent> logs;
    private Logger actionRunnerLogger;

    @BeforeEach
    void setup() {
        actionEventQueue = mock(ActionEventQueue.class);
        ActionRunner real = new ActionRunner(actionEventQueue, new ActionsProperties(),
                mock(HostnameService.class), mock(BuildInfo.class), mock(ContentStorageService.class),
                List.of(), "app", null);
        runner = spy(real);
        doNothing().when(runner).scheduleListen(any(), anyLong());

        logs = new ListAppender<>();
        logs.start();
        // logback-test.xml sets org.deltafi to OFF; re-enable this logger so we can assert on it
        actionRunnerLogger = (Logger) LoggerFactory.getLogger(ActionRunner.class);
        actionRunnerLogger.setLevel(Level.ALL);
        actionRunnerLogger.addAppender(logs);
    }

    @AfterEach
    void teardown() {
        actionRunnerLogger.detachAppender(logs);
        actionRunnerLogger.setLevel(null); // restore inherited (OFF) level
        Thread.interrupted(); // clear any interrupt flag so it can't leak between tests
    }

    private boolean logged(String fragment) {
        return logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains(fragment));
    }

    @Test
    void pollFailureReschedulesWithEscalatedDelayAndNoShutdownLog() {
        when(actionEventQueue.takeAction(anyString())).thenThrow(new RuntimeException("valkey down"));

        runner.listen(action, 0L);

        verify(runner).scheduleListen(action, 200L); // nextBackoffMs(0, false) = base*2
        assertThat(logged("could not reach the event queue")).isTrue();
        assertThat(logged("Shutting down action thread")).isFalse();
    }

    @Test
    void blipAfterProgressReschedulesAtBaseDelay() {
        ActionInput input = mock(ActionInput.class);
        when(input.getActionContext()).thenReturn(mock(ActionContext.class));
        when(input.getReturnAddress()).thenReturn("addr");
        when(actionEventQueue.takeAction(anyString()))
                .thenReturn(input)
                .thenThrow(new RuntimeException("valkey blip"));
        doNothing().when(runner).executeAction(any(), any(), any());

        runner.listen(action, 0L);

        verify(runner).scheduleListen(action, 100L); // madeProgress -> reset to base
    }

    @Test
    void interruptShutsDownWithoutReschedule() {
        Thread.currentThread().interrupt();

        runner.listen(action, 0L);

        verify(runner, never()).scheduleListen(any(), anyLong());
        assertThat(logged("Shutting down action thread")).isTrue();
    }
}
