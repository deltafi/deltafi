package org.deltafi.core.domain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.JsonMap;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {
    public static final String EGRESS_CLASS = "org.deltafi.core.action.RestPostEgressAction";
    public static final String FORMAT_CLASS = "org.deltafi.passthrough.action.RoteFormatAction";

    public static final String PASSTHROUGH_EGRESS = "PassthroughEgressAction";
    public static final String PASSTHROUGH_FORMAT = "PassthroughFormatAction";
    public static final String SMOKE_FORMAT = "SmokeFormatAction";

    @InjectMocks
    RedisService redisService;

    @Mock
    JedisKeyedBlockingQueue jedisKeyedBlockingQueue;

    @Mock
    DeltaFiConfigService configService;

    @Test
    void testEnqueue() throws ActionConfigException, JsonProcessingException {
        List<String> actionNames = new ArrayList<>(Arrays.asList(PASSTHROUGH_FORMAT, SMOKE_FORMAT, PASSTHROUGH_EGRESS));

        DeltaFile deltaFile = Util.emptyDeltaFile("did", "notIncludedFlow");
        Action action = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        deltaFile.getActions().add(action);

        ActionConfiguration smokeFormatConfig = makeConfig(SMOKE_FORMAT);
        ActionConfiguration passthroughFormatConfig = makeConfig(PASSTHROUGH_FORMAT);
        ActionConfiguration passthroughEgressConfig = makeConfig(PASSTHROUGH_EGRESS);

        Mockito.when(configService.getConfigForAction(SMOKE_FORMAT)).thenReturn(smokeFormatConfig);
        Mockito.when(configService.getConfigForAction(PASSTHROUGH_FORMAT)).thenReturn(passthroughFormatConfig);
        Mockito.when(configService.getConfigForAction(PASSTHROUGH_EGRESS)).thenReturn(passthroughEgressConfig);

        ArgumentCaptor<List> listArgCaptor = ArgumentCaptor.forClass(List.class);

        redisService.enqueue(actionNames, deltaFile);
        Mockito.verify(jedisKeyedBlockingQueue).put(listArgCaptor.capture());
        List<Pair<String, Object>> capturedList = (List<Pair<String, Object>>) listArgCaptor.getValue();
        assertEquals(3, capturedList.size());

    }

    private boolean isEgressAction(final String name) {
        return (name.toUpperCase().indexOf("EGRESS") != -1);
    }

    private JsonMap getRequiredParams(final String name) {
        JsonMap params = new JsonMap();
        params.put("name", name);
        if (isEgressAction(name)) {
            params.put("url", "https://egress");
            params.put("egressFlow", "out");
        }
        return params;
    }

    private ActionConfiguration makeEgressConfig(final String name) {
        EgressActionConfiguration config = new EgressActionConfiguration();
        config.setName(name);
        config.setApiVersion("0.7.0");
        config.setType(EGRESS_CLASS);
        return config;
    }

    private ActionConfiguration makeFormatConfig(final String name) {
        FormatActionConfiguration config = new FormatActionConfiguration();
        config.setName(name);
        config.setApiVersion("0.7.0");
        config.setType(FORMAT_CLASS);
        return config;
    }

    private ActionConfiguration makeConfig(final String name) {
        JsonMap params = getRequiredParams(name);
        ActionConfiguration actionConfig;
        if (isEgressAction(name)) {
            actionConfig = makeEgressConfig(name);
        } else {
            actionConfig = makeFormatConfig(name);
        }
        actionConfig.setParameters(params);
        return actionConfig;
    }
}
