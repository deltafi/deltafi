/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.action;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.queue.jedis.SortedSetEntry;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionExecution;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.ActionState;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for pushing and popping action events to a redis queue.
 */
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

    public static final String DGS_QUEUE = "dgs";
    private static final Duration LONG_RUNNING_HEARTBEAT_THRESHOLD = Duration.ofSeconds(30);

    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public ActionEventQueue(ActionEventQueueProperties actionEventQueueProperties, int poolSize) throws URISyntaxException {
        int maxIdle = poolSize > 0 ? poolSize : actionEventQueueProperties.getMaxIdle();
        int maxTotal = poolSize > 0 ? poolSize : actionEventQueueProperties.getMaxTotal();
        jedisKeyedBlockingQueue = new JedisKeyedBlockingQueue(actionEventQueueProperties.getUrl(),
                actionEventQueueProperties.getPassword(), maxIdle, maxTotal);
        log.info("Jedis pool size: " + maxTotal);
    }

    /**
     * Checks if the queue has a tasking for the specified action.
     *
     * @param  actionInput  the action input object containing the queue name and action context
     * @return true if a tasking for the action exists in the queue, false otherwise
     */
    public boolean queueHasTaskingForAction(ActionInput actionInput) {
        return jedisKeyedBlockingQueue.exists(actionInput.getQueueName(), "*\"name\":\"" +
                actionInput.getActionContext().getName() + "\"*");
    }

    /**
     * Puts the given action inputs into the appropriate Redis queue(s).
     * If the {@code checkUnique} parameter is set to {@code true}, this method will ensure that no other item with the
     * same 'did' field value already exists in the queue before adding an action input.
     * <p>
     * Note that checking for uniqueness is an expensive operation as it involves scanning the Redis set, which can be
     * slow and resource-intensive, particularly for larger sets. Therefore, it's recommended to use this option only in
     * requeue scenarios.
     * <p>
     * If the conversion of an action input to JSON fails, the method will log an error and skip that input.
     *
     * @param actionInputs a list of action inputs to be queued
     * @param checkUnique  if {@code true}, the method will check for uniqueness of 'did' field values before queuing an action input;
     *                     if {@code false}, the method will queue all action inputs without checking for uniqueness
     */
    public void putActions(List<ActionInput> actionInputs, boolean checkUnique) {
        List<SortedSetEntry> actions = new ArrayList<>();
        for (ActionInput actionInput : actionInputs) {
            if (actionInput.getAction() != null && actionInput.getAction().getState() == ActionState.COLD_QUEUED) {
                continue;
            }

            if (checkUnique) {
                String pattern = "*\"did\":\"" + actionInput.getActionContext().getDid() + "\"*";
                if (jedisKeyedBlockingQueue.exists(actionInput.getQueueName(), pattern)) {
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

        jedisKeyedBlockingQueue.put(actions);
    }

    /**
     * Request an ActionInput object from the ActionEvent queue for the specified action
     *
     * @param actionClassName Name of action for Action event request
     * @return next Action on the queue for the given action name
     * @throws JsonProcessingException if the incoming event cannot be serialized
     */
    public ActionInput takeAction(String actionClassName) throws JsonProcessingException {
        return convertInput(jedisKeyedBlockingQueue.take(actionClassName));
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
        jedisKeyedBlockingQueue.put(new SortedSetEntry(queueName(returnAddress), OBJECT_MAPPER.writeValueAsString(result), OffsetDateTime.now()));
    }

    /**
     * Submit a List of result objects for action processing
     *
     * @param results List of ActionEvent results to be posted to the action queue
     * @throws JsonProcessingException if any of the outgoing events cannot be deserialized
     */
    public void putResults(List<ActionEvent> results, String returnAddress) throws JsonProcessingException {
        String queueName = queueName(returnAddress);
        final List<Exception> exceptions = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        List<SortedSetEntry> queuedResults = results.stream()
                .map(actionEvent -> {
                    try {
                        return new SortedSetEntry(queueName, OBJECT_MAPPER.writeValueAsString(actionEvent), now);
                    } catch (JsonProcessingException e) {
                        exceptions.add(e);
                        return null;
                    }
                })
                .filter(Objects::isNull)
                .toList();

        if (!exceptions.isEmpty()) {
            throw (JsonProcessingException) exceptions.get(0);
        }

        jedisKeyedBlockingQueue.put(queuedResults);
    }

    public ActionEvent takeResult(String returnAddress) throws JsonProcessingException {
        return convertEvent(jedisKeyedBlockingQueue.take(queueName(returnAddress)));

    }

    public static ActionEvent convertEvent(String element) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(element, ActionEvent.class);
    }

    public static ActionInput convertInput(String element) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(element, ActionInput.class);
    }

    public void setHeartbeat(String key) {
        jedisKeyedBlockingQueue.setHeartbeat(key);
    }

    public void drop(List<String> actionNames) {
        jedisKeyedBlockingQueue.drop(actionNames);
    }

    public Set<String> keys() { return jedisKeyedBlockingQueue.keys(); }

    public long size(String key) {
        return jedisKeyedBlockingQueue.sortedSetSize(key);
    }

    /**
     * Records a long-running task in Redis.
     *
     * Serializes the given {@link ActionExecution} object and stores it in Redis
     * along with its start time and the current time as the heartbeat.
     *
     * @param actionExecution the {@link ActionExecution} object representing the task
     */
    public void recordLongRunningTask(ActionExecution actionExecution) {
        try {
            jedisKeyedBlockingQueue.recordLongRunningTask(actionExecution.key(),
                    OBJECT_MAPPER.writeValueAsString(List.of(actionExecution.startTime().toString(), OffsetDateTime.now().toString())));
        } catch (JsonProcessingException e) {
            log.error("Unable to convert long running task information to JSON", e);
        }
    }

    /**
     * Removes the specified long-running task from Redis.
     *
     * Deletes the given {@link ActionExecution} object from the Redis hash,
     * thus marking it as no longer a long-running task.
     *
     * @param actionExecution the {@link ActionExecution} object to be removed
     */
    public void removeLongRunningTask(ActionExecution actionExecution) {
        jedisKeyedBlockingQueue.removeLongRunningTask(actionExecution.key());
    }

    /**
     * Fetches and returns a list of tasks that have been running for longer
     * than the specified duration threshold.
     *
     * Deserializes tasks from Redis and filters out those which have
     * heartbeat times within the acceptable range.
     *
     * @return a list of {@link ActionExecution} objects representing tasks
     *         that have been running beyond the threshold
     */
    @SuppressWarnings("unused")
    public List<ActionExecution> getLongRunningTasks() {
        Map<String, String> allTasks = jedisKeyedBlockingQueue.getLongRunningTasks();
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
                    String did = keyParts[2];

                    longRunningTasks.add(new ActionExecution(clazz, action, did, startTime));
                }
            } catch (JsonProcessingException e) {
                log.error("Unable to deserialize long running task information from JSON: " + key + " = " + value, e);
            }
        }

        return longRunningTasks;
    }

    /**
     * Removes long-running tasks from Redis that have heartbeat times
     * exceeding the specified duration threshold.
     *
     * Iterates over tasks in Redis, deserializing and checking their heartbeat times.
     * If a task's heartbeat is older than the threshold or if its data is malformed,
     * it's removed from Redis.
     */
    public void removeExpiredLongRunningTasks() {
        Map<String, String> allTasks = jedisKeyedBlockingQueue.getLongRunningTasks();

        for (Map.Entry<String, String> entry : allTasks.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            try {
                List<OffsetDateTime> times = OBJECT_MAPPER.readValue(value, new TypeReference<>() {});

                if (times.size() != 2) {
                    jedisKeyedBlockingQueue.removeLongRunningTask(key);
                    log.warn("Removed long-running task with malformed data (unexpected length) with key: {}", key);
                    continue;
                }

                OffsetDateTime heartbeatTime = times.get(1);

                if (heartbeatTime.plus(LONG_RUNNING_HEARTBEAT_THRESHOLD).isBefore(OffsetDateTime.now())) {
                    jedisKeyedBlockingQueue.removeLongRunningTask(key);
                    log.info("Removed expired long-running task with key: {}", key);
                }
            } catch (JsonProcessingException e) {
                jedisKeyedBlockingQueue.removeLongRunningTask(key);
                log.error("Unable to deserialize long running task information from JSON for key: {}. Removed the key.", key, e);
            }
        }
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
    public boolean longRunningTaskExists(String clazz, String action, String did) {
        ActionExecution taskToCheck = new ActionExecution(clazz, action, did, null);  // Passing null since we don't care about the startTime for this check.
        String key = taskToCheck.key();
        String serializedValue = jedisKeyedBlockingQueue.getLongRunningTask(key);

        if (serializedValue == null) {
            return false;
        }

        try {
            List<OffsetDateTime> times = OBJECT_MAPPER.readValue(serializedValue, new TypeReference<>() {});
            if (times.size() != 2) {
                log.error("Malformed long-running task data in Redis for key: {}", key);
                return false;
            }

            OffsetDateTime heartbeatTime = times.get(1);
            return !heartbeatTime.plus(LONG_RUNNING_HEARTBEAT_THRESHOLD).isBefore(OffsetDateTime.now());

        } catch (JsonProcessingException e) {
            log.error("Unable to deserialize long running task information from JSON for key: {}", key, e);
            return false;
        }
    }
}
