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
package org.deltafi.actionkit.lookup;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import lombok.RequiredArgsConstructor;
import org.deltafi.actionkit.generated.client.*;
import org.deltafi.actionkit.generated.types.*;
import org.deltafi.common.graphql.dgs.GraphQLExecutor;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.lookup.LookupTable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.http.HttpResponse;
import java.util.*;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public class LookupTableClient {
    private final String lookupUrl;
    private final GraphQLClient graphQLClient;
    private final HttpService httpService;

    /**
     * Gets the lookup tables.
     *
     * @return the list of lookup tables (may be empty)
     * @throws Exception if the GraphQL query fails
     */
    public List<LookupTable> getLookupTables() throws Exception {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new GetLookupTablesGraphQLQuery.Builder()
                        .build(),
                new GetLookupTablesProjectionRoot<>()
                        .name()
                        .columns()
                        .keyColumns()
                        .serviceBacked()
                        .backingServiceActive()
                        .pullThrough()
                        .refreshDuration()
                        .lastRefresh()
        );

        GraphQLResponse response = GraphQLExecutor.executeQuery(graphQLClient, graphQLQueryRequest);

        return response.extractValueAsObject(graphQLQueryRequest.getQuery().getOperationName(), new TypeRef<>() {});
    }

    /**
     * Creates a lookup table.
     *
     * @param lookupTable the lookup table definition
     * @return the Result of the GraphQL mutation
     * @throws Exception if the GraphQL mutation fails
     */
    public Result createLookupTable(LookupTable lookupTable) throws Exception {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new CreateLookupTableGraphQLQuery.Builder()
                        .lookupTableInput(LookupTableInput.newBuilder()
                                .name(lookupTable.getName())
                                .columns(lookupTable.getColumns())
                                .keyColumns(lookupTable.getKeyColumns())
                                .serviceBacked(lookupTable.isServiceBacked())
                                .backingServiceActive(lookupTable.isBackingServiceActive())
                                .pullThrough(lookupTable.isPullThrough())
                                .refreshDuration(lookupTable.getRefreshDuration())
                                .lastRefresh(lookupTable.getLastRefresh())
                                .build())
                        .build(),
                new CreateLookupTableProjectionRoot<>()
                        .info()
                        .errors()
                        .success()
        );

        GraphQLResponse response = GraphQLExecutor.executeQuery(graphQLClient, graphQLQueryRequest);

        return response.extractValueAsObject(graphQLQueryRequest.getQuery().getOperationName(), Result.class);
    }

    private static final int BATCH_UPSERT_SIZE = 10000;

    /**
     * Upserts (inserts or updates) rows to a lookup table.
     *
     * @param lookupTableName the lookup table name
     * @param rows the rows to upsert, each being a Map of column/value pairs
     * @return the Result of the GraphQL mutation
     * @throws Exception if the GraphQL mutation fails
     */
    public Result upsertRows(String lookupTableName, List<Map<String, String>> rows) throws Exception {
        int batchStartRow = 0;
        while (batchStartRow < rows.size()) {
            int batchSize = Integer.min(rows.size() - batchStartRow, BATCH_UPSERT_SIZE);
            Result result = batchUpsertRows(lookupTableName, rows.subList(batchStartRow, batchStartRow + batchSize));
            if (!result.getSuccess()) {
                return result;
            }
            batchStartRow += batchSize;
        }
        return Result.newBuilder().success(true).build();
    }

    private Result batchUpsertRows(String lookupTableName, List<Map<String, String>> rows) throws Exception {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new UpsertLookupTableRowsGraphQLQuery.Builder()
                        .lookupTableName(lookupTableName)
                        .rows(toGraphQl(rows))
                        .build(),
                new UpsertLookupTableRowsProjectionRoot<>()
                        .info()
                        .errors()
                        .success()
        );

        GraphQLResponse response = GraphQLExecutor.executeQuery(graphQLClient, graphQLQueryRequest);

        return response.extractValueAsObject(graphQLQueryRequest.getQuery().getOperationName(), Result.class);
    }

    private List<List<ColumnValueInput>> toGraphQl(List<Map<String, String>> rows) {
        return rows.stream()
                .map(row -> row.entrySet().stream()
                        .map(mapEntry -> new ColumnValueInput(mapEntry.getKey(), mapEntry.getValue()))
                        .toList())
                .toList();
    }

    /**
     * Removes rows from a lookup table.
     *
     * @param lookupTableName the lookup table name
     * @param rows the rows to remove, each being a Map of key column/value pairs
     * @return the Result of the GraphQL mutation
     * @throws Exception if the GraphQL mutation fails
     */
    public Result removeRows(String lookupTableName, List<Map<String, String>> rows) throws Exception {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new RemoveLookupTableRowsGraphQLQuery.Builder()
                        .lookupTableName(lookupTableName)
                        .rows(toGraphQl(rows))
                        .build(),
                new RemoveLookupTableRowsProjectionRoot<>()
                        .info()
                        .errors()
                        .success()
        );

        GraphQLResponse response = GraphQLExecutor.executeQuery(graphQLClient, graphQLQueryRequest);

        return response.extractValueAsObject(graphQLQueryRequest.getQuery().getOperationName(), Result.class);
    }

    /**
     * Queries a lookup table.
     *
     * @param lookupTableName the lookup table name
     * @param lookupOptions the lookup options to use for the query
     * @return a List of Maps matching the query, with each Map representing the column/value pairs for a row
     * @throws Exception if the GraphQL query fails
     */
    @Cacheable("lookup-table-client-cache")
    public LookupResults lookup(String lookupTableName, LookupOptions lookupOptions) throws Exception {
        LookupGraphQLQuery.Builder builder = new LookupGraphQLQuery.Builder()
                .lookupTableName(lookupTableName);

        if (lookupOptions.matchingColumnValues() != null) {
            builder.matchingColumnValues(toGraphQl(lookupOptions.matchingColumnValues()));
        }

        if (lookupOptions.resultColumns() != null) {
            builder.resultColumns(new ArrayList<>(lookupOptions.resultColumns()));
        }

        if (lookupOptions.sortColumn() != null) {
            builder.sortColumn(lookupOptions.sortColumn());
        }

        if (lookupOptions.sortDirection() != null) {
            builder.sortDirection(lookupOptions.sortDirection());
        }

        if (lookupOptions.offset() != null) {
            builder.offset(lookupOptions.offset());
        }

        if (lookupOptions.limit() != null) {
            builder.limit(lookupOptions.limit());
        }

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(builder.build(),
                new LookupProjectionRoot<>()
                        .offset()
                        .count()
                        .totalCount()
                        .rows()
                        .column()
                        .value()
        );

        GraphQLResponse response = GraphQLExecutor.executeQuery(graphQLClient, graphQLQueryRequest);

        org.deltafi.actionkit.generated.types.LookupResults lookupResults = response.extractValueAsObject(
                graphQLQueryRequest.getQuery().getOperationName(),
                org.deltafi.actionkit.generated.types.LookupResults.class);

        return new LookupResults(lookupResults.getTotalCount(),
                lookupResults.getRows().stream()
                        .map(row -> {
                            Map<String, String> map = new HashMap<>();
                            row.forEach(columnValue -> map.put(columnValue.getColumn(), columnValue.getValue()));
                            return map;
                        })
                        .toList());
    }

    private List<MatchingColumnValueInput> toGraphQl(Map<String, Set<String>> matchingColumnValues) {
        return matchingColumnValues.entrySet().stream()
                .map(entry -> new MatchingColumnValueInput(entry.getKey(), new ArrayList<>(entry.getValue())))
                .toList();
    }

    @CacheEvict(value = "lookup-table-client-cache", allEntries = true)
    public void clearCache() {}

    /**
     * Sets the state of the backing service for the LookupTableSupplier for the named lookup table.
     *
     * @param lookupTableName the name of the lookup table
     * @param active true to mark the backing service active or false to make it inactive
     * @return the Result of the GraphQL mutation
     * @throws Exception if the GraphQL mutation fails
     */
    public Result setBackingServiceActive(String lookupTableName, boolean active) throws Exception {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new SetLookupTableBackingServiceActiveGraphQLQuery.Builder()
                        .lookupTableName(lookupTableName)
                        .active(active)
                        .build(),
                new SetLookupTableBackingServiceActiveProjectionRoot<>()
                        .info()
                        .errors()
                        .success()
        );

        GraphQLResponse response = GraphQLExecutor.executeQuery(graphQLClient, graphQLQueryRequest);

        return response.extractValueAsObject(graphQLQueryRequest.getQuery().getOperationName(), Result.class);
    }

    @RequiredArgsConstructor
    public enum UploadFileType {
        JSON(MediaType.APPLICATION_JSON),
        CSV("text/csv");

        private final String mediaType;
    }

    /**
     * Uploads table data to a named lookup table. This will REPLACE existing table data with just the data contained in
     * the provided fileContents.
     *
     * <p>
     * If the fileType is JSON, the file contents should contain a valid JSON array of maps, each containing key/value
     * pairs for columns in a table row.
     * </p>
     * <p>
     * If the fileType is CSV, the file contents should be valid CSV, with the first row containing column names.
     * </p>
     *
     * @param lookupTableName the name of the lookup table
     * @param fileType the type of file to upload
     * @param fileContents the contents of the file to upload
     * @throws IOException if the table cannot be uploaded to DeltaFi Core
     */
    public void uploadTable(String lookupTableName, UploadFileType fileType, String fileContents) throws IOException {
        HttpResponse<InputStream> response = httpService.post(lookupUrl + "/" + lookupTableName,
                Map.of("X-User-Permissions", "Admin", "X-User-Name", "deltafi-cli"),
                new ByteArrayInputStream(fileContents.getBytes()), fileType.mediaType);
        Response.Status status = Response.Status.fromStatusCode(response.statusCode());
        if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
            try (InputStream body = response.body()) {
                throw new IOException(String.format("Unable to upload table %s: Server returned status code %s: %s",
                        lookupTableName, response.statusCode(), new String(body.readAllBytes())));
            }
        }
    }
}
