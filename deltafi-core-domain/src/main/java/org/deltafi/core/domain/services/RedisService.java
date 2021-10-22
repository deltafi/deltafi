package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.api.Constants;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.JsonMap;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RedisService {
    private final JedisKeyedBlockingQueue jedisKeyedBlockingQueue;
    private final DeltaFiConfigService configService;

    public void enqueue(List<String> actionNames, DeltaFile deltaFile) throws ActionConfigException {
        Map<String, Object> actionInputsMap = new HashMap<>();
        for (String actionName : actionNames) {
            ActionConfiguration params = configService.getConfigForAction(actionName);
            actionInputsMap.put(params.getType(), toActionInput(actionName, params, deltaFile));
        }
        try {
            jedisKeyedBlockingQueue.put(actionInputsMap);
        } catch (JsonProcessingException e) {
            // TODO: this should never happen, but do something?
        }
    }

    public ActionEventInput dgsFeed() throws JsonProcessingException {
        return jedisKeyedBlockingQueue.take(Constants.DGS_QUEUE, ActionEventInput.class);
    }

    private ActionInput toActionInput(String actionName, ActionConfiguration params, DeltaFile deltaFile) {
        ActionInput actionInput = new ActionInput();
        actionInput.setDeltaFile(deltaFile.forQueue(actionName));

        if (Objects.isNull(params.getParameters())) {
            params.setParameters(new JsonMap());
        }
        params.getParameters().put("name", params.getName());
        actionInput.setActionParams(params.getParameters());
        return actionInput;
    }
}