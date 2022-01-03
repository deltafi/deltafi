package org.deltafi.core.domain.api.types;

import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

        assertEquals(5, deltaFile.getActions().size());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(0).getState());
        assertEquals(ActionState.COMPLETE, deltaFile.getActions().get(1).getState());
        assertEquals(ActionState.RETRIED, deltaFile.getActions().get(2).getState());
        assertEquals(ActionState.QUEUED, deltaFile.getActions().get(3).getState());
        assertEquals(ActionState.QUEUED, deltaFile.getActions().get(4).getState());
        assertEquals(deltaFile.getActions().get(0).getName(), deltaFile.getActions().get(3).getName());
        assertEquals(deltaFile.getActions().get(2).getName(), deltaFile.getActions().get(4).getName());
    }
}
