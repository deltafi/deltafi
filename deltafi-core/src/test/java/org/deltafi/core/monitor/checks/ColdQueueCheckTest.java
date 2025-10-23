/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.monitor.checks;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

class ColdQueueCheckTest {


    @Test
    void testAverage() {
        ColdQueueCheck.ColdQueueHistory history = new ColdQueueCheck.ColdQueueHistory();
        history.add(10);
        assertEquals(0, history.average());
        history.add(20);
        assertEquals(10, history.average());
        history.add(30);
        assertEquals(15, history.average());
    }

    @Test
    void testBurstThenGrowing() {
        ColdQueueCheck.ColdQueueHistory history = new ColdQueueCheck.ColdQueueHistory();
        history.add(100);
        assertFalse(history.computeIsWarning(1, 1000));
        assertTrue(history.computeIsWarning(1, 10));
        // growing but below minimum
        history.add(200);
        assertFalse(history.computeIsWarning(250, 1000));
        // growing
        history.add(300);
        assertTrue(history.computeIsWarning(250, 1000));
    }

    @Test
    void testShrinkAndGrow() {
        ColdQueueCheck.ColdQueueHistory history = new ColdQueueCheck.ColdQueueHistory();
        history.add(300);
        history.add(200);
        assertFalse(history.computeIsWarning(100, 1000));
        // avg is 250; 200 is less than 250
        history.add(200);
        assertFalse(history.computeIsWarning(100, 1000));
        // avg is 233
        history.add(233);
        assertTrue(history.computeIsWarning(100, 1000));
    }

    @Test
    void testAgeOff() {
        Map<String, ColdQueueCheck.ColdQueueHistory> previous = new HashMap<>();
        ColdQueueCheck.ColdQueueHistory cqHistory = new ColdQueueCheck.ColdQueueHistory();
        cqHistory.add(100);
        for (int i = 0; i < ColdQueueCheck.MAX_SIZE_HISTORY - 1; i++) {
            cqHistory.add(1);
        }
        assertEquals(12, cqHistory.historySize());
        // 111 / 11 =~ 10
        assertEquals(10, cqHistory.average());
        assertEquals(12, cqHistory.historySize());
        // 100 is removed, everything else is a '1'
        cqHistory.add(1);
        assertEquals(1, cqHistory.average());
    }

    @Test
    void testUpdateHistory() {
        final String KEY_A = "a";
        final String KEY_B = "b";
        final String KEY_C = "c";

        Map<String, ColdQueueCheck.ColdQueueHistory> previous = new HashMap<>();

        ColdQueueCheck.ColdQueueHistory historyA = new ColdQueueCheck.ColdQueueHistory();
        historyA.add(100);
        previous.put(KEY_A, historyA);

        ColdQueueCheck.ColdQueueHistory historyB = new ColdQueueCheck.ColdQueueHistory();
        historyB.add(100);
        previous.put(KEY_B, historyB);

        // "a" is updated; "b" is removed; "c" is added
        ColdQueueCheck.updateHistory(previous, Map.of(
                KEY_A, 200,
                KEY_C, 50));

        assertEquals(2, previous.get(KEY_A).historySize());
        assertFalse(previous.containsKey(KEY_B));
        assertEquals(1, previous.get(KEY_C).historySize());

        ColdQueueCheck.updateHistory(previous, Map.of(KEY_C, 50));

        assertFalse(previous.containsKey(KEY_A));
        assertFalse(previous.containsKey(KEY_B));
        assertEquals(2, previous.get(KEY_C).historySize());
    }
}
