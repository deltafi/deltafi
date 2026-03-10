/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.valkey.JedisPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.artifact.VersionChangedEvent;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link VersionChangedEvent}s to the Valkey pub/sub channel
 * {@link ArtifactRegistryConstants#VERSION_CHANGED_CHANNEL} so action-kit workers can invalidate
 * their artifact caches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactRegistryNotificationService {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final JedisPool jedisPool;

    /**
     * Publishes a version-change event. Non-fatal on failure — subscribers will
     * fall back to polling when notifications are missed.
     */
    public void publish(VersionChangedEvent event) {
        try {
            String json = MAPPER.writeValueAsString(event);
            try (io.valkey.Jedis jedis = jedisPool.getResource()) {
                jedis.publish(ArtifactRegistryConstants.VERSION_CHANGED_CHANNEL, json);
            }
            log.debug("Published version-change notification: prefix={} artifact={} alias={} version={}",
                    event.prefix(), event.artifactName(), event.alias(), event.newVersion());
        } catch (Exception e) {
            log.warn("Failed to publish version-change notification for {}/{}: {}",
                    event.prefix(), event.artifactName(), e.getMessage());
        }
    }
}
