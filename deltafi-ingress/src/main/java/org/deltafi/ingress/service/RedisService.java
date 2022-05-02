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
package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ZAddParams;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

import static org.deltafi.core.domain.api.Constants.DGS_QUEUE;

public class RedisService {
    private final JedisPool jedisPool;
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    public RedisService(String redisUrl, String redisPassword) throws URISyntaxException {
        URI uri = new URI(redisUrl);
        GenericObjectPoolConfig<Jedis> pool = new GenericObjectPoolConfig<>();
        if (redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(pool, uri);
        } else {
            this.jedisPool = new JedisPool(pool, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, redisPassword);
        }
    }

    public void ingress(ActionEventInput input) throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(DGS_QUEUE, Instant.now().toEpochMilli(), mapper.writeValueAsString(input), ZAddParams.zAddParams().nx());
        }
    }
}