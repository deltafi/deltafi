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
package org.deltafi.core.types;

import org.assertj.core.api.Assertions;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeltaFileTest {
    @Test
    void testAcknowledgeClearsAutoResume() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime now2 = OffsetDateTime.now();
        Action action1 = Action.builder()
                .name("action1")
                .state(ActionState.ERROR)
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();
        DeltaFileFlow flow = DeltaFileFlow.builder()
                .actions(List.of(action1))
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .flows(new LinkedHashSet<>(List.of(flow)))
                .build();

        deltaFile.acknowledgeErrors(now2, "reason");

        assertNull(action1.getNextAutoResumeReason());
        assertNull(action1.getNextAutoResume());
        assertEquals("reason", flow.getErrorAcknowledgedReason());
        assertEquals(now2, flow.getErrorAcknowledged());
    }

    @Test
    void testErrorCanBeCancelled() {
        OffsetDateTime now = OffsetDateTime.now();
        Action action1 = Action.builder()
                .name("action1")
                .state(ActionState.ERROR)
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();
        DeltaFileFlow flow = DeltaFileFlow.builder()
                .actions(List.of(action1))
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .flows(new LinkedHashSet<>(List.of(flow)))
                .stage(DeltaFileStage.ERROR)
                .build();

        assertTrue(deltaFile.canBeCancelled());
        deltaFile.cancel(OffsetDateTime.now());
        assertEquals(deltaFile.getStage(), DeltaFileStage.CANCELLED);
        assertNull(action1.getNextAutoResume());
        assertNull(action1.getNextAutoResumeReason());
        assertFalse(deltaFile.canBeCancelled());
    }

    @Test
    void testIngressCanBeCancelled() {
        Action action1 = Action.builder()
                .name("action1")
                .state(ActionState.QUEUED)
                .build();
        DeltaFileFlow flow = DeltaFileFlow.builder()
                .actions(List.of(action1))
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .flows(new LinkedHashSet<>(List.of(flow)))
                .stage(DeltaFileStage.IN_FLIGHT)
                .build();

        assertTrue(deltaFile.canBeCancelled());
        assertFalse(flow.queuedActions().isEmpty());
        deltaFile.cancel(OffsetDateTime.now());
        assertTrue(flow.queuedActions().isEmpty());

        assertEquals(deltaFile.getStage(), DeltaFileStage.CANCELLED);
        assertNull(action1.getNextAutoResume());
        assertNull(action1.getNextAutoResumeReason());
        assertFalse(deltaFile.canBeCancelled());
    }

    @Test
    void testRetryErrors() {
        OffsetDateTime now = OffsetDateTime.now();
        Action action1 = Action.builder()
                .name("action1")
                .type(ActionType.TRANSFORM)
                .state(ActionState.ERROR)
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();
        DeltaFileFlow flow1 = DeltaFileFlow.builder()
                .name("flow1")
                .actions(new ArrayList<>(List.of(action1)))
                .build();
        Action action2 = Action.builder()
                .name("action2")
                .type(ActionType.TRANSFORM)
                .state(ActionState.COMPLETE)
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();
        DeltaFileFlow flow2 = DeltaFileFlow.builder()
                .name("flow2")
                .actions(new ArrayList<>(List.of(action2)))
                .build();
        Action action3 = Action.builder()
                .name("action3")
                .type(ActionType.TRANSFORM)
                .state(ActionState.ERROR)
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();
        DeltaFileFlow flow3 = DeltaFileFlow.builder()
                .name("flow3")
                .actions(new ArrayList<>(List.of(action3)))
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .flows(new LinkedHashSet<>(List.of(flow1, flow2, flow3)))
                .build();

        List<DeltaFileFlow> retried = deltaFile.resumeErrors(List.of(
                new ResumeMetadata("flow1", "action1", Map.of("a", "b"), List.of("c", "d")),
                new ResumeMetadata("flow3", "action3", Map.of("x", "y"), List.of("z"))), now);
        assertNull(action1.getNextAutoResume());
        assertNotNull(action2.getNextAutoResume());
        assertNull(action3.getNextAutoResume());
        assertNull(action1.getNextAutoResumeReason());
        assertEquals("policy-name", action2.getNextAutoResumeReason());
        assertNull(action3.getNextAutoResumeReason());
        assertEquals(2, retried.size());

        assertEquals(ActionState.RETRIED, action1.getState());
        assertEquals(Map.of("a", "b"), action1.getMetadata());
        assertEquals(List.of("c", "d"), action1.getDeleteMetadataKeys());
        assertEquals(ActionState.COMPLETE, action2.getState());
        assertEquals(ActionState.RETRIED, action3.getState());
        assertEquals(Map.of("x", "y"), action3.getMetadata());
        assertEquals(List.of("z"), action3.getDeleteMetadataKeys());
    }

    @Test
    void testRecalculateBytes() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        UUID did1 = UUID.randomUUID();
        UUID did2 = UUID.randomUUID();
        Content content1 = new Content("content1", "*/*", List.of(new Segment(uuid1, 0, 500, did1)));
        Content content2 = new Content("content1", "*/*", List.of(new Segment(uuid1, 400, 200, did1)));
        Content content3 = new Content("content1", "*/*", List.of(new Segment(uuid1, 200, 200, did1)));
        Content content4 = new Content("content1", "*/*", List.of(new Segment(uuid2, 5, 200, did1)));
        Content content5 = new Content("content1", "*/*", List.of(new Segment(uuid3, 5, 200, did2)));

        DeltaFile deltaFile = DeltaFile.builder()
                .flows(Set.of(DeltaFileFlow.builder().actions(List.of(
                        Action.builder().content(List.of(content1, content2)).build(),
                        Action.builder().content(List.of(content3)).build(),
                        Action.builder().content(List.of(content4)).build(),
                        Action.builder().content(List.of(content5)).build())
                ).build()))
                .did(did1)
                .build();

        deltaFile.recalculateBytes();
        assertEquals(1000, deltaFile.getReferencedBytes());
        assertEquals(800, deltaFile.getTotalBytes());
    }

    @Test
    void testPendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        Assertions.assertThat(deltaFile.pendingAnnotations(null)).isEmpty();
        Assertions.assertThat(deltaFile.pendingAnnotations(Set.of())).isEmpty();

        deltaFile.addAnnotations(Map.of("a", "1"));
        Set<String> expected = Set.of("a", "b", "c");

        Assertions.assertThat(deltaFile.pendingAnnotations(expected)).hasSize(2).contains("b", "c");
        deltaFile.addAnnotations(Map.of("b", "2", "c", ""));
        Assertions.assertThat(deltaFile.pendingAnnotations(expected)).isEmpty();
    }
}
