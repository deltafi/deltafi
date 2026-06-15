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

import lombok.Getter;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.types.SortDirection;
import org.deltafi.core.generated.types.LookupResults;
import org.deltafi.core.lookup.LookupMetadataExtractor.LookupMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class LookupTableRepo {
    @Getter
    private final LookupTable lookupTable;
    private final JdbcClient jdbcClient;

    public LookupTableRepo(LookupTable lookupTable, @Qualifier("lookup") JdbcClient lookupJdbcClient) {
        this.lookupTable = lookupTable;
        this.jdbcClient = lookupJdbcClient;
    }

    public int create() {
        String createTable = "CREATE TABLE " + quotedTableName() + " (" +
                String.join(", ", quotedColumns().stream().map(column -> column + " TEXT").toList()) +
                ", \"last_updated\" timestamp(6) with time zone" +
                ", PRIMARY KEY (" + String.join(", ", quotedKeyColumns()) + ")" +
                ");";

        int rtn = jdbcClient.sql(createTable).update();
        addModifiedTrigger();
        return rtn;
    }

    public void addModifiedTrigger() {
        String triggerName = lookupTable.getName().toLowerCase(Locale.ROOT) + "_notify_parent_modified";

        String sql = """
            CREATE TRIGGER %s
                AFTER INSERT OR UPDATE OR DELETE ON %s
                FOR EACH STATEMENT
                EXECUTE FUNCTION update_lookup_table_modified_on_child_change()
            """.formatted(quoteIdentifier(triggerName), quoteIdentifier(lookupTable.getName()));

        jdbcClient.sql(sql).update();
    }

    public int upsert(Map<String, String> rowMap, OffsetDateTime lastUpdated) {
        List<String> nonKeyColumns = new ArrayList<>(lookupTable.getColumns());
        nonKeyColumns.removeAll(lookupTable.getKeyColumns());
        nonKeyColumns = nonKeyColumns.stream().map(this::quoteIdentifier).collect(Collectors.toList());

        String insert = "INSERT INTO " + quotedTableName() + " (" + String.join(", ", quotedColumns()) +
                ", last_updated" +
                ")" +
                " VALUES (" +
                String.join(", ", Collections.nCopies(lookupTable.getColumns().size(), "?")) +
                ", ?" +
                ")" +
                " ON CONFLICT (" + String.join(", ", quotedKeyColumns()) + ")" +
                " DO UPDATE SET " +
                (nonKeyColumns.isEmpty() ? "" : (String.join(", ", nonKeyColumns.stream().map(column -> column + " = EXCLUDED." + column).toList()) + ", ")) +
                "\"last_updated\" = EXCLUDED.\"last_updated\"" +
                ";";
        return jdbcClient.sql(insert)
                .params(lookupTable.getColumns().stream().map(rowMap::get).toList())
                .param(lastUpdated)
                .update();
    }

    public int delete(Map<String, String> rowMap) {
        List<String> matchClauses = rowMap.entrySet().stream()
                .map(entry -> quoteIdentifier(entry.getKey()) + " = '" + entry.getValue() + "'")
                .toList();
        String delete = "DELETE FROM " + quotedTableName() + " WHERE " + String.join(" AND ", matchClauses) + ";";
        return jdbcClient.sql(delete)
                .update();
    }

    public void deleteOlder(OffsetDateTime lastUpdated) {
        jdbcClient.sql("DELETE FROM " + quotedTableName() + " WHERE last_updated < ?")
                .param(lastUpdated)
                .update();
    }

    public LookupResults find(
            @Nullable Map<String, Set<String>> matchingColumnValues,
            @Nullable List<String> resultColumns,
            @Nullable String sortColumn,
            @Nullable SortDirection sortDirection,
            @Nullable Integer offset,
            @Nullable Integer limit) {

        String tableName = lookupTable.getName();
        validateIdentifier(tableName);
        if (resultColumns == null || resultColumns.isEmpty()) {
            resultColumns = new ArrayList<>(lookupTable.getColumns());
            resultColumns.add("last_updated");
        }

        List<String> selectColumns = buildSelectColumns(resultColumns);
        String whereClause = buildWhereClause(matchingColumnValues);
        String orderByClause = buildOrderByClause(sortColumn, sortDirection);

        String sql = """
        SELECT %s,
               COUNT(*) OVER() AS "total_matches"
        FROM %s t
        %s
        %s
        OFFSET :offset
        LIMIT :limit
        """.formatted(
                String.join(", ", selectColumns.stream().map(c -> "t." + c).toList()),
                quoteIdentifier(tableName),
                whereClause,
                orderByClause
        );

        int effectiveOffset = offset != null ? offset : 0;
        int effectiveLimit = limit != null ? limit : Integer.MAX_VALUE;

        // get the latest timestamps for this lookup table prior to running the lookup
        LookupMetadata metadata = getLookupMetadata();

        LookupResults result = jdbcClient.sql(sql)
                .param("offset", effectiveOffset)
                .param("limit", effectiveLimit)
                .query(new LookupResultsExtractor(resultColumns, effectiveOffset));

        result.setCreated(metadata.created());
        result.setModified(metadata.modified());
        result.setTtlSeconds(ttlSeconds(metadata));
        return result;
    }

    private LookupMetadata getLookupMetadata() {
        String sql = """
                select created, modified, last_refresh, refresh_duration, pull_through, now() as db_now from lookup_tables
                where name = :table_name
                """;

        return jdbcClient.sql(sql).param("table_name", lookupTable.getName())
                .query(new LookupMetadataExtractor());
    }

    /**
     * Seconds a client may cache this result. {@code refreshDuration} is the table's freshness budget;
     * how it maps to a TTL depends on whether the table is fed by a supplier:
     * <ul>
     *   <li><b>Supplier-fed</b> ({@code lastRefresh} set): align the client cache to the next scheduled
     *       poll ({@code lastRefresh + refreshDuration}) so staleness does not compound across the
     *       supplier&rarr;Postgres and Postgres&rarr;client layers.</li>
     *   <li><b>Operator-owned</b> ({@code lastRefresh} null): treat {@code refreshDuration} as a flat
     *       sliding TTL so manual edits propagate to every consumer within the window.</li>
     * </ul>
     * Returns {@code null} when no {@code refreshDuration} is declared, when the table is pull-through
     * (it fetches from its supplier on every lookup, so caching would defeat that), or when a
     * supplier-fed table is already overdue for its next poll — in those cases the result must not be cached.
     */
    static Integer ttlSeconds(LookupMetadata metadata) {
        if (metadata.pullThrough() || metadata.refreshDuration() == null) {
            return null;
        }
        Duration refreshDuration = Duration.parse(metadata.refreshDuration());
        long seconds = metadata.lastRefresh() != null
                ? Duration.between(metadata.dbNow(), metadata.lastRefresh().plus(refreshDuration)).getSeconds()
                : refreshDuration.getSeconds();
        return seconds > 0 ? (int) Math.min(seconds, Integer.MAX_VALUE) : null;
    }

    public int count() {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + quotedTableName() + ";").query(Integer.class).single();
    }

    private String quotedTableName() {
        return quoteIdentifier(lookupTable.getName());
    }

    private List<String> quotedColumns() {
        return lookupTable.getColumns().stream().map(this::quoteIdentifier).toList();
    }

    private List<String> quotedKeyColumns() {
        return lookupTable.getKeyColumns().stream().map(this::quoteIdentifier).toList();
    }

    private List<String> buildSelectColumns(@Nullable List<String> resultColumns) {
        if (resultColumns == null || resultColumns.isEmpty()) {
            return List.of("*");
        }
        resultColumns.forEach(this::validateIdentifier);
        return resultColumns.stream().map(this::quoteIdentifier).toList();
    }

    private String buildWhereClause(@Nullable Map<String, Set<String>> matchingColumnValues) {
        if (matchingColumnValues == null || matchingColumnValues.isEmpty()) {
            return "";
        }

        List<String> clauses = matchingColumnValues.entrySet().stream()
                .map(entry -> {
                    validateIdentifier(entry.getKey());
                    String patterns = entry.getValue().stream()
                            .map(v -> "'" + v.replace("'", "''") + "'")
                            .collect(Collectors.joining(", "));
                    return "t.%s LIKE ANY (ARRAY[%s])".formatted(quoteIdentifier(entry.getKey()), patterns);
                })
                .toList();

        return "WHERE " + String.join(" AND ", clauses);
    }

    private String buildOrderByClause(@Nullable String sortColumn, @Nullable SortDirection sortDirection) {
        if (sortColumn == null) {
            return "";
        }
        validateIdentifier(sortColumn);
        sortColumn = quoteIdentifier(sortColumn);
        String direction = sortDirection != null ? sortDirection.name() : "ASC";
        return "ORDER BY t.%s %s".formatted(sortColumn, direction);
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }
        if (!identifier.matches("^[a-zA-Z_]\\w*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
    }

    private String quoteIdentifier(String identifier) {
        // PostgreSQL identifier quoting - doubles any internal quotes
        identifier = identifier.toLowerCase();
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
