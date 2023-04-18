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

import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.Segment;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        ContentReference contentReference1 = new ContentReference("*/*", new Segment("uuid1", 0, 500, "did1"));
        ContentReference contentReference2 = new ContentReference("*/*", new Segment("uuid1", 400, 200, "did1"));
        ContentReference contentReference3 = new ContentReference("*/*", new Segment("uuid1", 200, 200, "did1"));
        ContentReference contentReference4 = new ContentReference("*/*", new Segment("uuid2", 5, 200, "did1"));
        ContentReference contentReference5 = new ContentReference("*/*", new Segment("uuid3", 5, 200, "did2"));

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .protocolStack(List.of(
                        new ProtocolLayer("action", List.of(
                                new Content("name", Collections.emptyMap(), contentReference1),
                                new Content("name2", Collections.emptyMap(), contentReference2)), Collections.emptyMap()),
                        new ProtocolLayer("action2", List.of(
                                new Content("name3", Collections.emptyMap(), contentReference3)), Collections.emptyMap())
                ))
                .formattedData(List.of(
                        FormattedData.newBuilder().contentReference(contentReference4).build(),
                        FormattedData.newBuilder().contentReference(contentReference5).build()
                ))
                .did("did1")
                .build();

        deltaFile.recalculateBytes();
        assertEquals(1000, deltaFile.getReferencedBytes());
        assertEquals(800, deltaFile.getTotalBytes());
    }
}
