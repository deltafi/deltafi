/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.queue.jackey.ValkeyKeyedBlockingQueue;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ServerSentService {

    private static final String SSE_VALKEY_CHANNEL_PREFIX = "org.deltafi.ui.sse";

    private final Map<String, String> channelToDataMap = new HashMap<>();
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService dataExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ValkeyKeyedBlockingQueue valkeyService;

    public ServerSentService(ValkeyKeyedBlockingQueue valkeyService) {
        this.valkeyService = valkeyService;
        heartbeatExecutor.scheduleWithFixedDelay(this::sendHeartbeats, 15, 15, TimeUnit.SECONDS);
        dataExecutor.scheduleWithFixedDelay(this::sendUpdatedData, 15, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void cleanup() {
        dataExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    public SseEmitter createSseEmitter() {
        UUID uuid = UUID.randomUUID();
        SseEmitter sseEmitter = new SseEmitter(0L); // timeout of 0 to disable timeouts
        emitters.put(uuid, sseEmitter);
        sseEmitter.onCompletion(() -> onCompete(uuid));
        sseEmitter.onError((throwable -> onError(throwable, uuid)));
        sseEmitter.onTimeout(this::onTimeout);
        sendAllData(sseEmitter);
        return sseEmitter;
    }

    void sendHeartbeats() {
        if (emitters.isEmpty()) {
            return;
        }
        sendToAllEmitters(heartbeatEvent());
    }

    private SseEventBuilder heartbeatEvent() {
        return event("heartbeat", "" + System.currentTimeMillis());
    }

    void sendUpdatedData() {
        if (emitters.isEmpty()) {
            return;
        }

        try {
            doSendUpdatedData();
        } catch (Exception e) {
            log.error("Failed to send data to subscribers", e);
        }
    }

    private void doSendUpdatedData() {
        Map<String, String> sseData = valkeyService.getItemsWithPrefix(SSE_VALKEY_CHANNEL_PREFIX);
        for (Map.Entry<String, String> entry : sseData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!value.equals(channelToDataMap.get(key))) {
                channelToDataMap.put(key, value);
                log.debug("Sending on channel {} to {} subscriber(s)", key, emitters.size());
                sendToAllEmitters(event(removePrefix(key), value));
            }
        }
    }

    private void sendAllData(SseEmitter sseEmitter) {
        sendData(sseEmitter, heartbeatEvent());
        channelToDataMap.entrySet().stream()
                .map(entry -> event(removePrefix(entry.getKey()), entry.getValue()))
                .forEach(event -> sendData(sseEmitter, event));
    }

    private void sendToAllEmitters(SseEventBuilder event) {
        emitters.values().forEach(emitter -> sendData(emitter, event));
    }

    private SseEventBuilder event(String name, String value) {
        return SseEmitter.event().name(name).data(value);
    }

    private void onCompete(UUID uuid) {
        log.trace("Removing sseEmitter {}", uuid);
        emitters.remove(uuid);
    }

    private void onError(Throwable t, UUID uuid) {
        // this can happen often (i.e. after browser window is closed then attempting to send data)
        log.trace("Removing sseEmitters for {} due to an error", uuid, t);
        emitters.remove(uuid);
    }

    private void onTimeout() {
        log.warn("Timed out waiting for SSE emitter to complete");
    }

    private void sendData(SseEmitter emitter, SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            // this can happen often (i.e. after browser window is closed then attempting to send data)
            log.trace("Failed to send data", e);
        }
    }

    private String removePrefix(String key) {
        return key.substring(SSE_VALKEY_CHANNEL_PREFIX.length() + 1);
    }
}
