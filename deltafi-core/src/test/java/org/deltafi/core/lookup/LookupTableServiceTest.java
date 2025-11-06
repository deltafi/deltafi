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
package org.deltafi.core.lookup;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.lookup.*;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.services.CoreEventQueue;
import org.deltafi.core.services.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

public class LookupTableServiceTest {
    @RequiredArgsConstructor
    private static class MockLookupTableRepoFactory implements LookupTableRepoFactory {
        private final Map<String, LookupTableRepo> mockLookupTableRepoMap;

        @Override
        public LookupTableRepo create(LookupTable lookupTable) {
            return mockLookupTableRepoMap.get(lookupTable.getName());
        }
    }

    private static final PluginCoordinates PLUGIN1 =
            PluginCoordinates.builder().groupId("a").artifactId("plugin-1").version("1.0.0").build();
    private static final PluginCoordinates PLUGIN2 =
            PluginCoordinates.builder().groupId("b").artifactId("plugin-2").version("1.0.0").build();

    private static final List<LookupTable> EXISTING_LOOKUP_TABLES = List.of(
            LookupTable.builder().sourcePlugin(PLUGIN1).name("existing-1").columns(List.of("a", "b")).pullThrough(true).build(),
            LookupTable.builder().sourcePlugin(PLUGIN1).name("existing-2").columns(List.of("c", "d")).pullThrough(true).backingServiceActive(false).build()
    );
    private static final LookupTable NEW_LOOKUP_TABLE = LookupTable.builder().name("new").build();

    private final LookupTablesRepo lookupTablesRepo = Mockito.mock(LookupTablesRepo.class);
    private final CoreEventQueue coreEventQueue = Mockito.mock(CoreEventQueue.class);
    private final IdentityService identityService = Mockito.mock(IdentityService.class);
    private final TestClock clock = new TestClock();
    private final DataSource dataSource = Mockito.mock(DataSource.class);
    private final Connection connection = Mockito.mock((Connection.class));
    private final Statement statement = Mockito.mock(Statement.class);

    private LookupTableService lookupTableService;

    private final LookupTableRepo existing1 = Mockito.mock(LookupTableRepo.class);
    private final LookupTableRepo existing2 = Mockito.mock(LookupTableRepo.class);
    private final LookupTableRepo new1 = Mockito.mock(LookupTableRepo.class);

    @BeforeEach
    public void setUp() throws SQLException {
        Mockito.when(lookupTablesRepo.findAll()).thenReturn(
                EXISTING_LOOKUP_TABLES.stream().map(LookupTableEntity::fromLookupTable).toList());

        Mockito.when(identityService.getUniqueId()).thenReturn("test-host");

        Mockito.when(existing1.getLookupTable()).thenReturn(EXISTING_LOOKUP_TABLES.getFirst());
        Mockito.when(existing2.getLookupTable()).thenReturn(EXISTING_LOOKUP_TABLES.getLast());
        Mockito.when(new1.getLookupTable()).thenReturn(NEW_LOOKUP_TABLE);

        MockLookupTableRepoFactory mockLookupTableRepoFactory = new MockLookupTableRepoFactory(Map.of(
                "existing-1", existing1,
                "existing-2", existing2,
                "new", new1
        ));

        lookupTableService = new LookupTableService(lookupTablesRepo, mockLookupTableRepoFactory, coreEventQueue,
                identityService, clock, dataSource);

        Mockito.when(connection.createStatement()).thenReturn(statement);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    public void validatesLookupTableCreation() {
        List<String> errors = lookupTableService.validateLookupTableCreation(
                LookupTable.builder().sourcePlugin(PLUGIN2).name("existing-1").refreshDuration("bad").build());

        Assertions.assertEquals(2, errors.size());
        Assertions.assertEquals("Lookup table existing-1 has an invalid refresh duration: bad", errors.getFirst());
        Assertions.assertEquals("Lookup table existing-1 exists in another plugin: a:plugin-1:1.0.0", errors.getLast());
    }

    @Test
    public void createsLookupTable() throws LookupTableServiceException {
        lookupTableService.createLookupTable(NEW_LOOKUP_TABLE, true);

        Mockito.verify(new1).create();
        Mockito.verify(lookupTablesRepo).saveAndFlush(Mockito.any(LookupTableEntity.class));
    }

    @Test
    public void doesntCreateLookupTableIfExistsWithSameColumns() throws LookupTableServiceException {
        lookupTableService.createLookupTable(EXISTING_LOOKUP_TABLES.getFirst(), true);

        Mockito.verify(new1, Mockito.never()).create();
        Mockito.verify(lookupTablesRepo).saveAndFlush(Mockito.any(LookupTableEntity.class)); // Saves to set backing service active
    }

    @Test
    public void recreatesLookupTableIfExistsWithDifferentColumns() throws LookupTableServiceException, SQLException {
        lookupTableService.createLookupTable(LookupTable.builder()
                .sourcePlugin(EXISTING_LOOKUP_TABLES.getFirst().getSourcePlugin())
                .name(EXISTING_LOOKUP_TABLES.getFirst().getName())
                .columns(List.of("x", "y"))
                .build(),
                true);

        Mockito.verify(lookupTablesRepo).deleteById("existing-1");
        Mockito.verify(statement).execute("DROP TABLE existing-1;");
        Mockito.verify(existing1).create();
        Mockito.verify(lookupTablesRepo).saveAndFlush(Mockito.any(LookupTableEntity.class));
    }

    @Test
    public void deletesLookupTable() throws SQLException, LookupTableServiceException {
        // Make sure lookupTablesRepoMap is initialized
        lookupTableService.upsertRows("existing-2", Collections.emptyList());
        Mockito.clearInvocations(lookupTablesRepo);

        lookupTableService.deleteLookupTable("existing-2");

        Mockito.verify(lookupTablesRepo).deleteById("existing-2");
        Mockito.verify(statement).execute("DROP TABLE existing-2;");

        lookupTableService.upsertRows("existing-2", Collections.emptyList());

        // Had to reload the tables since the name was missing from the cached repos
        Mockito.verify(lookupTablesRepo).findAll();
    }

    @Test
    public void getsLookupTables() {
        List<LookupTable> lookupTables = lookupTableService.getLookupTables();

        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.size(), lookupTables.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void addsRows() throws LookupTableServiceException {
        ArgumentCaptor<Map<String, String>> rowCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.when(existing1.upsert(rowCaptor.capture(), Mockito.eq(OffsetDateTime.now(clock)))).thenReturn(1);

        Map<String, String> row1 = Map.of("a", "1a", "b", "1b");
        Map<String, String> row2 = Map.of("a", "2a", "b", "2b");
        lookupTableService.upsertRows(EXISTING_LOOKUP_TABLES.getFirst().getName(), List.of(row1, row2));

        List<Map<String, String>> capturedRows = rowCaptor.getAllValues();
        Assertions.assertEquals(2, capturedRows.size());
        Assertions.assertEquals(row1, capturedRows.getFirst());
        Assertions.assertEquals(row2, capturedRows.getLast());
    }

    @Test
    public void lookupNoPullThrough() throws LookupTableServiceException, JsonProcessingException {
        Map<String, String> row1 = Map.of("a", "1a", "b", "1b");
        Map<String, String> row2 = Map.of("a", "2a", "b", "2b");
        Map<String, String> row3 = Map.of("a", "3a", "b", "3b");

        Mockito.when(existing2.find(Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null))).thenReturn(Pair.of(3, List.of(row1, row2, row3)));

        Pair<Integer, List<Map<String, String>>> results = lookupTableService.lookup(EXISTING_LOOKUP_TABLES.getLast().getName(), null, null, null, null, null, null);

        Assertions.assertEquals(List.of(row1, row2, row3), results.getRight());

        Mockito.verify(coreEventQueue, Mockito.never()).putLookupTableEvent(Mockito.any(LookupTableEvent.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pullsThrough() throws LookupTableServiceException, JsonProcessingException {
        Map<String, String> row1 = Map.of("a", "1a", "b", "1b");
        Map<String, String> row2 = Map.of("a", "2a", "b", "2b");
        Map<String, String> row3 = Map.of("a", "3a", "b", "3b");

        // Pull-through lookup supplier returns row1 and row2
        Mockito.when(coreEventQueue.takeLookupTableResult(Mockito.anyString()))
                .thenReturn(new LookupTableEventResult("lookup-table-event-0", EXISTING_LOOKUP_TABLES.getFirst().getName(),
                        List.of(row1, row2)));

        Mockito.when(existing1.upsert(Mockito.anyMap(), Mockito.eq(OffsetDateTime.now(clock)))).thenReturn(1);

        Mockito.when(existing1.find(Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null))).thenReturn(Pair.of(3, List.of(row1, row2, row3)));

        Pair<Integer, List<Map<String, String>>> results = lookupTableService.lookup(EXISTING_LOOKUP_TABLES.getFirst().getName(), null, null, null, null, null, null);

        Assertions.assertEquals(List.of(row1, row2, row3), results.getRight());

        // Pull-through lookup supplier was sent an appropriate event through the queue
        ArgumentCaptor<LookupTableEvent> lookupTableEventCaptor = ArgumentCaptor.forClass(LookupTableEvent.class);
        Mockito.verify(coreEventQueue).putLookupTableEvent(lookupTableEventCaptor.capture());
        LookupTableEvent lookupTableEvent = lookupTableEventCaptor.getValue();
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableEvent.getLookupTableName());
        Assertions.assertNull(lookupTableEvent.getMatchingColumnValues());
        Assertions.assertNull(lookupTableEvent.getResultColumns());

        // Pull-through response upserted row1 and row2
        ArgumentCaptor<Map<String, String>> rowCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(existing1, Mockito.times(2)).upsert(rowCaptor.capture(), Mockito.eq(OffsetDateTime.now(clock)));
        Assertions.assertEquals(List.of(row1, row2), rowCaptor.getAllValues());
    }

    @Test
    public void pullThroughTimesOut() throws LookupTableServiceException, JsonProcessingException {
        Map<String, String> row3 = Map.of("a", "3a", "b", "3b");

        // Pull-through lookup supplier returns null (signifying timeout)
        Mockito.when(coreEventQueue.takeLookupTableResult(Mockito.anyString())).thenReturn(null);

        Mockito.when(existing1.find(Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null), Mockito.eq(null))).thenReturn(Pair.of(1, List.of(row3)));

        Pair<Integer, List<Map<String, String>>> results = lookupTableService.lookup(EXISTING_LOOKUP_TABLES.getFirst().getName(), null, null, null, null, null, null);

        Assertions.assertEquals(List.of(row3), results.getRight());

        // Pull-through lookup supplier was sent an appropriate event through the queue
        ArgumentCaptor<LookupTableEvent> lookupTableEventCaptor = ArgumentCaptor.forClass(LookupTableEvent.class);
        Mockito.verify(coreEventQueue).putLookupTableEvent(lookupTableEventCaptor.capture());
        LookupTableEvent lookupTableEvent = lookupTableEventCaptor.getValue();
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableEvent.getLookupTableName());
        Assertions.assertNull(lookupTableEvent.getMatchingColumnValues());
        Assertions.assertNull(lookupTableEvent.getResultColumns());

        // Key is removed from queue
        Mockito.verify(coreEventQueue).dropLookupTableEvent(Mockito.eq(lookupTableEvent));

        // Failed pull-through results in service marked inactive
        ArgumentCaptor<LookupTableEntity> lookupTableCaptor = ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo).saveAndFlush(lookupTableCaptor.capture());
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableCaptor.getValue().getName());
        Assertions.assertFalse(lookupTableCaptor.getValue().isBackingServiceActive());

        Mockito.verify(existing1, Mockito.never()).upsert(Mockito.anyMap(), Mockito.any(OffsetDateTime.class));

        EXISTING_LOOKUP_TABLES.getFirst().setBackingServiceActive(true);
    }

    @Test
    public void setsBackingServiceInactive() throws LookupTableServiceException {
        lookupTableService.setBackingServiceActive(EXISTING_LOOKUP_TABLES.getFirst().getName(), false);

        ArgumentCaptor<LookupTableEntity> lookupTableCaptor = ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo).saveAndFlush(lookupTableCaptor.capture());
        Assertions.assertFalse(lookupTableCaptor.getValue().isBackingServiceActive());

        EXISTING_LOOKUP_TABLES.getFirst().setBackingServiceActive(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updatesTable() throws LookupTableServiceException, IOException {
        String csv = """
                a,b
                1a,1b
                2a,2b
                """;
        lookupTableService.updateTable(EXISTING_LOOKUP_TABLES.getFirst().getName(),
                new ByteArrayInputStream(csv.getBytes()));

        ArgumentCaptor<Map<String, String>> rowCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(existing1, Mockito.times(2)).upsert(rowCaptor.capture(), Mockito.eq(OffsetDateTime.now(clock)));
        List<Map<String, String>> capturedRows = rowCaptor.getAllValues();
        Assertions.assertEquals(2, capturedRows.size());
        Assertions.assertEquals(Map.of("a", "1a", "b", "1b"), capturedRows.getFirst());
        Assertions.assertEquals(Map.of("a", "2a", "b", "2b"), capturedRows.getLast());

        Mockito.verify(existing1).deleteOlder(Mockito.eq(OffsetDateTime.now(clock)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void refreshesTable() throws LookupTableServiceException, JsonProcessingException {
        Map<String, String> row1 = Map.of("a", "1a", "b", "1b");
        Map<String, String> row2 = Map.of("a", "2a", "b", "2b");
        Map<String, String> row3 = Map.of("a", "3a", "b", "3b");

        // Lookup supplier returns all rows
        Mockito.when(coreEventQueue.takeLookupTableResult(Mockito.anyString()))
                .thenReturn(new LookupTableEventResult("lookup-table-event-0", EXISTING_LOOKUP_TABLES.getFirst().getName(),
                        List.of(row1, row2, row3)));

        Mockito.when(existing1.upsert(Mockito.anyMap(), Mockito.eq(OffsetDateTime.now(clock)))).thenReturn(1);

        clock.setInstant(Instant.now());
        lookupTableService.refresh(EXISTING_LOOKUP_TABLES.getFirst().getName());

        // Lookup supplier was sent an appropriate event through the queue
        ArgumentCaptor<LookupTableEvent> lookupTableEventCaptor = ArgumentCaptor.forClass(LookupTableEvent.class);
        Mockito.verify(coreEventQueue).putLookupTableEvent(lookupTableEventCaptor.capture());
        LookupTableEvent lookupTableEvent = lookupTableEventCaptor.getValue();
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableEvent.getLookupTableName());
        Assertions.assertNull(lookupTableEvent.getMatchingColumnValues());
        Assertions.assertNull(lookupTableEvent.getResultColumns());

        // Upserted all rows
        ArgumentCaptor<Map<String, String>> rowCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(existing1, Mockito.times(3)).upsert(rowCaptor.capture(), Mockito.eq(OffsetDateTime.now(clock)));
        Assertions.assertEquals(List.of(row1, row2, row3), rowCaptor.getAllValues());

        Mockito.verify(existing1).deleteOlder(Mockito.eq(OffsetDateTime.now(clock)));

        ArgumentCaptor<LookupTableEntity> lookupTableCaptor = ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo).saveAndFlush(lookupTableCaptor.capture());
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableCaptor.getValue().getName());
        Assertions.assertEquals(lookupTableCaptor.getValue().getLastRefresh(), OffsetDateTime.now(clock));
    }

    @Test
    public void updatesVariables() {
        Variable variable1 = Variable.builder().build();
        Variable variable2 = Variable.builder().build();

        lookupTableService.updateVariables(List.of("existing-1", "existing-2"), List.of(variable1, variable2));

        ArgumentCaptor<LookupTableEntity> lookupTableEntityArgumentCaptor =
                ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo, Mockito.times(2)).saveAndFlush(lookupTableEntityArgumentCaptor.capture());
        List<LookupTableEntity> lookupTableEntities = lookupTableEntityArgumentCaptor.getAllValues();
        Assertions.assertEquals(2, lookupTableEntities.size());
        Assertions.assertEquals("existing-1", lookupTableEntities.getFirst().getName());
        Assertions.assertEquals(2, lookupTableEntities.getFirst().getVariables().size());
        Assertions.assertEquals("existing-2", lookupTableEntities.getLast().getName());
        Assertions.assertEquals(2, lookupTableEntities.getLast().getVariables().size());
    }
}
