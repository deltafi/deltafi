package org.deltafi.actionkit.action.egress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EgressUtilityTest {

    @Test
    void testFlow() {
        assertEquals("my", EgressUtility.flow("MyEgressAction"));
    }
}