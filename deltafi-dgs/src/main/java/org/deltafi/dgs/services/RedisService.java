package org.deltafi.dgs.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.dgs.api.types.ActionInput;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.api.types.JsonMap;
import org.deltafi.dgs.configuration.ActionConfiguration;
import org.deltafi.dgs.exceptions.ActionConfigException;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.KeyedZSetElement;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.deltafi.dgs.api.Constants.DGS_QUEUE;

@Service
public class RedisService {
    Logger log = LoggerFactory.getLogger(RedisService.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    private final JedisPool jedisPool;
    private final ActionConfigService actionConfigService;

    public RedisService(JedisPool jedisPool, ActionConfigService actionConfigService) {
        this.jedisPool = jedisPool;
        this.actionConfigService = actionConfigService;
    }

    public void enqueue(List<String> actionNames, DeltaFile deltaFile) throws ActionConfigException {
        try (Jedis jedis = jedisPool.getResource()) {
            for (String actionName : actionNames) {
                ActionConfiguration params = actionConfigService.getConfigForAction(actionName);
                ActionInput actionInput = toActionInput(actionName, params, deltaFile);
                jedis.zadd(params.getType(), Instant.now().toEpochMilli(), mapper.writeValueAsString(actionInput), ZAddParams.zAddParams().nx());
            }
        } catch (JsonProcessingException e) {
            // TODO: this should never happen, but do something?
        }
    }

    public ActionEventInput dgsFeed() throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            KeyedZSetElement keyedZSetElement = jedis.bzpopmin(0, DGS_QUEUE);
            return mapper.readValue(keyedZSetElement.getElement(), ActionEventInput.class);
        }
    }

    public ActionInput toActionInput(String actionName, ActionConfiguration params, DeltaFile deltaFile) {
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
