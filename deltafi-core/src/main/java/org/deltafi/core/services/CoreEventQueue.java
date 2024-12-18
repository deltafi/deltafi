/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.queue.jackey.SortedSetEntry;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.common.types.ActionInput;
import org.deltafi.core.types.WrappedActionInput;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for pushing and popping action events to a valkey queue.
 */
@Slf4j
public class CoreEventQueue {

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

    public static final String DGS_QUEUE = "dgs";
    private static final Duration LONG_RUNNING_HEARTBEAT_THRESHOLD = Duration.ofSeconds(30);

    private final ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

    public CoreEventQueue(EventQueueProperties eventQueueProperties, int poolSize) throws URISyntaxException {
        int maxIdle = poolSize > 0 ? poolSize : eventQueueProperties.getMaxIdle();
        int maxTotal = poolSize > 0 ? poolSize : eventQueueProperties.getMaxTotal();
        valkeyKeyedBlockingQueue = new ValkeyKeyedBlockingQueue(eventQueueProperties.getUrl(),
                eventQueueProperties.getPassword(), maxIdle, maxTotal);
    }

    public CoreEventQueue(ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue) {
        this.valkeyKeyedBlockingQueue = valkeyKeyedBlockingQueue;
    }

    public Set<String> keys() { return valkeyKeyedBlockingQueue.keys(); }

    public void drop(List<String> actionNames) {
        valkeyKeyedBlockingQueue.drop(actionNames);
    }

    public void setHeartbeat(String key) {
        valkeyKeyedBlockingQueue.setHeartbeat(key);
    }

    /**
     * Checks if the queue has a tasking for the specified action.
     *
     * @param  actionInput  the action input object containing the queue name and action context
     * @return true if a tasking for the action exists in the queue, false otherwise
     */
    public boolean queueHasTaskingForAction(ActionInput actionInput) {
        return valkeyKeyedBlockingQueue.exists(actionInput.getQueueName(), "*\"actionName\":\"" +
                actionInput.getActionContext().getActionName() + "\"*");
    }

    /**
     * Puts the given action inputs into the appropriate Valkey queue(s).
     * If the {@code checkUnique} parameter is set to {@code true}, this method will ensure that no other item with the
     * same 'did' field value already exists in the queue before adding an action input.
     * <p>
     * Note that checking for uniqueness is an expensive operation as it involves scanning the Valkey set, which can be
     * slow and resource-intensive, particularly for larger sets. Therefore, it's recommended to use this option only in
     * requeue scenarios.
     * <p>
     * If the conversion of an action input to JSON fails, the method will log an error and skip that input.
     *
     * @param actionInputs a list of action inputs to be queued
     * @param checkUnique  if {@code true}, the method will check for uniqueness of 'did' field values before queuing an action input;
     *                     if {@code false}, the method will queue all action inputs without checking for uniqueness
     */
    public void putActions(List<WrappedActionInput> actionInputs, boolean checkUnique) {
        List<SortedSetEntry> actions = new ArrayList<>();
        for (WrappedActionInput actionInput : actionInputs) {
            if (actionInput.isColdQueued()) {
                continue;
            }

            if (checkUnique) {
                String pattern = "*\"" + actionInput.getActionContext().getFlowId() + "\"*";
                if (valkeyKeyedBlockingQueue.exists(actionInput.getQueueName(), pattern)) {
                    log.warn("Skipping queueing for potential duplicate action event: {}", actionInput);
                    continue;
                }
            }

            try {
                actions.add(new SortedSetEntry(actionInput.getQueueName(), OBJECT_MAPPER.writeValueAsString(actionInput), actionInput.getActionCreated()));
            } catch (JsonProcessingException e) {
                log.error("Unable to convert action to JSON", e);
            }
        }

        valkeyKeyedBlockingQueue.put(actions);
    }

    private String queueName(String returnAddress) {
        String queueName = DGS_QUEUE;
        if (returnAddress != null) {
            queueName += "-" + returnAddress;
        }

        return queueName;
    }

    public ActionEvent takeResult(String returnAddress) throws JsonProcessingException {
        return convertEvent(valkeyKeyedBlockingQueue.take(queueName(returnAddress)));

    }

    public static ActionEvent convertEvent(String element) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(element, ActionEvent.class);
    }

    public long size(String key) {
        return valkeyKeyedBlockingQueue.sortedSetSize(key);
    }

    /**
     * Fetches and returns a list of tasks that have been running for longer
     * than the specified duration threshold.
     * <p>
     * Deserializes tasks from Valkey and filters out those which have
     * heartbeat times within the acceptable range.
     *
     * @return a list of {@link ActionExecution} objects representing tasks
     *         that have been running beyond the threshold
     */
    @SuppressWarnings("unused")
    public List<ActionExecution> getLongRunningTasks() {
        Map<String, String> allTasks = valkeyKeyedBlockingQueue.getLongRunningTasks();
        List<ActionExecution> longRunningTasks = new ArrayList<>();

        for (Map.Entry<String, String> entry : allTasks.entrySet()) {
            String key = entry.getKey(); // "class:action:did" string
            String value = entry.getValue(); // Serialized "[startTime, heartbeatTime]" string

            // Deserialize the value to extract startTime and heartbeatTime
            try {
                List<OffsetDateTime> times = OBJECT_MAPPER.readValue(value, new TypeReference<>() {});
                if (times.size() != 2) {
                    log.error("Unable to deserialize long running task time information from JSON");
                    continue;
                }
                OffsetDateTime startTime = times.get(0);
                OffsetDateTime heartbeatTime = times.get(1);

                // Check if the heartbeat exceeds the threshold
                if (!heartbeatTime.plus(LONG_RUNNING_HEARTBEAT_THRESHOLD).isBefore(OffsetDateTime.now())) {
                    // Split the key to extract class, action, and did
                    String[] keyParts = key.split(":");
                    String clazz = keyParts[0];
                    String action = keyParts[1];
                    UUID did = UUID.fromString(keyParts[2]);

                    longRunningTasks.add(new ActionExecution(clazz, action, did, startTime, heartbeatTime));
                }
            } catch (JsonProcessingException e) {
                log.error("Unable to deserialize long running task information from JSON: {} = {}", key, value, e);
            }
        }

        return longRunningTasks;
    }

    public void removeLongRunningTask(Collection<String> actionExecutionKeys) {
        valkeyKeyedBlockingQueue.removeLongRunningTasks(actionExecutionKeys);
    }

    /**
     * Check if a specific long-running task exists and if its heartbeat is within the acceptable threshold.
     *
     * @param clazz  The class name.
     * @param action The action name.
     * @param did    The did value.
     * @return true if the task exists and its heartbeat is within the threshold, false otherwise.
     */
    @SuppressWarnings("ununsed")
    public boolean longRunningTaskExists(String clazz, String action, UUID did) {
        ActionExecution taskToCheck = new ActionExecution(clazz, action, did, null);  // Passing null since we don't care about the startTime for this check.
        String key = taskToCheck.key();
        String serializedValue = valkeyKeyedBlockingQueue.getLongRunningTask(key);

        if (serializedValue == null) {
            return false;
        }

        try {
            List<OffsetDateTime> times = OBJECT_MAPPER.readValue(serializedValue, new TypeReference<>() {});
            if (times.size() != 2) {
                log.error("Malformed long-running task data in Valkey for key: {}", key);
                return false;
            }

            OffsetDateTime heartbeatTime = times.get(1);
            return !heartbeatTime.plus(LONG_RUNNING_HEARTBEAT_THRESHOLD).isBefore(OffsetDateTime.now());

        } catch (JsonProcessingException e) {
            log.error("Unable to deserialize long running task information from JSON for key: {}", key, e);
            return false;
        }
    }

    /**
     * Removes long-running tasks from Valkey that have heartbeat times
     * exceeding the specified duration threshold.
     * <p>
     * Iterates over tasks in Valkey, deserializing and checking their heartbeat times.
     * If a task's heartbeat is older than the threshold or if its data is malformed,
     * it's removed from Valkey.
     */
    public void removeExpiredLongRunningTasks() {
        Map<String, String> allTasks = valkeyKeyedBlockingQueue.getLongRunningTasks();

        for (Map.Entry<String, String> entry : allTasks.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            try {
                List<OffsetDateTime> times = OBJECT_MAPPER.readValue(value, new TypeReference<>() {});

                if (times.size() != 2) {
                    valkeyKeyedBlockingQueue.removeLongRunningTask(key);
                    log.warn("Removed long-running task with malformed data (unexpected length) with key: {}", key);
                    continue;
                }

                OffsetDateTime heartbeatTime = times.get(1);

                if (heartbeatTime.plus(LONG_RUNNING_HEARTBEAT_THRESHOLD).isBefore(OffsetDateTime.now())) {
                    valkeyKeyedBlockingQueue.removeLongRunningTask(key);
                    log.info("Removed expired long-running task with key: {}", key);
                }
            } catch (JsonProcessingException e) {
                valkeyKeyedBlockingQueue.removeLongRunningTask(key);
                log.error("Unable to deserialize long running task information from JSON for key: {}. Removed the key.", key, e);
            }
        }
    }
}
