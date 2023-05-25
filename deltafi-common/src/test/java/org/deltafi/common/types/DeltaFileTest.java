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
package org.deltafi.common.types;

import org.assertj.core.api.Assertions;
import org.deltafi.common.content.Segment;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeltaFileTest {
    @Test
    void testSourceMetadata() {
        DeltaFile deltaFile = DeltaFile.newBuilder()
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
    void testRetryErrors() {
        OffsetDateTime now = OffsetDateTime.now();
        Action action1 = Action.newBuilder()
                .name("action1")
                .state(ActionState.ERROR)
                .build();
        Action action2 = Action.newBuilder()
                .name("action2")
                .state(ActionState.COMPLETE)
                .build();
        Action action3 = Action.newBuilder()
                .name("action3")
                .state(ActionState.ERROR)
                .build();

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .actions(new ArrayList<>(List.of(action1, action2, action3)))
                .nextAutoResume(now)
                .nextAutoResumeReason("policy-name")
                .build();

        List<String> retried = deltaFile.retryErrors();
        assertNull(deltaFile.getNextAutoResume());
        assertEquals("policy-name", deltaFile.getNextAutoResumeReason());
        assertEquals(List.of("action1", "action3"), retried);

        assertEquals(3, deltaFile.getActions().size());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(0).getState());
        assertEquals(ActionState.COMPLETE, deltaFile.getActions().get(1).getState());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(2).getState());
    }

    @Test
    void testRecalculateBytes() {
        Content content1 = new Content("content1", "*/*", List.of(new Segment("uuid1", 0, 500, "did1")));
        Content content2 = new Content("content1", "*/*", List.of(new Segment("uuid1", 400, 200, "did1")));
        Content content3 = new Content("content1", "*/*", List.of(new Segment("uuid1", 200, 200, "did1")));
        Content content4 = new Content("content1", "*/*", List.of(new Segment("uuid2", 5, 200, "did1")));
        Content content5 = new Content("content1", "*/*", List.of(new Segment("uuid3", 5, 200, "did2")));

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .actions(List.of(
                        Action.newBuilder().content(List.of(content1, content2)).build(),
                        Action.newBuilder().content(List.of(content3)).build()
                ))
                .formattedData(List.of(
                        FormattedData.newBuilder().content(content4).build(),
                        FormattedData.newBuilder().content(content5).build()
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
}
