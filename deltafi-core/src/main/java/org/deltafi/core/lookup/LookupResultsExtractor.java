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

import lombok.RequiredArgsConstructor;
import org.deltafi.core.generated.types.ColumnValue;
import org.deltafi.core.generated.types.LookupResults;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public class LookupResultsExtractor implements ResultSetExtractor<LookupResults> {
    private static final Set<String> META_COLUMNS = Set.of("table_created", "table_modified", "total_matches");

    private final List<String> resultColumns;
    private final int offset;

    @Override
    public LookupResults extractData(ResultSet rs) throws SQLException, DataAccessException {
        List<List<ColumnValue>> rows = new ArrayList<>();
        int totalMatches = 0;
        OffsetDateTime created = null;
        OffsetDateTime modified = null;

        while (rs.next()) {
            if (rows.isEmpty()) {
                totalMatches = rs.getInt("total_matches");
                created = rs.getObject("table_created", OffsetDateTime.class);
                modified = rs.getObject("table_modified", OffsetDateTime.class);
            }
            rows.add(mapRowToColumnValues(rs));
        }

        return LookupResults.newBuilder()
                .offset(offset)
                .count(rows.size())
                .totalCount(totalMatches)
                .created(created)
                .modified(modified)
                .rows(rows).build();
    }

    private List<ColumnValue> mapRowToColumnValues(ResultSet rs) throws SQLException {
        List<ColumnValue> columnValues = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            if (includeColumn(columnName)) {
                String value = rs.getString(i);
                if (value != null) {
                    columnValues.add(new ColumnValue(columnName, value));
                }
            }
        }

        return columnValues;
    }

    private boolean includeColumn(String columnName) {
        return !META_COLUMNS.contains(columnName) && inResultColumns(columnName);
    }

    private boolean inResultColumns(String columnName) {
        return resultColumns == null || resultColumns.isEmpty() || resultColumns.contains(columnName);
    }
}