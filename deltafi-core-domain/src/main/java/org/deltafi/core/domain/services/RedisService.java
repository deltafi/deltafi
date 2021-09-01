package org.deltafi.core.domain.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.JsonMap;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.KeyedZSetElement;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.deltafi.core.domain.api.Constants.DGS_QUEUE;

@Service
public class RedisService {

    private static final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    private final JedisPool jedisPool;
    private final DeltaFiConfigService configService;

    public RedisService(JedisPool jedisPool, DeltaFiConfigService configService) {
        this.jedisPool = jedisPool;
        this.configService = configService;
    }

    public void enqueue(List<String> actionNames, DeltaFile deltaFile) throws ActionConfigException {
        try (Jedis jedis = jedisPool.getResource()) {
            for (String actionName : actionNames) {
                ActionConfiguration params = configService.getConfigForAction(actionName);
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