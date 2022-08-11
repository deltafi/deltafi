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
package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.core.domain.plugin.Plugin;
import org.deltafi.core.domain.plugin.PluginCleaner;
import org.springframework.stereotype.Service;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.List;

import static org.deltafi.common.constant.DeltaFiConstants.DGS_QUEUE;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService implements PluginCleaner {
    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public void enqueue(List<ActionInput> actionInputs) throws JedisConnectionException {
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

    public ActionEventInput dgsFeed() throws JsonProcessingException {
        return jedisKeyedBlockingQueue.take(DGS_QUEUE, ActionEventInput.class);
    }

    @Override
    public void cleanupFor(Plugin plugin) {
        jedisKeyedBlockingQueue.drop(plugin.actionNames());
    }
}
