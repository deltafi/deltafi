package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.api.Constants;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.springframework.stereotype.Service;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {
    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public void dropQueues(List<String> actionNames) {
        jedisKeyedBlockingQueue.drop(actionNames);
    }

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
        return jedisKeyedBlockingQueue.take(Constants.DGS_QUEUE, ActionEventInput.class);
    }

}
