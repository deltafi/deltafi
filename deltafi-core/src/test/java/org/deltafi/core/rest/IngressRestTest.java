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
package org.deltafi.core.rest;

import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.exceptions.IngressException;
import org.deltafi.core.services.IngressService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(MockitoExtension.class)
public class IngressRestTest {

    @Mock
    IngressService ingressService;

    @Mock
    MetricRepository metricRepository;

    @InjectMocks
    IngressRest testObj;

    @Test
    public void testUTF32Metadata() throws ObjectStorageException, IngressException {
        String username = "nobody";
        String contentType = "application/flowfile-v1";
        String flow = "";
        String filename = "filename";
        InputStream dataStream = readData("rest-test/flowfile");
        AtomicReference<String> encodedString = new AtomicReference<>(null);
        AtomicInteger numberOfMetadata = new AtomicInteger(0);

        Mockito.when(ingressService.isEnabled()).thenReturn(true);
        Mockito.when(ingressService.isStorageAvailable()).thenReturn(true);
        Mockito.when(ingressService.ingressData(Mockito.any(InputStream.class), Mockito.anyString(), Mockito.anyString(), Mockito.anyMap(), Mockito.anyString())).then(invocation -> {
            Map<String, String> metadataReturned = invocation.getArgument(3, Map.class);

            encodedString.set(metadataReturned.get("encodedString"));
            numberOfMetadata.set(metadataReturned.size());

            return null;
        });

        testObj.ingressData(dataStream, filename, flow, null, contentType, username);

        Assertions.assertEquals("\uD84E\uDCE7", encodedString.get());
        Assertions.assertEquals(12, numberOfMetadata.get());
    }

    private InputStream readData(String location) {
        return getClass().getClassLoader().getResourceAsStream(location);
    }
}
