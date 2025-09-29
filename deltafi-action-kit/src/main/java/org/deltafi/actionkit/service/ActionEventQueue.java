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
package org.deltafi.actionkit.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.valkey.SortedSetEntry;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.types.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.deltafi.common.action.EventQueueProperties.DGS_QUEUE;

/**
 * Service for pushing and popping action events to a valkey queue.
 */
@RequiredArgsConstructor
@Slf4j
public class ActionEventQueue {

    private static final ObjectMapper OBJECT_MAPPER;
    static {
        ObjectMapper temp = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
                .registerModule(new JavaTimeModule());
        StreamReadConstraints streamReadConstraints = StreamReadConstraints
                .builder()
                .maxStringLength(16 * 1024 * 1024)
                .build();
        temp.getFactory().setStreamReadConstraints(streamReadConstraints);
        OBJECT_MAPPER = temp;
    }

    private final ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    /**
     * Request an ActionInput object from the ActionEvent queue for the specified action
     *
     * @param actionClassName Name of action for Action event request
     * @return next Action on the queue for the given action name
     * @throws JsonProcessingException if the incoming event cannot be serialized
     */
    public ActionInput takeAction(String actionClassName) throws JsonProcessingException {
        return convertInput(valkeyKeyedBlockingQueue.take(actionClassName));
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
     * @throws JsonProcessingException if the outgoing event cannot be deserialized
     */
    public void putResult(ActionEvent result, String returnAddress) throws JsonProcessingException {
        valkeyKeyedBlockingQueue.put(new SortedSetEntry(queueName(returnAddress), OBJECT_MAPPER.writeValueAsString(result), OffsetDateTime.now()));
    }

    public static ActionInput convertInput(String element) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(element, ActionInput.class);
    }

    public void setHeartbeat(String key) {
        valkeyKeyedBlockingQueue.setHeartbeat(key);
    }

    /**
     * Records a long-running task in Valkey.
     *
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
        } catch (JsonProcessingException e) {
            log.error("Unable to convert long running task information to JSON", e);
        }
    }

    /**
     * Removes the specified long-running task from Valkey.
     *
     * Deletes the given {@link ActionExecution} object from the Valkey hash,
     * thus marking it as no longer a long-running task.
     *
     * @param actionExecution the {@link ActionExecution} object to be removed
     */
    public void removeLongRunningTask(ActionExecution actionExecution) {
        valkeyKeyedBlockingQueue.removeLongRunningTask(actionExecution.key());
    }
}
