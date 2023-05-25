/**
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

import org.deltafi.core.services.api.DeltafiApiClient;
import org.deltafi.core.services.api.model.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicReference;


@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    DeltafiApiClient deltafiApiClient;

    @InjectMocks
    EventService sut;

    @Test
    void publishEvent() {
        AtomicReference<String> json = new AtomicReference<>(null);

        Mockito.when(deltafiApiClient.createEvent(Mockito.anyString())).then(invocation -> {
            json.set(invocation.getArgument(0, String.class));
            return json.get();
        });

        sut.publishEvent(Event.builder("My event").build());

        Mockito.verify(deltafiApiClient).createEvent("""
                {
                    "summary": "My event",
                    "source": "core",
                    "severity": "info",
                    "notification": false
                }""");
    }

    @Test
    void publishInfo() {
        AtomicReference<String> json = new AtomicReference<>(null);

        Mockito.when(deltafiApiClient.createEvent(Mockito.anyString())).then(invocation -> {
            json.set(invocation.getArgument(0, String.class));
            return json.get();
        });

        sut.publishInfo("My event");

        Mockito.verify(deltafiApiClient).createEvent("""
                {
                    "summary": "My event",
                    "source": "core",
                    "severity": "info",
                    "notification": false
                }""");
    }
}