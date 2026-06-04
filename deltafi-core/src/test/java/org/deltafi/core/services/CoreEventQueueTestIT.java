/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import lombok.SneakyThrows;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.types.WrappedActionInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CoreEventQueueTestIT {

    @Container
    public static final GenericContainer<?> valkeyContainer = new GenericContainer<>(DockerImageName.parse("valkey/valkey:9.0.0-alpine"))
            .withExposedPorts(6379);

    CoreEventQueue coreEventQueue;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        String url = "redis://" + valkeyContainer.getHost() + ":" + valkeyContainer.getMappedPort(6379);
        ValkeyKeyedBlockingQueue blockingQueue = new ValkeyKeyedBlockingQueue(url, null, 1);
        this.coreEventQueue = new CoreEventQueue(blockingQueue, new TestClock());
    }

    /**
     * Control case: the dedup check works when the target flowId sits in the first ZSCAN batch.
     */
    @Test
    void dedupCatchesDuplicateInSmallQueue() {
        String queue = "small-queue";
        UUID dupFlowId = UUID.randomUUID();

        // The duplicate flowId is the oldest item in the queue (smallest score / head of the ZSET),
        // which is exactly the population requeue re-pushes: items older than the requeue threshold.
        OffsetDateTime base = OffsetDateTime.now().minusMinutes(10);
        List<WrappedActionInput> filler = new ArrayList<>();
        filler.add(input(queue, dupFlowId, base));
        for (int i = 0; i < 4; i++) {
            filler.add(input(queue, UUID.randomUUID(), base.plusSeconds(i + 1)));
        }
        coreEventQueue.putActions(filler, false);

        long before = coreEventQueue.size(queue);

        // re-queue an item whose flowId is already waiting in the queue
        coreEventQueue.putActions(List.of(input(queue, dupFlowId, base)), true);

        assertThat(coreEventQueue.size(queue))
                .as("dedup should skip a flowId already waiting in the queue")
                .isEqualTo(before);
    }

    /**
     * Bug reproduction: with a deep queue, the single-batch ZSCAN in
     * {@link ValkeyKeyedBlockingQueue#exists} fails to find the present flowId, so the duplicate
     * is enqueued and the queue grows. This test should fail until the dedup check is fixed.
     */
    @Test
    void dedupCatchesDuplicateInLargeQueue() {
        String queue = "large-queue";
        UUID dupFlowId = UUID.randomUUID();

        // The duplicate is the oldest item (head of the ZSET) - the realistic requeue candidate -
        // but it is buried behind ~5000 newer items, so a single ZSCAN batch never reaches it.
        OffsetDateTime base = OffsetDateTime.now().minusMinutes(10);
        List<WrappedActionInput> filler = new ArrayList<>();
        filler.add(input(queue, dupFlowId, base));
        for (int i = 0; i < 5000; i++) {
            filler.add(input(queue, UUID.randomUUID(), base.plusSeconds(i + 1)));
        }
        coreEventQueue.putActions(filler, false);

        long before = coreEventQueue.size(queue);

        // re-queue an item whose flowId is already waiting at the head of the queue
        coreEventQueue.putActions(List.of(input(queue, dupFlowId, base)), true);

        assertThat(coreEventQueue.size(queue))
                .as("dedup should skip a flowId already waiting in the queue regardless of depth")
                .isEqualTo(before);
    }

    /**
     * Exemplary requeue case: a real requeue cycle re-pushes a *batch* of overdue actions, many of
     * which are still waiting in the queue. The original bug let nearly all of those duplicates
     * through (each cycle re-pushed them), so this asserts the realistic property: of a mixed batch,
     * every already-queued flowId is skipped and only the genuinely new ones are added. Pre-fix the
     * queue would grow by ~dupCount + newCount; post-fix it grows by exactly newCount.
     */
    @Test
    void dedupCatchesManyDuplicatesInLargeQueue() {
        String queue = "mixed-large-queue";
        OffsetDateTime base = OffsetDateTime.now().minusMinutes(10);

        List<UUID> queued = new ArrayList<>();
        List<WrappedActionInput> filler = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            UUID flowId = UUID.randomUUID();
            queued.add(flowId);
            filler.add(input(queue, flowId, base.plusSeconds(i)));
        }
        coreEventQueue.putActions(filler, false);
        long before = coreEventQueue.size(queue);

        // requeue batch: 100 flowIds already waiting (must be skipped) + 100 brand-new (must be added)
        List<WrappedActionInput> requeueBatch = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requeueBatch.add(input(queue, queued.get(i), base.plusSeconds(i)));   // duplicate
        }
        int newCount = 100;
        for (int i = 0; i < newCount; i++) {
            requeueBatch.add(input(queue, UUID.randomUUID(), OffsetDateTime.now()));   // new
        }
        coreEventQueue.putActions(requeueBatch, true);

        assertThat(coreEventQueue.size(queue))
                .as("every already-queued flowId skipped, only the new ones added")
                .isEqualTo(before + newCount);
    }

    /**
     * Builds a WrappedActionInput with the fields {@code putActions} reads. The did is always fresh
     * so that a duplicate flowId still serializes to a distinct ZSET member (matching real requeue,
     * where each re-queue mints a new ActionInput) - this is what lets the missed-dedup grow the queue.
     */
    private WrappedActionInput input(String queue, UUID flowId, OffsetDateTime created) {
        ActionContext context = ActionContext.builder()
                .did(UUID.randomUUID())
                .flowId(flowId)
                .actionName("SomeAction")
                .build();

        return WrappedActionInput.builder()
                .queueName(queue)
                .actionContext(context)
                .actionCreated(created)
                .coldQueued(false)
                .build();
    }
}
