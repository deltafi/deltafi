package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.api.Constants;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.springframework.stereotype.Service;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {
    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;
    private final DeltaFiConfigService configService;

    public void enqueue(List<String> actionNames, DeltaFile deltaFile) throws ActionConfigException, JedisConnectionException {
        List<Pair<String, Object>> actions = new ArrayList<>();
        for (String actionName : actionNames) {
            ActionConfiguration actionConfiguration = configService.getConfigForAction(actionName);
            actions.add(Pair.of(actionConfiguration.getType(), toActionInput(actionName, actionConfiguration, deltaFile)));
        }
        try {
            jedisKeyedBlockingQueue.put(actions);
        } catch (JsonProcessingException e) {
            log.error("Unable to convert action to JSON", e);
        }
    }

    public void enqueue(Map<String, List<DeltaFile>> enqueueActions) throws ActionConfigException, JedisConnectionException {
        List<Pair<String, Object>> actions = new ArrayList<>();
        for (String actionName : enqueueActions.keySet()) {
            ActionConfiguration actionConfiguration = configService.getConfigForAction(actionName);
            for (DeltaFile deltaFile : enqueueActions.get(actionName)) {
                actions.add(Pair.of(actionConfiguration.getType(), toActionInput(actionName, actionConfiguration, deltaFile)));
            }
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

    private ActionInput toActionInput(String actionName, ActionConfiguration actionConfiguration, DeltaFile deltaFile) {
        ActionInput actionInput = new ActionInput();
        actionInput.setDeltaFile(deltaFile.forQueue(actionName));

        ActionContext context = ActionContext.builder()
                .did(deltaFile.getDid())
                .name(actionName)
                .ingressFlow(deltaFile.getSourceInfo().getFlow()).build();

        actionInput.setActionContext(context);

        if (Objects.isNull(actionConfiguration.getParameters())) {
            actionConfiguration.setParameters(Collections.emptyMap());
        }

        actionInput.setActionParams(actionConfiguration.getParameters());
        return actionInput;
    }
}
