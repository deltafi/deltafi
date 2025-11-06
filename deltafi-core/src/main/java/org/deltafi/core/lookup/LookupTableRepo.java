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

import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.types.SortDirection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class LookupTableRepo {
    @Getter
    private final LookupTable lookupTable;
    private final JdbcClient jdbcClient;

    public LookupTableRepo(LookupTable lookupTable, @Qualifier("lookup") JdbcClient lookupJdbcClient) {
        this.lookupTable = lookupTable;
        this.jdbcClient = lookupJdbcClient;
    }

    public int create() {
        String createTable = "CREATE TABLE " + lookupTable.getName() + " (" +
                String.join(", ", lookupTable.getColumns().stream().map(column -> column + " TEXT").toList()) +
                ", last_updated timestamp(6) with time zone" +
                ", PRIMARY KEY (" + String.join(", ", lookupTable.getKeyColumns()) + ")" +
                ");";

        return jdbcClient.sql(createTable).update();
    }

    public int upsert(Map<String, String> rowMap, OffsetDateTime lastUpdated) {
        List<String> nonKeyColumns = new ArrayList<>(lookupTable.getColumns());
        nonKeyColumns.removeAll(lookupTable.getKeyColumns());
        String insert = "INSERT INTO " + lookupTable.getName() + " (" + String.join(", ", lookupTable.getColumns()) +
                ", last_updated" +
                ")" +
                " VALUES (" +
                String.join(", ", Collections.nCopies(lookupTable.getColumns().size(), "?")) +
                ", ?" +
                ")" +
                " ON CONFLICT (" + String.join(", ", lookupTable.getKeyColumns()) + ")" +
                " DO UPDATE SET " +
                (nonKeyColumns.isEmpty() ? "" : (String.join(", ", nonKeyColumns.stream().map(column -> column + " = EXCLUDED." + column).toList()) + ", ")) +
                "last_updated = EXCLUDED.last_updated" +
                ";";
        return jdbcClient.sql(insert)
                .params(lookupTable.getColumns().stream().map(rowMap::get).toList())
                .param(lastUpdated)
                .update();
    }

    public int delete(Map<String, String> rowMap) {
        List<String> matchClauses = rowMap.entrySet().stream()
                .map(entry -> entry.getKey() + " = '" + entry.getValue() + "'")
                .toList();
        String delete = "DELETE FROM " + lookupTable.getName() + " WHERE " + String.join(" AND ", matchClauses) + ";";
        return jdbcClient.sql(delete)
                .update();
    }

    public void deleteOlder(OffsetDateTime lastUpdated) {
        jdbcClient.sql("DELETE FROM " + lookupTable.getName() + " WHERE last_updated < ?")
                .param(lastUpdated)
                .update();
    }

    public Pair<Integer, List<Map<String, String>>> find(@Nullable Map<String, Set<String>> matchingColumnValues,
            @Nullable List<String> resultColumns, @Nullable String sortColumn, @Nullable SortDirection sortDirection,
            @Nullable Integer offset, @Nullable Integer limit) {
        StringBuilder sql = new StringBuilder();

        sql.append("SELECT ");

        sql.append(((resultColumns == null) || resultColumns.isEmpty()) ? "*" :
                (String.join(", ", resultColumns)) + ", last_updated");

        sql.append(", COUNT(*) OVER() AS total_matches");

        sql.append(" FROM ").append(lookupTable.getName());

        if (matchingColumnValues != null) {
            List<String> matchClauses = matchingColumnValues.entrySet().stream()
                    .map(entry -> String.format("%s LIKE ANY ('{%s}')", entry.getKey(),
                            String.join(", ", entry.getValue())))
                    .toList();
            sql.append(" WHERE ").append(String.join(" AND ", matchClauses));
        }

        if (sortColumn != null) {
            sql.append(" ORDER BY ").append(sortColumn).append(" ");
            sql.append(sortDirection != null ? sortDirection : SortDirection.ASC);
        }

        if (offset != null) {
            sql.append(" OFFSET ").append(offset);
        }

        if (limit != null) {
            sql.append(" LIMIT ").append(limit);
        }

        sql.append(";");

        List<Pair<Integer, Map<String, String>>> rows = jdbcClient.sql(sql.toString())
                .query((resultSet, rowNum) -> mapRow(resultColumns, resultSet))
                .list();

        return Pair.of(rows.isEmpty() ? 0 : rows.getFirst().getLeft(), rows.stream().map(Pair::getRight).toList());
    }

    private Pair<Integer, Map<String, String>> mapRow(List<String> columns, ResultSet resultSet) {
        Map<String, String> rowMap = new HashMap<>();
        if (columns == null) {
            columns = lookupTable.getColumns();
        }
        for (String column : columns) {
            try {
                rowMap.put(column, resultSet.getString(column));
            } catch (SQLException e) {
                throw new RuntimeException("Unable to map column", e);
            }
        }
        try {
            rowMap.put("last_updated",
                    resultSet.getTimestamp("last_updated").toLocalDateTime().atOffset(ZoneOffset.UTC).toString());
            return Pair.of(resultSet.getInt("total_matches"), rowMap);
        } catch (SQLException e) {
            return Pair.of(-1, rowMap);
        }
    }

    public int count() {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + lookupTable.getName() + ";").query(Integer.class).single();
    }
}
