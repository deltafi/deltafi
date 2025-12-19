/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvenanceClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    private DeltaFiProperties deltaFiProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeRecord_doesNothingWhenDisabled() {
        when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        when(deltaFiProperties.isProvenanceEnabled()).thenReturn(false);

        ProvenanceClient provenanceClient = new ProvenanceClient(httpClient, objectMapper, deltaFiPropertiesService, "http://localhost:8080");

        ProvenanceClient.ProvenanceRecord record = new ProvenanceClient.ProvenanceRecord(
                UUID.randomUUID().toString(), null,
                "test-system", "test-source", "test.txt",
                List.of("transform1"), "sink1", "COMPLETE",
                System.currentTimeMillis(), System.currentTimeMillis(),
                Map.of("key", "value")
        );

        provenanceClient.writeRecord(record);

        // No exception thrown, record is silently ignored
        verifyNoInteractions(httpClient);
    }

    @Test
    void provenanceRecord_fromFactoryMethod() {
        UUID did = UUID.randomUUID();
        UUID parentDid = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.parse("2025-01-15T10:00:00Z");
        OffsetDateTime completed = OffsetDateTime.parse("2025-01-15T10:05:00Z");

        ProvenanceClient.ProvenanceRecord record = ProvenanceClient.ProvenanceRecord.from(
                did,
                parentDid,
                "prod-system",
                "MyDataSource",
                "document.pdf",
                List.of("Transform1", "Transform2"),
                "MySink",
                "COMPLETE",
                created,
                completed,
                Map.of("customer", "ABC123")
        );

        assertEquals(did.toString(), record.did());
        assertEquals(parentDid.toString(), record.parentDid());
        assertEquals("prod-system", record.systemName());
        assertEquals("MyDataSource", record.dataSource());
        assertEquals("document.pdf", record.filename());
        assertEquals(List.of("Transform1", "Transform2"), record.transforms());
        assertEquals("MySink", record.dataSink());
        assertEquals("COMPLETE", record.finalState());
        assertEquals(created.toInstant().toEpochMilli(), record.created());
        assertEquals(completed.toInstant().toEpochMilli(), record.completed());
        assertEquals(Map.of("customer", "ABC123"), record.annotations());
    }

    @Test
    void provenanceRecord_handlesNullDataSink() {
        UUID did = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        ProvenanceClient.ProvenanceRecord record = ProvenanceClient.ProvenanceRecord.from(
                did,
                null,  // No parent (non-split file)
                "system",
                "source",
                "file.txt",
                List.of(),
                null,  // No data sink (errored before egress)
                "ERROR",
                now,
                now,
                Map.of()
        );

        assertEquals(did.toString(), record.did());
        assertNull(record.parentDid());
        assertNull(record.dataSink());
        assertEquals("ERROR", record.finalState());
    }

    @Test
    void provenanceRecord_handlesEmptyTransforms() {
        UUID did = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        ProvenanceClient.ProvenanceRecord record = ProvenanceClient.ProvenanceRecord.from(
                did,
                null,
                "system",
                "source",
                "file.txt",
                List.of(),
                "sink",
                "COMPLETE",
                now,
                now,
                Map.of()
        );

        assertTrue(record.transforms().isEmpty());
    }
}
