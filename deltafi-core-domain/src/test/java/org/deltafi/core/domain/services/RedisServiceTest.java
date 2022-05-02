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
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.core.domain.Util;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.generated.types.Action;
import org.deltafi.core.domain.generated.types.ActionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

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

    @Captor
    ArgumentCaptor<List<Pair<String, Object>>> listArgCaptor;

    @Test
    void testEnqueue() throws JsonProcessingException {
        DeltaFile deltaFile = Util.emptyDeltaFile("did", "notIncludedFlow");
        Action action = Action.newBuilder().state(ActionState.COMPLETE).name("FormatAction").build();
        deltaFile.getActions().add(action);

        ActionInput smokeFormat = makeActionInput(SMOKE_FORMAT, deltaFile);
        ActionInput passthroughFormat = makeActionInput(PASSTHROUGH_FORMAT, deltaFile);
        ActionInput passthroughEgress = makeActionInput(PASSTHROUGH_EGRESS, deltaFile);


        redisService.enqueue(List.of(smokeFormat, passthroughFormat, passthroughEgress));
        Mockito.verify(jedisKeyedBlockingQueue).put(listArgCaptor.capture());
        List<Pair<String, Object>> capturedList = listArgCaptor.getValue();
        assertEquals(3, capturedList.size());

    }

    private boolean isEgressAction(final String name) {
        return (name.toUpperCase().contains("EGRESS"));
    }

    private Map<String, Object> getRequiredParams(final String name) {
        Map<String, Object> params = new HashMap<>();
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

    private ActionInput makeActionInput(String name, DeltaFile deltaFile) {
        return makeConfig(name).buildActionInput(deltaFile);
    }

    private ActionConfiguration makeConfig(final String name) {
        Map<String, Object> params = getRequiredParams(name);
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
