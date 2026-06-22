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
package org.deltafi.actionkit.artifact;

import tools.jackson.databind.ObjectMapper;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.JedisPubSub;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.artifact.ArtifactRegistryConstants;
import org.deltafi.common.artifact.VersionChangedEvent;

import java.util.function.Consumer;

/**
 * Subscribes to the Valkey pub/sub channel for artifact version-change events and
 * invalidates (refreshes) any matching entries in the {@link ArtifactCacheManager}.
 *
 * <p>Each plugin instance maintains its own subscription, so all running plugins receive
 * every notification (fan-out). Because Valkey pub/sub is fire-and-forget, messages are
 * never stored and require no pruning — a missed message simply means the cache remains
 * stale until its TTL expires naturally.
 *
 * <p>A background daemon thread keeps the subscription alive, reconnecting automatically
 * if the connection drops.
 */
@Slf4j
class ArtifactVersionChangeListener implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PLUGIN_PREFIX = "plugins/";

    private final JedisPool jedisPool;
    private final ArtifactCacheManager cacheManager;
    private final Consumer<VersionChangedEvent> eventHandler;

    private volatile boolean running = true;
    private Thread subscriberThread;

    ArtifactVersionChangeListener(JedisPool jedisPool, ArtifactCacheManager cacheManager,
                                   Consumer<VersionChangedEvent> eventHandler) {
        this.jedisPool = jedisPool;
        this.cacheManager = cacheManager;
        this.eventHandler = eventHandler;
    }

    /** Starts the background subscriber thread. */
    void start() {
        subscriberThread = new Thread(this::subscribeLoop, "artifact-cache-listener");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void subscribeLoop() {
        while (running) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleMessage(message);
                    }
                }, ArtifactRegistryConstants.VERSION_CHANGED_CHANNEL);
            } catch (Exception e) {
                if (running) {
                    log.warn("Artifact cache listener disconnected, retrying in 5s: {}", e.getMessage());
                    try {
                        //noinspection BusyWait
                        Thread.sleep(5_000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void handleMessage(String json) {
        try {
            VersionChangedEvent event = MAPPER.readValue(json, VersionChangedEvent.class);
            String owner = ownerFrom(event);
            cacheManager.refreshIfCached(owner, event.artifactName(), event.alias());
            cacheManager.invokeCallbacks(owner, event.artifactName(), event.alias(), event);
            eventHandler.accept(event);
            log.debug("Processed version-change event: owner={} artifact={} alias={}",
                    owner, event.artifactName(), event.alias());
        } catch (Exception e) {
            log.warn("Failed to process version-change event: {}", e.getMessage());
        }
    }

    /**
     * Converts the S3 event prefix from {@link VersionChangedEvent} into the owner string
     * used as the cache key. {@code "shared"} stays as-is; {@code "plugins/{name}"} → {@code "{name}"}.
     */
    private static String ownerFrom(VersionChangedEvent event) {
        if (event.isShared()) {
            return ArtifactRegistryConstants.SHARED_OWNER;
        }
        String prefix = event.prefix();
        return prefix.startsWith(PLUGIN_PREFIX) ? prefix.substring(PLUGIN_PREFIX.length()) : prefix;
    }

    @Override
    public void close() {
        running = false;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
    }
}
