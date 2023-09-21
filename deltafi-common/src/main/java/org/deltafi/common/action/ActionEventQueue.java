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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.queue.jedis.SortedSetEntry;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.ActionState;

import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for pushing and popping action events to a redis queue.
 */
@Slf4j
public class ActionEventQueue {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, true)
            .registerModule(new JavaTimeModule());

    private static final String DGS_QUEUE = "dgs";

    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public ActionEventQueue(ActionEventQueueProperties actionEventQueueProperties, int poolSize) throws URISyntaxException {
        int maxIdle = poolSize > 0 ? poolSize : actionEventQueueProperties.getMaxIdle();
        int maxTotal = poolSize > 0 ? poolSize : actionEventQueueProperties.getMaxTotal();
        jedisKeyedBlockingQueue = new JedisKeyedBlockingQueue(actionEventQueueProperties.getUrl(),
                actionEventQueueProperties.getPassword(), maxIdle, maxTotal);
        log.info("Jedis pool size: " + maxTotal);
    }

    /**
     * Puts the given action inputs into the appropriate Redis queue(s).
     * If the {@code checkUnique} parameter is set to {@code true}, this method will ensure that no other item with the
     * same 'did' field value already exists in the queue before adding an action input.
     *
     * Note that checking for uniqueness is an expensive operation as it involves scanning the Redis set, which can be
     * slow and resource-intensive, particularly for larger sets. Therefore, it's recommended to use this option only in
     * requeue scenarios.
     *
     * If the conversion of an action input to JSON fails, the method will log an error and skip that input.
     *
     * @param actionInputs a list of action inputs to be queued
     * @param checkUnique  if {@code true}, the method will check for uniqueness of 'did' field values before queuing an action input;
     *                     if {@code false}, the method will queue all action inputs without checking for uniqueness
     */
    public void putActions(List<ActionInput> actionInputs, boolean checkUnique) {
        List<SortedSetEntry> actions = new ArrayList<>();
        for (ActionInput actionInput : actionInputs) {
            if (actionInput.getAction().getState() == ActionState.COLD_QUEUED) {
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
     * @param result ActionEventInput object for the result to be posted to the action queue
     * @throws JsonProcessingException if the outgoing event cannot be deserialized
     */
    public void putResult(ActionEvent result, String returnAddress) throws JsonProcessingException {
        jedisKeyedBlockingQueue.put(new SortedSetEntry(queueName(returnAddress), OBJECT_MAPPER.writeValueAsString(result), OffsetDateTime.now()));
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
}
