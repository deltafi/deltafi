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
package org.deltafi.core.lookup;

import tools.jackson.core.JacksonException;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.lookup.*;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.generated.types.ColumnValue;
import org.deltafi.core.generated.types.LookupResults;
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

import static org.assertj.core.api.Assertions.assertThat;

class LookupTableServiceTest {
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
            LookupTable.builder().sourcePlugin(PLUGIN1).name("existing_1").columns(List.of("a", "b")).keyColumns(List.of("a")).pullThrough(true).build(),
            LookupTable.builder().sourcePlugin(PLUGIN1).name("existing_2").columns(List.of("c", "d")).keyColumns(List.of("c")).pullThrough(true).backingServiceActive(false).build()
    );
    private static final LookupTable NEW_LOOKUP_TABLE = LookupTable.builder().columns(List.of("a")).keyColumns(List.of("a")).name("new").build();

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
    void setUp() throws SQLException {
        Mockito.when(lookupTablesRepo.findAll()).thenReturn(
                EXISTING_LOOKUP_TABLES.stream().map(LookupTableEntity::fromLookupTable).toList());

        Mockito.when(identityService.getUniqueId()).thenReturn("test-host");

        Mockito.when(existing1.getLookupTable()).thenReturn(EXISTING_LOOKUP_TABLES.getFirst());
        Mockito.when(existing2.getLookupTable()).thenReturn(EXISTING_LOOKUP_TABLES.getLast());
        Mockito.when(new1.getLookupTable()).thenReturn(NEW_LOOKUP_TABLE);

        MockLookupTableRepoFactory mockLookupTableRepoFactory = new MockLookupTableRepoFactory(Map.of(
                "existing_1", existing1,
                "existing_2", existing2,
                "new", new1
        ));

        lookupTableService = new LookupTableService(lookupTablesRepo, mockLookupTableRepoFactory, coreEventQueue,
                identityService, clock, dataSource);

        Mockito.when(connection.createStatement()).thenReturn(statement);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void validatesLookupTableCreation() {
        List<String> errors = lookupTableService.validateLookupTableCreation(
                LookupTable.builder().sourcePlugin(PLUGIN2).name("existing_1").refreshDuration("bad").build());

        assertThat(errors).hasSize(4)
                .contains("Lookup table existing_1 has an empty key columns list")
                .contains("Lookup table existing_1 has an empty columns list")
                .contains("Lookup table existing_1 has an invalid refresh duration: bad")
                .contains("Lookup table existing_1 exists in another plugin: a:plugin-1:1.0.0");
    }

    @Test
    void validate_nonPositiveRefreshDuration() {
        List<String> errors = lookupTableService.validateLookupTableCreation(
                LookupTable.builder().sourcePlugin(PLUGIN2).name("valid_table")
                        .keyColumns(List.of("a")).columns(List.of("a")).refreshDuration("PT0S").build());

        assertThat(errors).contains("Lookup table valid_table has a non-positive refresh duration: PT0S");
    }

    @Test
    void validate_invalidNameChars() {
        List<String> errors = lookupTableService.validateLookupTableCreation(
                LookupTable.builder().sourcePlugin(PLUGIN2).name("existing-no-dashes-allowed").keyColumns(List.of("a")).columns(List.of("a")).refreshDuration("PT1M").build());

        assertThat(errors).hasSize(1)
                .contains("Table name 'existing-no-dashes-allowed' contains invalid characters: '-'. Only letters (a-z, A-Z), digits (0-9), and underscores (_) are allowed. Please rename using only these characters.");
    }

    @Test
    void validate_nameTooLong() {
        List<String> errors = lookupTableService.validateLookupTableCreation(
                LookupTable.builder().sourcePlugin(PLUGIN2).name("postgres_does_not_allow_names_that_are_too_long_so_reject_this_instead_bad_grammar_exception").keyColumns(List.of("a")).columns(List.of("a")).refreshDuration("PT1M").build());

        assertThat(errors).hasSize(1)
                .contains("Table name 'postgres_does_not_allow_names_that_are_too_long_so_reject_this_instead_bad_grammar_exception' is too long (92 characters). PostgreSQL limits identifiers to 63 characters. Please shorten the name.");
    }

    @Test
    void validate_keyNotInColumns() {
        List<String> errors = lookupTableService.validateLookupTableCreation(
                LookupTable.builder().sourcePlugin(PLUGIN2).name("table_name").keyColumns(List.of("c", "d", "e")).columns(List.of("a", "b", "c")).refreshDuration("PT1M").build());

        assertThat(errors).hasSize(1)
                .contains("Lookup table table_name has keys (d, e) which are not in the column list");
    }

    @Test
    void createsLookupTable() throws LookupTableServiceException {
        lookupTableService.createLookupTable(NEW_LOOKUP_TABLE, true);

        Mockito.verify(new1).create();
        Mockito.verify(lookupTablesRepo).saveAndFlush(Mockito.any(LookupTableEntity.class));
    }

    @Test
    void doesntCreateLookupTableIfExistsWithSameColumns() throws LookupTableServiceException {
        lookupTableService.createLookupTable(EXISTING_LOOKUP_TABLES.getFirst(), true);

        Mockito.verify(new1, Mockito.never()).create();
        Mockito.verify(lookupTablesRepo).saveAndFlush(Mockito.any(LookupTableEntity.class)); // Saves to set backing service active
    }

    @Test
    void recreatesLookupTableIfExistsWithDifferentColumns() throws LookupTableServiceException, SQLException {
        lookupTableService.createLookupTable(LookupTable.builder()
                .sourcePlugin(EXISTING_LOOKUP_TABLES.getFirst().getSourcePlugin())
                .name(EXISTING_LOOKUP_TABLES.getFirst().getName())
                .columns(List.of("x", "y"))
                .keyColumns(List.of("x"))
                .build(),
                true);

        Mockito.verify(lookupTablesRepo).deleteById("existing_1");
        Mockito.verify(statement).execute("DROP TABLE existing_1;");
        Mockito.verify(existing1).create();
        Mockito.verify(lookupTablesRepo).saveAndFlush(Mockito.any(LookupTableEntity.class));
    }

    @Test
    void deletesLookupTable() throws SQLException, LookupTableServiceException {
        // Make sure lookupTablesRepoMap is initialized
        lookupTableService.upsertRows("existing_2", Collections.emptyList());
        Mockito.clearInvocations(lookupTablesRepo);

        lookupTableService.deleteLookupTable("existing_2");

        Mockito.verify(lookupTablesRepo).deleteById("existing_2");
        Mockito.verify(statement).execute("DROP TABLE existing_2;");

        lookupTableService.upsertRows("existing_2", Collections.emptyList());

        // Had to reload the tables since the name was missing from the cached repos
        Mockito.verify(lookupTablesRepo).findAll();
    }

    @Test
    void getsLookupTables() {
        List<LookupTable> lookupTables = lookupTableService.getLookupTables();

        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.size(), lookupTables.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsRows() throws LookupTableServiceException {
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
    void lookupNoPullThrough() throws LookupTableServiceException, JacksonException {
        List<ColumnValue> row1 = toColumnValueRow(Map.of("a", "1a", "b", "1b"));
        List<ColumnValue> row2 = toColumnValueRow(Map.of("a", "2a", "b", "2b"));
        List<ColumnValue> row3 = toColumnValueRow(Map.of("a", "3a", "b", "3b"));

        LookupResults mockResult = LookupResults.newBuilder().rows(List.of(row1, row2, row3)).totalCount(3).build();
        Mockito.when(existing2.find(null, null, null, null, null, null)).thenReturn(mockResult);

        LookupResults results = lookupTableService.lookup(EXISTING_LOOKUP_TABLES.getLast().getName(), null, null, null, null, null, null);

        Assertions.assertEquals(mockResult,  results);

        Mockito.verify(coreEventQueue, Mockito.never()).putLookupTableEvent(Mockito.any(LookupTableEvent.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pullsThrough() throws LookupTableServiceException, JacksonException {
        Map<String, String> row1 = Map.of("a", "1a", "b", "1b");
        Map<String, String> row2 = Map.of("a", "2a", "b", "2b");

        List<ColumnValue> cvRow1 = toColumnValueRow(row1);
        List<ColumnValue> cvRow2 = toColumnValueRow(row2);
        List<ColumnValue> row3 = toColumnValueRow(Map.of("a", "3a", "b", "3b"));

        // Pull-through lookup supplier returns row1 and row2
        Mockito.when(coreEventQueue.takeLookupTableResult(Mockito.anyString()))
                .thenReturn(new LookupTableEventResult("lookup-table-event-0", EXISTING_LOOKUP_TABLES.getFirst().getName(),
                        List.of(row1, row2)));

        Mockito.when(existing1.upsert(Mockito.anyMap(), Mockito.eq(OffsetDateTime.now(clock)))).thenReturn(1);

        LookupResults mockResult = LookupResults.newBuilder().rows(List.of(cvRow1, cvRow2, row3)).totalCount(3).build();
        Mockito.when(existing1.find(null, null, null, null, null, null)).thenReturn(mockResult);

        LookupResults results = lookupTableService.lookup(EXISTING_LOOKUP_TABLES.getFirst().getName(), null, null, null, null, null, null);

        Assertions.assertEquals(mockResult, results);

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
    void pullThroughTimesOut() throws LookupTableServiceException, JacksonException {
        List<ColumnValue> row3 = toColumnValueRow(Map.of("a", "3a", "b", "3b"));

        // Pull-through lookup supplier returns null (signifying timeout)
        Mockito.when(coreEventQueue.takeLookupTableResult(Mockito.anyString())).thenReturn(null);

        LookupResults mockResults = LookupResults.newBuilder().totalCount(1).rows(List.of(row3)).build();
        Mockito.when(existing1.find(null, null, null, null, null, null)).thenReturn(mockResults);

        LookupResults results = lookupTableService.lookup(EXISTING_LOOKUP_TABLES.getFirst().getName(), null, null, null, null, null, null);

        Assertions.assertEquals(mockResults, results);

        // Pull-through lookup supplier was sent an appropriate event through the queue
        ArgumentCaptor<LookupTableEvent> lookupTableEventCaptor = ArgumentCaptor.forClass(LookupTableEvent.class);
        Mockito.verify(coreEventQueue).putLookupTableEvent(lookupTableEventCaptor.capture());
        LookupTableEvent lookupTableEvent = lookupTableEventCaptor.getValue();
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableEvent.getLookupTableName());
        Assertions.assertNull(lookupTableEvent.getMatchingColumnValues());
        Assertions.assertNull(lookupTableEvent.getResultColumns());

        // Key is removed from queue
        Mockito.verify(coreEventQueue).dropLookupTableEvent(lookupTableEvent);

        // Failed pull-through results in service marked inactive
        ArgumentCaptor<LookupTableEntity> lookupTableCaptor = ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo).saveAndFlush(lookupTableCaptor.capture());
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableCaptor.getValue().getName());
        Assertions.assertFalse(lookupTableCaptor.getValue().isBackingServiceActive());

        Mockito.verify(existing1, Mockito.never()).upsert(Mockito.anyMap(), Mockito.any(OffsetDateTime.class));

        EXISTING_LOOKUP_TABLES.getFirst().setBackingServiceActive(true);
    }

    @Test
    void setsBackingServiceInactive() throws LookupTableServiceException {
        lookupTableService.setBackingServiceActive(EXISTING_LOOKUP_TABLES.getFirst().getName(), false);

        ArgumentCaptor<LookupTableEntity> lookupTableCaptor = ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo).saveAndFlush(lookupTableCaptor.capture());
        Assertions.assertFalse(lookupTableCaptor.getValue().isBackingServiceActive());

        EXISTING_LOOKUP_TABLES.getFirst().setBackingServiceActive(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesTable() throws LookupTableServiceException, IOException {
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

        Mockito.verify(existing1).deleteOlder(OffsetDateTime.now(clock));
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshesTable() throws LookupTableServiceException, JacksonException {
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

        Mockito.verify(existing1).deleteOlder(OffsetDateTime.now(clock));

        ArgumentCaptor<LookupTableEntity> lookupTableCaptor = ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo).saveAndFlush(lookupTableCaptor.capture());
        Assertions.assertEquals(EXISTING_LOOKUP_TABLES.getFirst().getName(), lookupTableCaptor.getValue().getName());
        Assertions.assertEquals(lookupTableCaptor.getValue().getLastRefresh(), OffsetDateTime.now(clock));
    }

    @Test
    void updatesVariables() {
        Variable variable1 = Variable.builder().build();
        Variable variable2 = Variable.builder().build();

        lookupTableService.updateVariables(List.of("existing_1", "existing_2"), List.of(variable1, variable2));

        ArgumentCaptor<LookupTableEntity> lookupTableEntityArgumentCaptor =
                ArgumentCaptor.forClass(LookupTableEntity.class);
        Mockito.verify(lookupTablesRepo, Mockito.times(2)).saveAndFlush(lookupTableEntityArgumentCaptor.capture());
        List<LookupTableEntity> lookupTableEntities = lookupTableEntityArgumentCaptor.getAllValues();
        Assertions.assertEquals(2, lookupTableEntities.size());
        Assertions.assertEquals("existing_1", lookupTableEntities.getFirst().getName());
        Assertions.assertEquals(2, lookupTableEntities.getFirst().getVariables().size());
        Assertions.assertEquals("existing_2", lookupTableEntities.getLast().getName());
        Assertions.assertEquals(2, lookupTableEntities.getLast().getVariables().size());
    }

    private List<ColumnValue> toColumnValueRow(Map<String, String> rowValues) {
        List<ColumnValue> columnValues = new ArrayList<>();
        for (Map.Entry<String, String> entry : rowValues.entrySet()) {
            columnValues.add(new ColumnValue(entry.getKey(), entry.getValue()));
        }
        return columnValues;
    }
}
