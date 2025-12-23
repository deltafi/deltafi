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
package org.deltafi.core.types.snapshot;

import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.generated.types.RateLimit;
import org.deltafi.core.generated.types.RateLimitUnit;
import org.deltafi.core.types.RestDataSource;
import org.deltafi.core.util.FlowBuilders;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class RestDataSourceSnapshotTest {

    @Test
    void testCreateSnapshotWithoutRateLimit() {
        RestDataSource restDataSource = FlowBuilders.buildDataSource("test-source");
        restDataSource.setFlowStatus(running());
        restDataSource.setTestMode(false);
        restDataSource.setMaxErrors(10);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(restDataSource);

        assertEquals("RestDataSource", snapshot.getName());
        assertTrue(snapshot.isRunning());
        assertFalse(snapshot.isTestMode());
        assertEquals("test-source", snapshot.getTopic());
        assertEquals(10, snapshot.getMaxErrors());
        assertNull(snapshot.getRateLimit());
    }

    @Test
    void testCreateSnapshotWithFilesRateLimit() {
        RestDataSource restDataSource = FlowBuilders.buildDataSource("test-source-files");
        restDataSource.setFlowStatus(new FlowStatus(FlowState.STOPPED, new ArrayList<>(), false, true, false));
        restDataSource.setTestMode(true);
        restDataSource.setMaxErrors(5);

        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(100L)
                .durationSeconds(60)
                .build();
        restDataSource.setRateLimit(rateLimit);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(restDataSource);

        assertEquals("RestDataSource", snapshot.getName());
        assertFalse(snapshot.isRunning());
        assertTrue(snapshot.isTestMode());
        assertEquals("test-source-files", snapshot.getTopic());
        assertEquals(5, snapshot.getMaxErrors());
        assertNotNull(snapshot.getRateLimit());
        assertEquals(RateLimitUnit.FILES, snapshot.getRateLimit().getUnit());
        assertEquals(100L, snapshot.getRateLimit().getMaxAmount());
        assertEquals(60, snapshot.getRateLimit().getDurationSeconds());
    }

    @Test
    void testCreateSnapshotWithBytesRateLimit() {
        RestDataSource restDataSource = FlowBuilders.buildDataSource("test-source-bytes");
        restDataSource.setFlowStatus(running());
        restDataSource.setTestMode(false);
        restDataSource.setMaxErrors(15);

        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1048576L) // 1MB
                .durationSeconds(300) // 5 minutes
                .build();
        restDataSource.setRateLimit(rateLimit);

        RestDataSourceSnapshot snapshot = new RestDataSourceSnapshot(restDataSource);

        assertEquals("RestDataSource", snapshot.getName());
        assertTrue(snapshot.isRunning());
        assertFalse(snapshot.isTestMode());
        assertEquals("test-source-bytes", snapshot.getTopic());
        assertEquals(15, snapshot.getMaxErrors());
        assertNotNull(snapshot.getRateLimit());
        assertEquals(RateLimitUnit.BYTES, snapshot.getRateLimit().getUnit());
        assertEquals(1048576L, snapshot.getRateLimit().getMaxAmount());
        assertEquals(300, snapshot.getRateLimit().getDurationSeconds());
    }

    @Test
    void testSnapshotEqualityWithNullRateLimit() {
        RestDataSource restDataSource1 = FlowBuilders.buildDataSource("test-source");
        restDataSource1.setFlowStatus(running());
        restDataSource1.setTestMode(false);
        restDataSource1.setTopic("test-topic");
        restDataSource1.setMaxErrors(10);
        restDataSource1.setRateLimit(null);

        RestDataSource restDataSource2 = FlowBuilders.buildDataSource("test-source");
        restDataSource2.setFlowStatus(running());
        restDataSource2.setTestMode(false);
        restDataSource2.setTopic("test-topic");
        restDataSource2.setMaxErrors(10);
        restDataSource2.setRateLimit(null);

        RestDataSourceSnapshot snapshot1 = new RestDataSourceSnapshot(restDataSource1);
        RestDataSourceSnapshot snapshot2 = new RestDataSourceSnapshot(restDataSource2);

        assertEquals(snapshot1, snapshot2);
        assertEquals(snapshot1.hashCode(), snapshot2.hashCode());
    }

    @Test
    void testSnapshotEqualityWithSameRateLimit() {
        RestDataSource restDataSource1 = FlowBuilders.buildDataSource("test-source");
        restDataSource1.setFlowStatus(running());
        restDataSource1.setTestMode(false);
        restDataSource1.setTopic("test-topic");
        restDataSource1.setMaxErrors(10);

        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();
        restDataSource1.setRateLimit(rateLimit);

        RestDataSource restDataSource2 = FlowBuilders.buildDataSource("test-source");
        restDataSource2.setFlowStatus(running());
        restDataSource2.setTestMode(false);
        restDataSource2.setTopic("test-topic");
        restDataSource2.setMaxErrors(10);
        restDataSource2.setRateLimit(rateLimit);

        RestDataSourceSnapshot snapshot1 = new RestDataSourceSnapshot(restDataSource1);
        RestDataSourceSnapshot snapshot2 = new RestDataSourceSnapshot(restDataSource2);

        assertEquals(snapshot1, snapshot2);
        assertEquals(snapshot1.hashCode(), snapshot2.hashCode());
    }

    @Test
    void testSnapshotInequalityNullVsRateLimit() {
        RestDataSource restDataSource1 = FlowBuilders.buildDataSource("test-source");
        restDataSource1.setMaxErrors(10);
        restDataSource1.setRateLimit(null);

        RestDataSource restDataSource2 = FlowBuilders.buildDataSource("test-source");
        restDataSource2.setMaxErrors(10);

        RateLimit rateLimit = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();
        restDataSource2.setRateLimit(rateLimit);

        RestDataSourceSnapshot snapshot1 = new RestDataSourceSnapshot(restDataSource1);
        RestDataSourceSnapshot snapshot2 = new RestDataSourceSnapshot(restDataSource2);

        assertNotEquals(snapshot1, snapshot2);
    }

    @Test
    void testSnapshotInequalityDifferentRateLimits() {
        RestDataSource restDataSource1 = FlowBuilders.buildDataSource("test-source");
        restDataSource1.setMaxErrors(10);

        RateLimit rateLimit1 = RateLimit.newBuilder()
                .unit(RateLimitUnit.FILES)
                .maxAmount(50L)
                .durationSeconds(120)
                .build();
        restDataSource1.setRateLimit(rateLimit1);

        RestDataSource restDataSource2 = FlowBuilders.buildDataSource("test-source");
        restDataSource2.setMaxErrors(10);

        RateLimit rateLimit2 = RateLimit.newBuilder()
                .unit(RateLimitUnit.BYTES)
                .maxAmount(1000L)
                .durationSeconds(60)
                .build();
        restDataSource2.setRateLimit(rateLimit2);

        RestDataSourceSnapshot snapshot1 = new RestDataSourceSnapshot(restDataSource1);
        RestDataSourceSnapshot snapshot2 = new RestDataSourceSnapshot(restDataSource2);

        assertNotEquals(snapshot1, snapshot2);
    }

    private FlowStatus running() {
        return new FlowStatus(FlowState.RUNNING, new ArrayList<>(), false, true, false);
    }
}