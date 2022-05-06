/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.api.Constants;
import org.deltafi.core.domain.api.types.ActionInput;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.net.URISyntaxException;

/**
 * Specialization of ActionEventService.  Service for pushing and popping action events to a redis queue.
 */
public class RedisActionEventService implements ActionEventService {
    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public RedisActionEventService(String url, String password) throws URISyntaxException {
        // TODO: The maxIdle and maxTotal need to scale up with the number of actions configured
        jedisKeyedBlockingQueue = new JedisKeyedBlockingQueue(url, password, 16, 16);
    }

    @Override
    public ActionInput getAction(String actionClassName) throws JsonProcessingException, JedisConnectionException {
        return jedisKeyedBlockingQueue.take(actionClassName, ActionInput.class);
    }

    @Override
    public void submitResult(Result result) throws JsonProcessingException, JedisConnectionException {
        jedisKeyedBlockingQueue.put(Constants.DGS_QUEUE, result.toEvent());
    }
}