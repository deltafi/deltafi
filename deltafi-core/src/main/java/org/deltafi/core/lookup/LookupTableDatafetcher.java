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
import com.netflix.graphql.types.errors.ErrorType;
import graphql.GraphQLError;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.types.SortDirection;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.types.Result;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.*;
import java.util.stream.Collectors;

@DgsComponent
@ControllerAdvice
public class LookupTableDatafetcher {
    private final LookupTableService lookupTableService;

    public LookupTableDatafetcher(@Nullable LookupTableService lookupTableService) {
        this.lookupTableService = lookupTableService;
    }

    @DgsQuery
    @NeedsPermission.LookupTableRead
    @SuppressWarnings("ConstantConditions")
    public List<LookupTable> getLookupTables() {
        verifyEnabled();
        return lookupTableService.getLookupTables();
    }

    private void verifyEnabled() {
        if (lookupTableService == null) {
            throw new UnsupportedOperationException("Lookup table service disabled");
        }
    }

    @DgsQuery
    @NeedsPermission.LookupTableRead
    @SuppressWarnings("ConstantConditions")
    public LookupTable getLookupTable(@InputArgument String lookupTableName) {
        verifyEnabled();
        return lookupTableService.getLookupTable(lookupTableName);
    }

    @DgsMutation
    @NeedsPermission.LookupTableCreate
    @SuppressWarnings("ConstantConditions")
    public Result createLookupTable(@InputArgument LookupTable lookupTableInput) {
        verifyEnabled();
        try {
            lookupTableService.createLookupTable(lookupTableInput, true);
            return new Result(true, null, null);
        } catch (LookupTableServiceException e) {
            return new Result(false, null, e.getErrors());
        }
    }

    @DgsMutation
    @NeedsPermission.LookupTableDelete
    @SuppressWarnings("ConstantConditions")
    public Result deleteLookupTable(@InputArgument String lookupTableName) throws LookupTableServiceException {
        verifyEnabled();
        lookupTableService.deleteLookupTable(lookupTableName);
        return new Result(true, null, null);
    }

    @DgsMutation
    @NeedsPermission.LookupTableUpdate
    @SuppressWarnings("ConstantConditions")
    public Result upsertLookupTableRows(@InputArgument String lookupTableName,
            @InputArgument List<List<ColumnValueInput>> rows) throws LookupTableServiceException {
        verifyEnabled();
        lookupTableService.upsertRows(lookupTableName, fromGraphQl(rows));
        return new Result(true, null, null);
    }

    private List<Map<String, String>> fromGraphQl(List<List<ColumnValueInput>> rows) {
        return rows.stream()
                .map(row -> row.stream()
                        .collect(Collectors.toMap(ColumnValueInput::getColumn, ColumnValueInput::getValue)))
                .toList();
    }

    @DgsMutation
    @NeedsPermission.LookupTableUpdate
    @SuppressWarnings("ConstantConditions")
    public Result removeLookupTableRows(@InputArgument String lookupTableName,
            @InputArgument List<List<ColumnValueInput>> rows) throws LookupTableServiceException {
        verifyEnabled();
        lookupTableService.removeRows(lookupTableName, fromGraphQl(rows));
        return new Result(true, null, null);
    }

    @DgsQuery
    @NeedsPermission.LookupTableRead
    @SuppressWarnings("ConstantConditions")
    public LookupResults lookup(@InputArgument String lookupTableName,
            @InputArgument List<MatchingColumnValueInput> matchingColumnValues,
            @InputArgument List<String> resultColumns,
            @InputArgument String sortColumn, @InputArgument SortDirection sortDirection,
            @InputArgument Integer offset, @InputArgument Integer limit) throws LookupTableServiceException {
        verifyEnabled();
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
    @SuppressWarnings("ConstantConditions")
    public Result setLookupTableBackingServiceActive(@InputArgument String lookupTableName,
            @InputArgument Boolean active) throws LookupTableServiceException {
        verifyEnabled();
        lookupTableService.setBackingServiceActive(lookupTableName, active);
        return new Result(true, null, null);
    }

    @GraphQlExceptionHandler
    public GraphQLError handleLookupTableException(LookupTableServiceException e) {
        return GraphQLError.newError().errorType(ErrorType.INTERNAL).message(e.getMessage()).build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleUnsupportedOperationException(UnsupportedOperationException ignored) {
        return GraphQLError.newError().errorType(ErrorType.UNAVAILABLE).message("Lookup table service disabled").build();
    }
}
