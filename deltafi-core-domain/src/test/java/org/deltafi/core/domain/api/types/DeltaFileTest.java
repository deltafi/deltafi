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
package org.deltafi.core.domain.api.types;

import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeltaFileTest {
    @Test
    void testSourceMetadata() {
        DeltaFile deltaFile = DeltaFile.newBuilder()
                .sourceInfo(new SourceInfo(null, null,
                        List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))))
                .build();

        assertEquals("value1", deltaFile.sourceMetadata("key1"));
        assertEquals("value1", deltaFile.sourceMetadata("key1", "default"));
        assertEquals("value2", deltaFile.sourceMetadata("key2"));
        assertEquals("value2", deltaFile.sourceMetadata("key2", "default"));
        assertNull(deltaFile.sourceMetadata("key3"));
        assertEquals("default", deltaFile.sourceMetadata("key3", "default"));
    }

    @Test
    void testCalculateBytes() {
        ContentReference contentReference1 = new ContentReference("uuid1", 0, 500, "did1", "*/*");
        ContentReference contentReference2 = new ContentReference("uuid1", 400, 200, "did1", "*/*");
        ContentReference contentReference3 = new ContentReference("uuid1", 200, 200, "did1", "*/*");
        ContentReference contentReference4 = new ContentReference("uuid2", 5, 200, "did1", "*/*");

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .protocolStack(List.of(
                        new ProtocolLayer("type", "action", List.of(
                                new Content("name", Collections.emptyList(), contentReference1),
                                new Content("name2", Collections.emptyList(), contentReference2)), Collections.emptyList()),
                        new ProtocolLayer("type2", "action2", List.of(
                                new Content("name3", Collections.emptyList(), contentReference3)), Collections.emptyList())
                ))
                .formattedData(List.of(
                        FormattedData.newBuilder().contentReference(contentReference4).build()
                ))
                .build();

        deltaFile.calculateTotalBytes();
        assertEquals(800, deltaFile.getTotalBytes());

    }

    @Test
    void testRetryErrors() {
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
                .build();

        List<String> retried = deltaFile.retryErrors();
        assertEquals(List.of("action1", "action3"), retried);

        assertEquals(3, deltaFile.getActions().size());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(0).getState());
        assertEquals(ActionState.COMPLETE, deltaFile.getActions().get(1).getState());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(2).getState());
    }
}
