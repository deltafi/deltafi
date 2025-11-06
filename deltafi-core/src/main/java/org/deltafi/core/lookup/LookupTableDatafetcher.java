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

import com.netflix.graphql.dgs.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.types.SortDirection;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.types.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@ConditionalOnProperty("lookup.enabled")
@DgsComponent
@RequiredArgsConstructor
public class LookupTableDatafetcher {
    private final LookupTableService lookupTableService;

    @DgsQuery
    @NeedsPermission.LookupTableRead
    public List<LookupTable> getLookupTables() {
        return lookupTableService.getLookupTables();
    }

    @DgsQuery
    @NeedsPermission.LookupTableRead
    public LookupTable getLookupTable(@InputArgument String lookupTableName) {
        return lookupTableService.getLookupTable(lookupTableName);
    }

    @DgsMutation
    @NeedsPermission.LookupTableCreate
    public Result createLookupTable(@InputArgument LookupTable lookupTableInput) {
        try {
            lookupTableService.createLookupTable(lookupTableInput, true);
            return new Result(true, null, null);
        } catch (LookupTableServiceException e) {
            return new Result(false, null, e.getErrors());
        }
    }

    @DgsMutation
    @NeedsPermission.LookupTableDelete
    public Result deleteLookupTable(@InputArgument String lookupTableName) {
        try {
            lookupTableService.deleteLookupTable(lookupTableName);
            return new Result(true, null, null);
        } catch (LookupTableServiceException e) {
            return new Result(false, null, List.of(e.getMessage()));
        }
    }

    @DgsMutation
    @NeedsPermission.LookupTableUpdate
    public Result upsertLookupTableRows(@InputArgument String lookupTableName,
            @InputArgument List<List<ColumnValueInput>> rows) {
        try {
            lookupTableService.upsertRows(lookupTableName, fromGraphQl(rows));
            return new Result(true, null, null);
        } catch (LookupTableServiceException e) {
            return new Result(false, null, List.of(e.getMessage()));
        }
    }

    private List<Map<String, String>> fromGraphQl(List<List<ColumnValueInput>> rows) {
        return rows.stream()
                .map(row -> row.stream()
                        .collect(Collectors.toMap(ColumnValueInput::getColumn, ColumnValueInput::getValue)))
                .toList();
    }

    @DgsMutation
    @NeedsPermission.LookupTableUpdate
    public Result removeLookupTableRows(@InputArgument String lookupTableName,
            @InputArgument List<List<ColumnValueInput>> rows) {
        try {
            lookupTableService.removeRows(lookupTableName, fromGraphQl(rows));
            return new Result(true, null, null);
        } catch (LookupTableServiceException e) {
            return new Result(false, null, List.of(e.getMessage()));
        }
    }

    @DgsQuery
    @NeedsPermission.LookupTableRead
    public LookupResults lookup(@InputArgument String lookupTableName,
            @InputArgument List<MatchingColumnValueInput> matchingColumnValues,
            @InputArgument List<String> resultColumns,
            @InputArgument String sortColumn, @InputArgument SortDirection sortDirection,
            @InputArgument Integer offset, @InputArgument Integer limit)
            throws LookupTableServiceException {
        Pair<Integer, List<Map<String, String>>> results = lookupTableService.lookup(lookupTableName,
                fromGraphQlMatching(matchingColumnValues), resultColumns, sortColumn, sortDirection, offset, limit);
        return LookupResults.newBuilder()
                .rows(toGraphQl(results.getRight()))
                .totalCount(results.getLeft())
                .offset(offset)
                .count(limit)
                .build();
    }

    @Nullable
    private Map<String, Set<String>> fromGraphQlMatching(@Nullable List<MatchingColumnValueInput> matchingColumnValueInputs) {
        return matchingColumnValueInputs == null ? null : matchingColumnValueInputs.stream()
                .collect(Collectors.toMap(MatchingColumnValueInput::getColumn,
                        columnValueInput -> new HashSet<>(columnValueInput.getValue())));
    }

    private List<List<ColumnValue>> toGraphQl(List<Map<String, String>> rowsMap) {
        return rowsMap.stream()
                .map(rowMap -> {
                    List<ColumnValue> columnValues = new ArrayList<>();
                    rowMap.forEach((key, value) -> columnValues.add(new ColumnValue(key, value)));
                    return columnValues;
                })
                .toList();
    }

    @DgsMutation
    @NeedsPermission.Admin
    public Result setLookupTableBackingServiceActive(@InputArgument String lookupTableName,
            @InputArgument Boolean active) {
        try {
            lookupTableService.setBackingServiceActive(lookupTableName, active);
            return new Result(true, null, null);
        } catch (LookupTableServiceException e) {
            return new Result(false, null, List.of(e.getMessage()));
        }
    }
}
