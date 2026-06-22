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
// ABOUTME: Service for pushing and popping action events to a Valkey queue.
// ABOUTME: Handles action input/output, lookup table events, heartbeats, and long-running task tracking.
package org.deltafi.actionkit.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.lookup.LookupTableEvent;
import org.deltafi.common.lookup.LookupTableEventResult;
import org.deltafi.common.queue.valkey.SortedSetEntry;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.types.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.deltafi.common.action.EventQueueProperties.DGS_QUEUE;

/**
 * Service for pushing and popping action events to a valkey queue.
 */
@RequiredArgsConstructor
@Slf4j
public class ActionEventQueue {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder(
                    JsonFactory.builder()
                            .streamReadConstraints(StreamReadConstraints.builder()
                                    .maxStringLength(16 * 1024 * 1024)
                                    .build())
                            .build())
            .changeDefaultPropertyInclusion(incl -> incl
                    .withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .build();

    private final ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    /**
     * Request an ActionInput object from the ActionEvent queue for the specified action
     *
     * @param actionClassName Name of action for Action event request
     * @return next Action on the queue for the given action name
     * @throws JacksonException if the incoming event cannot be serialized
     */
    public ActionInput takeAction(String actionClassName) throws JacksonException {
        return OBJECT_MAPPER.readValue(valkeyKeyedBlockingQueue.take(actionClassName), ActionInput.class);
    }

    /**
     * Block until a lookup table event is available for any of the given table names.
     * @param lookupTableNames names of lookup tables to listen for
     * @return the next LookupTableEvent from the queue
     * @throws JacksonException if the incoming event cannot be deserialized
     */
    public LookupTableEvent takeLookupTableEvent(String... lookupTableNames) throws JacksonException {
        String[] prefixedLookupTableNames = Arrays.stream(lookupTableNames)
                .map(LookupTableEvent::buildKey)
                .toArray(String[]::new);
        return OBJECT_MAPPER.readValue(valkeyKeyedBlockingQueue.take(prefixedLookupTableNames), LookupTableEvent.class);
    }

    /**
     * Submit a lookup table event result back to the queue.
     * @param lookupTableEventResult the result to post
     * @throws JacksonException if the result cannot be serialized
     */
    public void putLookupTableResult(LookupTableEventResult lookupTableEventResult) throws JacksonException {
        valkeyKeyedBlockingQueue.put(new SortedSetEntry(lookupTableEventResult.getLookupTableEventId(),
                OBJECT_MAPPER.writeValueAsString(lookupTableEventResult), OffsetDateTime.now()));
    }

    private String queueName(String returnAddress) {
        String queueName = DGS_QUEUE;
        if (returnAddress != null) {
            queueName += "-" + returnAddress;
        }

        return queueName;
    }

    /**
     * Submit a result object for action processing
     *
     * @param result ActionEvent result to be posted to the action queue
     * @param returnAddress optional address suffix for the destination queue
     * @throws JacksonException if the outgoing event cannot be deserialized
     */
    public void putResult(ActionEvent result, String returnAddress) throws JacksonException {
        valkeyKeyedBlockingQueue.put(new SortedSetEntry(queueName(returnAddress), OBJECT_MAPPER.writeValueAsString(result), OffsetDateTime.now()));
    }

    /**
     * Record a heartbeat for the given key in Valkey.
     * @param key the heartbeat key to set
     */
    public void setHeartbeat(String key) {
        valkeyKeyedBlockingQueue.setHeartbeat(key);
    }

    /**
     * Records a long-running task in Valkey.
     * <p>
     * Serializes the given {@link ActionExecution} object and stores it in Valkey
     * along with its start time and the current time as the heartbeat.
     *
     * @param actionExecution the {@link ActionExecution} object representing the task
     */
    public void recordLongRunningTask(ActionExecution actionExecution) {
        try {
            List<String> values = new ArrayList<>(List.of(actionExecution.startTime().toString(), OffsetDateTime.now().toString()));
            if (actionExecution.appName() != null) {
                values.add(actionExecution.appName());
            }
            valkeyKeyedBlockingQueue.recordLongRunningTask(actionExecution.key(), OBJECT_MAPPER.writeValueAsString(values));
        } catch (JacksonException e) {
            log.error("Unable to convert long running task information to JSON", e);
        }
    }

    /**
     * Removes the specified long-running task from Valkey.
     * <p>
     * Deletes the given {@link ActionExecution} object from the Valkey hash,
     * thus marking it as no longer a long-running task.
     *
     * @param actionExecution the {@link ActionExecution} object to be removed
     */
    public void removeLongRunningTask(ActionExecution actionExecution) {
        valkeyKeyedBlockingQueue.removeLongRunningTask(actionExecution.key());
    }
}
