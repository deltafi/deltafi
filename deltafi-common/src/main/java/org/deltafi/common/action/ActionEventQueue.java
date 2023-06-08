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
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionInput;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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

    public void putActions(List<ActionInput> actionInputs) {
        List<Pair<String, String>> actions = new ArrayList<>();
        for (ActionInput actionInput : actionInputs) {
            try {
                actions.add(Pair.of(actionInput.getQueueName(), OBJECT_MAPPER.writeValueAsString(actionInput)));
            } catch (JsonProcessingException e) {
                log.error("Unable to convert action to JSON", e);
                return;
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
        jedisKeyedBlockingQueue.put(queueName(returnAddress),
                OBJECT_MAPPER.writeValueAsString(result));
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
}
