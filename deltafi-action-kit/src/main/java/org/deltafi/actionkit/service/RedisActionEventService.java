package org.deltafi.actionkit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.api.Constants;
import org.deltafi.core.domain.api.types.ActionInput;

import java.net.URISyntaxException;

public class RedisActionEventService implements ActionEventService {
    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    public RedisActionEventService(String url, String password) throws URISyntaxException {
        // TODO: The maxIdle and maxTotal need to scale up with the number of actions configured
        jedisKeyedBlockingQueue = new JedisKeyedBlockingQueue(url, password, 16, 16);
    }

    @Override
    public ActionInput getAction(String actionClassName) throws JsonProcessingException {
        return jedisKeyedBlockingQueue.take(actionClassName, ActionInput.class);
    }

    @Override
    public void submitResult(Result result) throws JsonProcessingException {
        jedisKeyedBlockingQueue.put(Constants.DGS_QUEUE, result.toEvent());
    }
}