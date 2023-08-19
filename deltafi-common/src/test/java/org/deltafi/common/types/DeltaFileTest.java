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
package org.deltafi.common.types;

import org.assertj.core.api.Assertions;
import org.deltafi.common.content.Segment;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeltaFileTest {
    @Test
    void testSourceMetadata() {
        DeltaFile deltaFile = DeltaFile.builder()
                .sourceInfo(new SourceInfo(null, null,
                        Map.of("key1", "value1", "key2", "value2")))
                .build();

        assertEquals("value1", deltaFile.sourceMetadata("key1"));
        assertEquals("value1", deltaFile.sourceMetadata("key1", "default"));
        assertEquals("value2", deltaFile.sourceMetadata("key2"));
        assertEquals("value2", deltaFile.sourceMetadata("key2", "default"));
        assertNull(deltaFile.sourceMetadata("key3"));
        assertEquals("default", deltaFile.sourceMetadata("key3", "default"));
    }

    @Test
    void testAcknowledgeClearsAutoResume() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime now2 = OffsetDateTime.now();
        Action action1 = Action.builder()
                .name("action1")
                .state(ActionState.ERROR)
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .actions(new ArrayList<>(List.of(action1)))
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();

        deltaFile.acknowledgeError(now2, "reason");

        assertNull(deltaFile.getNextAutoResumeReason());
        assertNull(deltaFile.getNextAutoResume());
        assertEquals("reason", deltaFile.getErrorAcknowledgedReason());
        assertEquals(now2, deltaFile.getErrorAcknowledged());
    }

    @Test
    void testRetryErrors() {
        OffsetDateTime now = OffsetDateTime.now();
        Action action1 = Action.builder()
                .name("action1")
                .type(ActionType.TRANSFORM)
                .flow("flow1")
                .state(ActionState.ERROR)
                .build();
        Action action2 = Action.builder()
                .name("action2")
                .type(ActionType.LOAD)
                .flow("flow2")
                .state(ActionState.COMPLETE)
                .build();
        Action action3 = Action.builder()
                .name("action3")
                .type(ActionType.LOAD)
                .flow("flow3")
                .state(ActionState.ERROR)
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .actions(new ArrayList<>(List.of(action1, action2, action3)))
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();

        List<String> retried = deltaFile.retryErrors(List.of(
                new ResumeMetadata("flow1", "action1", Map.of("a", "b"), List.of("c", "d")),
                new ResumeMetadata("flow3", "action3", Map.of("x", "y"), List.of("z"))));
        assertNull(deltaFile.getNextAutoResume());
        assertEquals("policy-name", deltaFile.getNextAutoResumeReason());
        assertEquals(List.of("action1", "action3"), retried);

        assertEquals(3, deltaFile.getActions().size());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(0).getState());
        assertEquals(Map.of("a", "b"), deltaFile.getActions().get(0).getMetadata());
        assertEquals(List.of("c", "d"), deltaFile.getActions().get(0).getDeleteMetadataKeys());
        assertEquals(ActionState.COMPLETE, deltaFile.getActions().get(1).getState());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(2).getState());
        assertEquals(Map.of("x", "y"), deltaFile.getActions().get(2).getMetadata());
        assertEquals(List.of("z"), deltaFile.getActions().get(2).getDeleteMetadataKeys());
    }

    @Test
    void testFirstActionError() {
        Action action1 = Action.builder()
                .name("action1")
                .state(ActionState.ERROR)
                .build();
        Action action2 = Action.builder()
                .name("action2")
                .state(ActionState.COMPLETE)
                .build();
        Action action3 = Action.builder()
                .name("action3")
                .state(ActionState.ERROR)
                .build();

        DeltaFile deltaFile = DeltaFile.builder()
                .actions(new ArrayList<>(List.of(action1, action2, action3)))
                .build();

        Optional<Action> firstActionError = deltaFile.firstActionError();
        assertTrue(firstActionError.isPresent());
        assertEquals("action1", firstActionError.get().getName());

        DeltaFile deltaFile2 = DeltaFile.builder()
                .actions(new ArrayList<>(List.of( action2)))
                .build();

        assertFalse(deltaFile2.firstActionError().isPresent());
    }

    @Test
    void testRecalculateBytes() {
        Content content1 = new Content("content1", "*/*", List.of(new Segment("uuid1", 0, 500, "did1")));
        Content content2 = new Content("content1", "*/*", List.of(new Segment("uuid1", 400, 200, "did1")));
        Content content3 = new Content("content1", "*/*", List.of(new Segment("uuid1", 200, 200, "did1")));
        Content content4 = new Content("content1", "*/*", List.of(new Segment("uuid2", 5, 200, "did1")));
        Content content5 = new Content("content1", "*/*", List.of(new Segment("uuid3", 5, 200, "did2")));

        DeltaFile deltaFile = DeltaFile.builder()
                .actions(List.of(
                        Action.builder().content(List.of(content1, content2)).build(),
                        Action.builder().content(List.of(content3)).build(),
                        Action.builder().content(List.of(content4)).build(),
                        Action.builder().content(List.of(content5)).build()
                ))
                .did("did1")
                .build();

        deltaFile.recalculateBytes();
        assertEquals(1000, deltaFile.getReferencedBytes());
        assertEquals(800, deltaFile.getTotalBytes());
    }

    @Test
    void addPendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).isNull();

        // nothing should happen when an empty string is passed in
        deltaFile.addPendingAnnotationsForFlow("  ");
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).isNull();

        deltaFile.addPendingAnnotationsForFlow("flow");
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).hasSize(1).contains("flow");

        // nothing should happen when a null set is passed in
        deltaFile.addPendingAnnotationsForFlow(null);
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).hasSize(1).contains("flow");

        // values should be appended into the set
        deltaFile.addPendingAnnotationsForFlow("flow2");
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).hasSize(2).containsAll(List.of("flow", "flow2"));
    }

    @Test
    void updatePendingAnnotations() {
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setAnnotations(new HashMap<>(Map.of("a", "1", "b", "2", "d", "4")));

        deltaFile.updatePendingAnnotationsForFlows("flow", Set.of("c"));
        // no flows in pendingAnnotationsForFlow yet, it should remain null regardless of the set of keys passed in
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).isNull();

        deltaFile.setPendingAnnotationsForFlows(new HashSet<>(Set.of("flow")));
        deltaFile.updatePendingAnnotationsForFlows("flow", Set.of("c"));
        // no key of c exists yet, pendingAnnotationsForFlow should keep flow in the set
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).hasSize(1).contains("flow");

        deltaFile.addAnnotations(Map.of("c", "3"));
        deltaFile.updatePendingAnnotationsForFlows("flow", Set.of("c"));
        // key of c is added, the flow should be removed from pendingAnnotationForFlows, empty set is nulled out
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).isNull();

        deltaFile.setPendingAnnotationsForFlows(new HashSet<>(Set.of("flow", "flow2")));
        deltaFile.updatePendingAnnotationsForFlows("flow", Set.of("c"));
        // key of c is added, the flow should be removed from pendingAnnotationForFlows
        Assertions.assertThat(deltaFile.getPendingAnnotationsForFlows()).hasSize(1).contains("flow2");
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
