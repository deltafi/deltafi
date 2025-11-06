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
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.lookup.*;
import org.deltafi.common.types.SortDirection;
import org.deltafi.common.types.Variable;
import org.deltafi.core.services.*;
import org.deltafi.core.types.PluginEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty("lookup.enabled")
@Service
@Slf4j
public class LookupTableService implements PluginCleaner {
    private final LookupTablesRepo lookupTablesRepo;
    private final LookupTableRepoFactory lookupTableRepoFactory;
    private final CoreEventQueue coreEventQueue;
    private final IdentityService identityService;
    private final Clock clock;
    private final DataSource lookupDataSource;

    private Map<String, LookupTableRepo> lookupTableRepoMap = new HashMap<>();

    public LookupTableService(LookupTablesRepo lookupTablesRepo, LookupTableRepoFactory lookupTableRepoFactory,
            CoreEventQueue coreEventQueue, IdentityService identityService, Clock clock,
            @Qualifier("lookup") DataSource lookupDataSource) {
        this.lookupTablesRepo = lookupTablesRepo;
        this.lookupTableRepoFactory = lookupTableRepoFactory;
        this.coreEventQueue = coreEventQueue;
        this.identityService = identityService;
        this.clock = clock;
        this.lookupDataSource = lookupDataSource;
    }

    public List<String> validateLookupTableCreation(LookupTable lookupTable) {
        List<String> errors = new ArrayList<>();

        if (lookupTable.getRefreshDuration() != null) {
            try {
                Duration.parse(lookupTable.getRefreshDuration());
            } catch (DateTimeParseException e) {
                errors.add("Lookup table %s has an invalid refresh duration: %s".formatted(lookupTable.getName(),
                        lookupTable.getRefreshDuration()));
            }
        }

        loadLookupTables();

        LookupTableRepo existingLookupTableRepo = getLookupTableRepoForValidation(lookupTable.getName());

        if ((existingLookupTableRepo != null) && !existingLookupTableRepo.getLookupTable().getSourcePlugin()
                .equalsIgnoreVersion(lookupTable.getSourcePlugin())) {
            errors.add("Lookup table %s exists in another plugin: %s".formatted(lookupTable.getName(),
                    existingLookupTableRepo.getLookupTable().getSourcePlugin()));
        }

        return errors;
    }

    public void createLookupTable(LookupTable lookupTable, boolean validate) throws LookupTableServiceException {
        log.info("Creating lookup table {}", lookupTable.getName());

        if (validate) {
            List<String> errors = validateLookupTableCreation(lookupTable);
            if (!errors.isEmpty()) {
                throw new LookupTableServiceException(errors);
            }
        }

        LookupTableRepo existingLookupTableRepo = getLookupTableRepoForValidation(lookupTable.getName());
        if (existingLookupTableRepo != null) {
            if (existingLookupTableRepo.getLookupTable().getColumns().equals(lookupTable.getColumns())) {
                if (existingLookupTableRepo.getLookupTable().isServiceBacked()) {
                    existingLookupTableRepo.getLookupTable().setBackingServiceActive(true);
                    saveLookupTable(existingLookupTableRepo.getLookupTable());
                }
                return; // Already created with the same columns
            }
            deleteLookupTable(lookupTable.getName());
        }

        LookupTableRepo lookupTableRepo = lookupTableRepoFactory.create(lookupTable);
        lookupTableRepo.create();

        saveLookupTable(lookupTable);

        lookupTableRepoMap.put(lookupTable.getName(), lookupTableRepo);
    }

    private void loadLookupTables() {
        lookupTableRepoMap = lookupTablesRepo.findAll().stream()
                .map(LookupTableEntity::toLookupTable)
                .collect(Collectors.toMap(LookupTable::getName, lookupTableRepoFactory::create));
    }

    private LookupTableRepo getLookupTableRepoForValidation(String name) {
        try {
            return getLookupTableRepo(name);
        } catch (LookupTableServiceException e) {
            return null;
        }
    }

    private LookupTableRepo getLookupTableRepo(String name) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = lookupTableRepoMap.get(name);

        if (lookupTableRepo == null) {
            loadLookupTables();
            lookupTableRepo = lookupTableRepoMap.get(name);
        }

        if (lookupTableRepo == null) {
            throw new LookupTableServiceException("Lookup table doesn't exist: " + name);
        }

        return lookupTableRepo;
    }

    private void saveLookupTable(LookupTable lookupTable) {
        lookupTablesRepo.saveAndFlush(LookupTableEntity.fromLookupTable(lookupTable));
    }

    public void deleteLookupTable(String lookupTableName) throws LookupTableServiceException {
        log.info("Deleting lookup table {}", lookupTableName);
        lookupTablesRepo.deleteById(lookupTableName);
        lookupTableRepoMap.remove(lookupTableName);
        try (Connection connection = lookupDataSource.getConnection()) {
            connection.createStatement().execute("DROP TABLE " + lookupTableName + ";");
        } catch (SQLException e) {
            throw new LookupTableServiceException("Failed to drop table " + lookupTableName + ": " + e.getMessage());
        }
    }

    public List<LookupTable> getLookupTables() {
        loadLookupTables();
        List<LookupTable> lookupTables = new ArrayList<>();
        lookupTableRepoMap.values().forEach(lookupTableRepo -> {
            LookupTable lookupTable = lookupTableRepo.getLookupTable();
            lookupTable.setTotalRows(lookupTableRepo.count());
            lookupTables.add(lookupTable);
        });
        return lookupTables;
    }

    public LookupTable getLookupTable(String lookupTableName) {
        loadLookupTables();
        LookupTableRepo lookupTableRepo = lookupTableRepoMap.get(lookupTableName);
        return lookupTableRepo != null ? lookupTableRepo.getLookupTable() : null;
    }

    public void upsertRows(String name, List<Map<String, String>> rows) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = getLookupTableRepo(name);
        int rowsAdded = 0;
        for (Map<String, String> row : rows) {
            rowsAdded += lookupTableRepo.upsert(row, OffsetDateTime.now(clock));
        }
        if (rowsAdded != rows.size()) {
            throw new LookupTableServiceException("Failed to add rows to lookup table: " + name);
        }
    }

    public void removeRows(String name, List<Map<String, String>> rows) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = getLookupTableRepo(name);
        int rowsRemoved = 0;
        for (Map<String, String> row : rows) {
            rowsRemoved += lookupTableRepo.delete(row);
        }
        if (rowsRemoved != rows.size()) {
            throw new LookupTableServiceException("Failed to remove rows from lookup table: " + name);
        }
    }

    public Pair<Integer, List<Map<String, String>>> lookup(String name,
            @Nullable Map<String, Set<String>> matchingColumnValues, @Nullable List<String> resultColumns,
            @Nullable String sortColumn, @Nullable SortDirection sortDirection, @Nullable Integer offset,
            @Nullable Integer limit) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = getLookupTableRepo(name);

        if (lookupTableRepo.getLookupTable().isPullThrough() &&
                lookupTableRepo.getLookupTable().isBackingServiceActive()) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            upsertFromSupplier(lookupTableRepo, matchingColumnValues, resultColumns, now);
        }

        return lookupTableRepo.find(matchingColumnValues, resultColumns, sortColumn, sortDirection, offset, limit);
    }

    public void loadFromSupplier(String name) throws LookupTableServiceException {
        upsertFromSupplier(getLookupTableRepo(name), null, null, OffsetDateTime.now(clock));
    }

    private boolean upsertFromSupplier(LookupTableRepo lookupTableRepo,
            @Nullable Map<String, Set<String>> matchingColumnValues, @Nullable List<String> resultColumns,
            OffsetDateTime now) {
        LookupTable lookupTable = lookupTableRepo.getLookupTable();
        try {
            Map<String, String> pluginVariableMap = lookupTable.getVariables() == null ? Collections.emptyMap() :
                    lookupTable.getVariables().stream()
                            .filter(pluginVariable -> (pluginVariable.getValue() != null) ||
                                    (pluginVariable.getDefaultValue() != null))
                            .collect(Collectors.toMap(Variable::getName, pluginVariable ->
                                    pluginVariable.getValue() != null ? pluginVariable.getValue() :
                                            pluginVariable.getDefaultValue()));

            LookupTableEvent lookupTableEvent = new LookupTableEvent(identityService.getUniqueId(),
                    lookupTable.getName(), matchingColumnValues, resultColumns, pluginVariableMap);
            coreEventQueue.putLookupTableEvent(lookupTableEvent);

            LookupTableEventResult lookupTableEventResult =
                    coreEventQueue.takeLookupTableResult(lookupTableEvent.getId());
            if (lookupTableEventResult != null) {
                lookupTableEventResult.getRows().forEach(row -> lookupTableRepo.upsert(row, now));
                return true;
            }

            coreEventQueue.dropLookupTableEvent(lookupTableEvent);

            log.warn("Lookup table supplier for {} didn't respond. Disabling supplier.",
                    lookupTableEvent.getLookupTableName());
            lookupTable.setBackingServiceActive(false);
            saveLookupTable(lookupTableRepo.getLookupTable());
        } catch (JsonProcessingException e) {
            log.error("Unable to process results from supplier for lookup table {}",
                    lookupTable.getName(), e);
        }
        return false;
    }

    public void setBackingServiceActive(String name, boolean active) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = getLookupTableRepo(name);
        lookupTableRepo.getLookupTable().setBackingServiceActive(active);
        saveLookupTable(lookupTableRepo.getLookupTable());
    }

    public void updateTable(String name, List<Map<String, String>> rows) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = getLookupTableRepo(name);

        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Map<String, String> row : rows) {
            lookupTableRepo.upsert(row, now);
        }

        lookupTableRepo.deleteOlder(now);

        lookupTableRepo.getLookupTable().setLastRefresh(now);
        saveLookupTable(lookupTableRepo.getLookupTable());
    }

    private static final CsvMapper CSV_MAPPER = new CsvMapper();

    public void updateTable(String name, InputStream csv) throws LookupTableServiceException, IOException {
        CsvSchema schema = CsvSchema.emptySchema().withHeader(); // Use the first row as headers
        try (MappingIterator<Map<String, String>> csvReader =
                CSV_MAPPER.readerFor(Map.class).with(schema).readValues(csv)) {
            updateTable(name, csvReader.readAll());
        }
    }

    public void refresh(String name) throws LookupTableServiceException {
        LookupTableRepo lookupTableRepo = getLookupTableRepo(name);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (upsertFromSupplier(lookupTableRepo, null, null, now)) {
            lookupTableRepo.deleteOlder(now);
            lookupTableRepo.getLookupTable().setLastRefresh(now);
            saveLookupTable(lookupTableRepo.getLookupTable());
        }
    }

    @Override
    public void cleanupFor(PluginEntity plugin) {
        plugin.getLookupTables().forEach(lookupTable -> {
            try {
                deleteLookupTable(lookupTable.getName());
            } catch (LookupTableServiceException e) {
                log.warn(e.getMessage());
            }
        });
    }

    public void updateVariables(List<String> lookupTableNames, List<Variable> variables) {
        lookupTableNames.forEach(lookupTableName -> {
            try {
                LookupTable lookupTable = getLookupTableRepo(lookupTableName).getLookupTable();
                lookupTable.setVariables(variables);
                lookupTablesRepo.saveAndFlush(LookupTableEntity.fromLookupTable(lookupTable));
            } catch (LookupTableServiceException e) {
                log.warn("Unable to set variables in lookup table. Lookup table not found: {}", lookupTableName);
            }
        });
    }
}
