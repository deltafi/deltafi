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

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.ActionInput;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for pushing and popping action events to a redis queue.
 */
@Slf4j
public class ActionEventQueue {
    private static final String DGS_QUEUE = "dgs";

    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public ActionEventQueue(ActionEventQueueProperties actionEventQueueProperties, int poolSize) throws URISyntaxException {
        int maxIdle = poolSize > 0 ? poolSize : actionEventQueueProperties.getMaxIdle();
        int maxTotal = poolSize > 0 ? poolSize : actionEventQueueProperties.getMaxTotal();
        jedisKeyedBlockingQueue = new JedisKeyedBlockingQueue(actionEventQueueProperties.getUrl(),
                actionEventQueueProperties.getPassword().orElse(null), maxIdle, maxTotal);
        log.info("Jedis pool size: " + maxTotal);
    }

    public void putActions(List<ActionInput> actionInputs) throws JedisConnectionException {
        List<Pair<String, Object>> actions = new ArrayList<>();
        for (ActionInput actionInput : actionInputs) {
            actions.add(Pair.of(actionInput.getQueueName(), actionInput));
        }

        try {
            jedisKeyedBlockingQueue.put(actions);
        } catch (JsonProcessingException e) {
            log.error("Unable to convert action to JSON", e);
        }
    }

    /**
     * Request an ActionInput object from the ActionEvent queue for the specified action
     *
     * @param actionClassName Name of action for Action event request
     * @return next Action on the queue for the given action name
     * @throws JsonProcessingException if the incoming event cannot be serialized
     */
    public ActionInput takeAction(String actionClassName) throws JsonProcessingException, JedisConnectionException {
        return jedisKeyedBlockingQueue.take(actionClassName, ActionInput.class);
    }

    /**
     * Submit a result object for action processing
     *
     * @param result ActionEventInput object for the result to be posted to the action queue
     * @throws JsonProcessingException if the outgoing event cannot be deserialized
     */
    public void putResult(ActionEventInput result) throws JsonProcessingException, JedisConnectionException {
        jedisKeyedBlockingQueue.put(DGS_QUEUE, result);
    }

    public ActionEventInput takeResult() throws JsonProcessingException {
        return jedisKeyedBlockingQueue.take(DGS_QUEUE, ActionEventInput.class);
    }

    public void drop(List<String> actionNames) {
        jedisKeyedBlockingQueue.drop(actionNames);
    }
}
