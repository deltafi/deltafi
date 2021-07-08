package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.types.DeltaFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EgressActionTest {
    public static class MyEgressAction extends EgressAction {
        @Override
        public Result execute(DeltaFile deltaFile) {
            return null;
        }
    }

    @Test
    void testFlow() {
        MyEgressAction egressAction = new MyEgressAction();
        egressAction.name = "MyEgressAction";
        assertEquals("my", egressAction.flow());
    }
}