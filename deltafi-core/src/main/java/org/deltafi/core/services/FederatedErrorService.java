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
package org.deltafi.core.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.DeltaFilesFilter;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.ErrorSummaryFilter;
import org.deltafi.core.types.SummaryByFlow;
import org.deltafi.core.types.SummaryByFlowAndMessage;
import org.deltafi.core.types.leader.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * ABOUTME: Executes federated error operations across all member systems.
 * ABOUTME: Provides search and bulk action capabilities for fleet-wide error management.
 */
@Service
@Slf4j
public class FederatedErrorService {
    private static final int MAX_CONCURRENT_REQUESTS = 50;
    private static final int SUMMARY_QUERY_TIMEOUT_MS = 30000; // 30 seconds for summary queries
    private static final int ACTION_TIMEOUT_MS = 300000; // 5 minutes for bulk action operations

    private final ExecutorService executor;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final DeltaFilesService deltaFilesService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public FederatedErrorService(DeltaFiPropertiesService deltaFiPropertiesService,
                                  DeltaFilesService deltaFilesService,
                                  ObjectMapper objectMapper,
                                  HttpClient httpClient) {
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.deltaFilesService = deltaFilesService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute a federated error search across all configured members.
     */
    public FederatedErrorResponse searchErrors(FederatedErrorFilter filter) {
        List<FederatedErrorResult> results = Collections.synchronizedList(new ArrayList<>());

        // Query leader locally (if not filtered out)
        if (shouldIncludeLeader(filter)) {
            try {
                DeltaFilesFilter gqlFilter = toGraphQLFilter(filter);
                DeltaFiles leaderResults = deltaFilesService.deltaFiles(0, 0, gqlFilter, null, null);

                results.add(new FederatedErrorResult(
                    "Leader",
                    "local",
                    List.of("leader"),
                    (long) leaderResults.getTotalCount(),
                    ConnectionState.CONNECTED,
                    null
                ));
            } catch (Exception e) {
                log.error("Failed to query leader for errors: {}", e.getMessage(), e);
                results.add(new FederatedErrorResult(
                    "Leader",
                    "local",
                    List.of("leader"),
                    null,
                    ConnectionState.UNREACHABLE,
                    e.getMessage()
                ));
            }
        }

        // Query members in parallel (filtered by name/tag)
        List<MemberConfig> configs = filterMemberConfigs(
            deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs(), filter);
        int timeoutMs = deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout();

        List<CompletableFuture<Void>> futures = configs.stream()
            .map(config -> CompletableFuture
                .supplyAsync(() -> queryMemberErrors(config, filter), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> handleMemberError(config, ex))
                .thenAccept(results::add))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return buildErrorResponse(results);
    }

    /**
     * Execute an acknowledge action across fleet members.
     */
    public FederatedActionResponse acknowledgeErrors(FederatedActionRequest request) {
        return executeAction(request, "acknowledge");
    }

    /**
     * Execute a replay action across fleet members.
     */
    public FederatedActionResponse replayErrors(FederatedActionRequest request) {
        return executeAction(request, "replay");
    }

    /**
     * Execute a resume action across fleet members.
     */
    public FederatedActionResponse resumeErrors(FederatedActionRequest request) {
        return executeAction(request, "resume");
    }

    /**
     * Execute a replay-and-acknowledge action across fleet members.
     */
    public FederatedActionResponse replayAndAcknowledgeErrors(FederatedActionRequest request) {
        return executeAction(request, "replayAndAcknowledge");
    }

    /**
     * Get error summary by data source across all fleet members.
     */
    public FederatedErrorSummaryResponse errorSummaryByDataSource(FederatedErrorFilter filter) {
        return fetchErrorSummary(filter, "errorSummaryByFlow");
    }

    /**
     * Get error summary by error message across all fleet members.
     */
    public FederatedErrorSummaryResponse errorSummaryByMessage(FederatedErrorFilter filter) {
        return fetchErrorSummary(filter, "errorSummaryByMessage");
    }

    private FederatedErrorSummaryResponse fetchErrorSummary(FederatedErrorFilter filter, String queryType) {
        Map<String, FederatedErrorSummary> aggregatedSummaries = new ConcurrentHashMap<>();
        List<FederatedErrorResult> memberResults = Collections.synchronizedList(new ArrayList<>());

        // Query leader locally (if not filtered out)
        if (shouldIncludeLeader(filter)) {
            try {
                List<FederatedErrorSummary> leaderSummaries = queryLocalSummary(filter, queryType);
                for (FederatedErrorSummary summary : leaderSummaries) {
                    aggregatedSummaries.merge(summary.key(), summary, this::mergeSummaries);
                }
                long leaderCount = leaderSummaries.stream().mapToLong(FederatedErrorSummary::count).sum();
                memberResults.add(new FederatedErrorResult("Leader", "local", List.of("leader"),
                    leaderCount, ConnectionState.CONNECTED, null));
            } catch (Exception e) {
                log.error("Failed to query leader for error summary: {}", e.getMessage(), e);
                memberResults.add(new FederatedErrorResult("Leader", "local", List.of("leader"),
                    null, ConnectionState.UNREACHABLE, e.getMessage()));
            }
        }

        // Query members in parallel (filtered by name/tag)
        List<MemberConfig> configs = filterMemberConfigs(
            deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs(), filter);

        List<CompletableFuture<Void>> futures = configs.stream()
            .map(config -> CompletableFuture
                .supplyAsync(() -> queryMemberSummary(config, filter, queryType), executor)
                .orTimeout(SUMMARY_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> new MemberSummaryResult(config, List.of(), formatTimeoutError(ex)))
                .thenAccept(result -> {
                    if (result.error() == null) {
                        for (FederatedErrorSummary summary : result.summaries()) {
                            aggregatedSummaries.merge(summary.key(), summary, this::mergeSummaries);
                        }
                        long memberCount = result.summaries().stream().mapToLong(FederatedErrorSummary::count).sum();
                        memberResults.add(new FederatedErrorResult(config.name(), config.url(), config.tags(),
                            memberCount, ConnectionState.CONNECTED, null));
                    } else {
                        memberResults.add(new FederatedErrorResult(config.name(), config.url(), config.tags(),
                            null, ConnectionState.UNREACHABLE, result.error()));
                    }
                }))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<FederatedErrorSummary> sortedSummaries = aggregatedSummaries.values().stream()
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();

        long totalCount = sortedSummaries.stream().mapToLong(FederatedErrorSummary::count).sum();
        int membersSearched = (int) memberResults.stream().filter(r -> r.status() == ConnectionState.CONNECTED).count();
        int membersFailed = (int) memberResults.stream().filter(r -> r.status() != ConnectionState.CONNECTED).count();

        return new FederatedErrorSummaryResponse(sortedSummaries, totalCount, membersSearched, membersFailed, OffsetDateTime.now());
    }

    private record MemberSummaryResult(MemberConfig config, List<FederatedErrorSummary> summaries, String error) {}

    private FederatedErrorSummary mergeSummaries(FederatedErrorSummary a, FederatedErrorSummary b) {
        return new FederatedErrorSummary(
            a.key(),
            a.flowType(),
            a.count() + b.count(),
            a.nodeCount() + b.nodeCount()
        );
    }

    private List<FederatedErrorSummary> queryLocalSummary(FederatedErrorFilter filter, String queryType) {
        ErrorSummaryFilter summaryFilter = toErrorSummaryFilter(filter);

        if ("errorSummaryByFlow".equals(queryType)) {
            SummaryByFlow summary = deltaFilesService.getErrorSummaryByFlow(0, 10000, summaryFilter, null, null);
            return summary.countPerFlow().stream()
                .map(cpf -> new FederatedErrorSummary(cpf.getFlow(), cpf.getType().name(), cpf.getCount(), 1))
                .toList();
        } else {
            SummaryByFlowAndMessage summary = deltaFilesService.getErrorSummaryByMessage(0, 10000, summaryFilter, null, null);
            return summary.countPerMessage().stream()
                .map(cpm -> new FederatedErrorSummary(cpm.getMessage(), null, cpm.getCount(), 1))
                .toList();
        }
    }

    private ErrorSummaryFilter toErrorSummaryFilter(FederatedErrorFilter filter) {
        ErrorSummaryFilter summaryFilter = new ErrorSummaryFilter();
        if (filter != null) {
            summaryFilter.setErrorAcknowledged(filter.getErrorAcknowledged());
            summaryFilter.setErrorCause(filter.getErrorCause());
            if (filter.getDataSources() != null && filter.getDataSources().size() == 1) {
                summaryFilter.setFlow(filter.getDataSources().get(0));
            }
        }
        return summaryFilter;
    }

    private MemberSummaryResult queryMemberSummary(MemberConfig config, FederatedErrorFilter filter, String queryType) {
        try {
            String query = buildSummaryQuery(filter, queryType);
            List<FederatedErrorSummary> summaries = executeSummaryQuery(config, query, queryType);
            return new MemberSummaryResult(config, summaries, null);
        } catch (Exception e) {
            log.error("Failed to query member {} for error summary: {}", config.name(), e.getMessage());
            return new MemberSummaryResult(config, List.of(), formatException(e));
        }
    }

    private String buildSummaryQuery(FederatedErrorFilter filter, String queryType) {
        ErrorSummaryFilter summaryFilter = toErrorSummaryFilter(filter);

        if ("errorSummaryByFlow".equals(queryType)) {
            return new GraphQLQueryRequest(
                    ErrorSummaryByFlowGraphQLQuery.newRequest()
                            .offset(0)
                            .limit(10000)
                            .filter(summaryFilter)
                            .build(),
                    new ErrorSummaryByFlowProjectionRoot<>()
                            .countPerFlow().flow().count().type()
            ).serialize();
        } else {
            return new GraphQLQueryRequest(
                    ErrorSummaryByMessageGraphQLQuery.newRequest()
                            .offset(0)
                            .limit(10000)
                            .filter(summaryFilter)
                            .build(),
                    new ErrorSummaryByMessageProjectionRoot<>()
                            .countPerMessage().message().count().getRoot()
            ).serialize();
        }
    }

    private List<FederatedErrorSummary> executeSummaryQuery(MemberConfig config, String query, String queryType) throws Exception {
        String url = config.url() + "/api/v2/graphql";
        String body = objectMapper.writeValueAsString(Map.of("query", query));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(SUMMARY_QUERY_TIMEOUT_MS))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");

        addAuthHeader(config, builder);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());
        List<FederatedErrorSummary> summaries = new ArrayList<>();

        if ("errorSummaryByFlow".equals(queryType)) {
            JsonNode countPerFlow = jsonNode.path("data").path("errorSummaryByFlow").path("countPerFlow");
            if (countPerFlow.isArray()) {
                for (JsonNode item : countPerFlow) {
                    summaries.add(new FederatedErrorSummary(
                        item.path("flow").asText(),
                        item.path("type").asText(null),
                        item.path("count").asLong(0),
                        1
                    ));
                }
            }
        } else {
            JsonNode countPerMessage = jsonNode.path("data").path("errorSummaryByMessage").path("countPerMessage");
            if (countPerMessage.isArray()) {
                for (JsonNode item : countPerMessage) {
                    summaries.add(new FederatedErrorSummary(
                        item.path("message").asText(),
                        null,
                        item.path("count").asLong(0),
                        1
                    ));
                }
            }
        }

        return summaries;
    }

    private String formatTimeoutError(Throwable ex) {
        if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
            return "Request timed out";
        }
        return ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
    }

    private FederatedErrorResult queryMemberErrors(MemberConfig config, FederatedErrorFilter filter) {
        try {
            String query = buildErrorCountQuery(filter);
            long count = executeErrorCountQuery(config, query);
            return new FederatedErrorResult(
                config.name(),
                config.url(),
                config.tags(),
                count,
                ConnectionState.CONNECTED,
                null
            );
        } catch (Exception e) {
            log.error("Failed to query member {} for errors: {}", config.name(), e.getMessage());
            return new FederatedErrorResult(
                config.name(),
                config.url(),
                config.tags(),
                null,
                ConnectionState.UNREACHABLE,
                formatException(e)
            );
        }
    }

    private FederatedErrorResult handleMemberError(MemberConfig config, Throwable ex) {
        String error = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
            error = "Request timed out";
        }
        return new FederatedErrorResult(
            config.name(),
            config.url(),
            config.tags(),
            null,
            ConnectionState.UNREACHABLE,
            error
        );
    }

    private FederatedActionResponse executeAction(FederatedActionRequest request, String actionName) {
        // If flows are specified, use by-flow operations instead of matching operations
        if (request.getFlows() != null && !request.getFlows().isEmpty()) {
            return executeByFlowAction(request, actionName);
        }

        List<FederatedActionResult> results = Collections.synchronizedList(new ArrayList<>());
        Integer limit = request.getLimit();
        List<String> memberNames = request.getMemberNames();
        boolean includeLeader = memberNames == null || memberNames.isEmpty() || memberNames.contains("Leader");

        // Execute on leader locally (only if no specific members requested or Leader is in the list)
        if (includeLeader) {
            try {
                DeltaFilesFilter gqlFilter = toGraphQLFilter(request.getFilter());
                int[] counts = executeLocalAction(actionName, gqlFilter, request.getReason(), limit);
                results.add(new FederatedActionResult(
                    "Leader",
                    counts[0],
                    counts[1],
                    ConnectionState.CONNECTED,
                    null
                ));
            } catch (Exception e) {
                log.error("Failed to execute {} on leader: {}", actionName, e.getMessage(), e);
                results.add(new FederatedActionResult(
                    "Leader",
                    0,
                    0,
                    ConnectionState.UNREACHABLE,
                    e.getMessage()
                ));
            }
        }

        // Execute on all members in parallel
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();

        // Filter to specific members if requested
        if (memberNames != null && !memberNames.isEmpty()) {
            configs = configs.stream()
                .filter(c -> memberNames.contains(c.name()))
                .toList();
        }

        // Build mutations with and without limit (fallback for members that don't support limit yet)
        String memberMutationWithLimit = buildMutation(request, actionName, true);
        String memberMutationWithoutLimit = buildMutation(request, actionName, false);

        List<CompletableFuture<Void>> futures = configs.stream()
            .map(config -> CompletableFuture
                .supplyAsync(() -> executeMemberActionWithFallback(config, memberMutationWithLimit, memberMutationWithoutLimit), executor)
                .orTimeout(ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> handleActionError(config, ex))
                .thenAccept(results::add))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return buildActionResponse(results);
    }

    private FederatedActionResponse executeByFlowAction(FederatedActionRequest request, String actionName) {
        List<FederatedActionResult> results = Collections.synchronizedList(new ArrayList<>());
        Integer limit = request.getLimit();
        List<String> memberNames = request.getMemberNames();
        boolean includeLeader = memberNames == null || memberNames.isEmpty() || memberNames.contains("Leader");

        // Extract time range from filter
        OffsetDateTime modifiedAfter = request.getFilter() != null ? request.getFilter().getModifiedAfter() : null;
        OffsetDateTime modifiedBefore = request.getFilter() != null ? request.getFilter().getModifiedBefore() : null;

        // Execute on leader locally for each flow
        if (includeLeader) {
            int totalSuccess = 0;
            int totalFailure = 0;
            try {
                for (FederatedActionRequest.FlowSelection flow : request.getFlows()) {
                    if (flow.getFlowType() == null || flow.getName() == null) {
                        log.warn("Skipping flow with null type or name: type={}, name={}", flow.getFlowType(), flow.getName());
                        continue;
                    }
                    FlowType flowType = FlowType.valueOf(flow.getFlowType());
                    int[] counts = executeLocalByFlowAction(actionName, flowType, flow.getName(), request.getReason(),
                            modifiedAfter, modifiedBefore, limit);
                    totalSuccess += counts[0];
                    totalFailure += counts[1];
                }
                results.add(new FederatedActionResult("Leader", totalSuccess, totalFailure, ConnectionState.CONNECTED, null));
            } catch (Exception e) {
                log.error("Failed to execute {} by flow on leader: {}", actionName, e.getMessage(), e);
                results.add(new FederatedActionResult("Leader", totalSuccess, totalFailure, ConnectionState.UNREACHABLE, e.getMessage()));
            }
        }

        // Execute on all members in parallel
        List<MemberConfig> configs = deltaFiPropertiesService.getDeltaFiProperties().getMemberConfigs();

        if (memberNames != null && !memberNames.isEmpty()) {
            configs = configs.stream()
                .filter(c -> memberNames.contains(c.name()))
                .toList();
        }

        // Build by-flow mutations for each flow (filter out flows with null type/name)
        List<String> byFlowMutations = request.getFlows().stream()
            .filter(flow -> flow.getFlowType() != null && flow.getName() != null)
            .map(flow -> buildByFlowMutation(actionName, flow, request.getReason(), modifiedAfter, modifiedBefore, limit))
            .toList();

        List<CompletableFuture<Void>> futures = configs.stream()
            .map(config -> CompletableFuture
                .supplyAsync(() -> executeMemberByFlowActions(config, byFlowMutations), executor)
                .orTimeout(ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> handleActionError(config, ex))
                .thenAccept(results::add))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return buildActionResponse(results);
    }

    private int[] executeLocalByFlowAction(String actionName, FlowType flowType, String flowName, String reason,
            OffsetDateTime modifiedAfter, OffsetDateTime modifiedBefore, Integer limit) {
        int success = 0;
        int failure = 0;
        int effectiveLimit = limit != null ? limit : Integer.MAX_VALUE;

        switch (actionName) {
            case "acknowledge" -> {
                var results = deltaFilesService.acknowledgeByFlow(flowType, flowName, reason, modifiedAfter, modifiedBefore, effectiveLimit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            case "resume" -> {
                var results = deltaFilesService.resumeByFlowTypeAndName(flowType, flowName, null, false, modifiedAfter, modifiedBefore, effectiveLimit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            case "replay" -> {
                var results = deltaFilesService.replayErrorsByFlow(flowType, flowName, null, null, modifiedAfter, modifiedBefore, effectiveLimit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            case "replayAndAcknowledge" -> {
                var replayResults = deltaFilesService.replayErrorsByFlow(flowType, flowName, null, null, modifiedAfter, modifiedBefore, effectiveLimit);
                for (var r : replayResults) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
                var ackResults = deltaFilesService.acknowledgeByFlow(flowType, flowName, reason, modifiedAfter, modifiedBefore, effectiveLimit);
                for (var r : ackResults) {
                    if (!r.getSuccess()) {
                        log.warn("Failed to acknowledge DeltaFile {} after replay: {}", r.getDid(), r.getError());
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown action: " + actionName);
        }

        return new int[]{success, failure};
    }

    private String buildByFlowMutation(String actionName, FederatedActionRequest.FlowSelection flow, String reason,
            OffsetDateTime modifiedAfter, OffsetDateTime modifiedBefore, Integer limit) {
        FlowType flowType = FlowType.valueOf(flow.getFlowType());
        String flowName = flow.getName();

        return switch (actionName) {
            case "acknowledge" -> new GraphQLQueryRequest(
                    AcknowledgeByFlowGraphQLQuery.newRequest()
                            .flowType(flowType)
                            .flowName(flowName)
                            .reason(reason)
                            .modifiedAfter(modifiedAfter)
                            .modifiedBefore(modifiedBefore)
                            .limit(limit)
                            .build(),
                    new AcknowledgeByFlowProjectionRoot<>().did().success().error()
            ).serialize();
            case "resume" -> new GraphQLQueryRequest(
                    ResumeByFlowGraphQLQuery.newRequest()
                            .flowType(flowType)
                            .name(flowName)
                            .modifiedAfter(modifiedAfter)
                            .modifiedBefore(modifiedBefore)
                            .limit(limit)
                            .build(),
                    new ResumeByFlowProjectionRoot<>().did().success().error()
            ).serialize();
            case "replay" -> new GraphQLQueryRequest(
                    ReplayErrorsByFlowGraphQLQuery.newRequest()
                            .flowType(flowType)
                            .flowName(flowName)
                            .modifiedAfter(modifiedAfter)
                            .modifiedBefore(modifiedBefore)
                            .limit(limit)
                            .build(),
                    new ReplayErrorsByFlowProjectionRoot<>().did().success().error()
            ).serialize();
            case "replayAndAcknowledge" -> {
                // For replayAndAcknowledge, we need to combine two mutations in one request
                String replayMutation = new GraphQLQueryRequest(
                        ReplayErrorsByFlowGraphQLQuery.newRequest()
                                .flowType(flowType)
                                .flowName(flowName)
                                .modifiedAfter(modifiedAfter)
                                .modifiedBefore(modifiedBefore)
                                .limit(limit)
                                .queryName("replay")
                                .build(),
                        new ReplayErrorsByFlowProjectionRoot<>().did().success().error()
                ).serialize();
                String ackMutation = new GraphQLQueryRequest(
                        AcknowledgeByFlowGraphQLQuery.newRequest()
                                .flowType(flowType)
                                .flowName(flowName)
                                .reason(reason)
                                .modifiedAfter(modifiedAfter)
                                .modifiedBefore(modifiedBefore)
                                .limit(limit)
                                .queryName("acknowledge")
                                .build(),
                        new AcknowledgeByFlowProjectionRoot<>().did().success().error()
                ).serialize();
                // Combine into single mutation block: extract the inner parts and merge
                yield combineMutations(replayMutation, ackMutation);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + actionName);
        };
    }

    private String combineMutations(String... serializedMutations) {
        StringBuilder combined = new StringBuilder("mutation {");
        for (String mutation : serializedMutations) {
            // Extract content between "mutation {" and the final "}"
            int start = mutation.indexOf('{') + 1;
            int end = mutation.lastIndexOf('}');
            if (start > 0 && end > start) {
                combined.append(" ").append(mutation.substring(start, end).trim());
            }
        }
        combined.append(" }");
        return combined.toString();
    }

    private FederatedActionResult executeMemberByFlowActions(MemberConfig config, List<String> mutations) {
        int totalSuccess = 0;
        int totalFailure = 0;
        int mutationsFailed = 0;
        String lastError = null;

        for (String mutation : mutations) {
            try {
                int[] counts = executeGraphQLMutation(config, mutation);
                totalSuccess += counts[0];
                totalFailure += counts[1];
            } catch (Exception e) {
                log.error("Failed to execute by-flow mutation on {}: {}", config.name(), e.getMessage());
                mutationsFailed++;
                lastError = formatException(e);
            }
        }

        // If all mutations failed, member is unreachable
        if (mutationsFailed == mutations.size() && !mutations.isEmpty()) {
            return new FederatedActionResult(config.name(), 0, 0, ConnectionState.UNREACHABLE, lastError);
        }

        // If some mutations failed, include error info but mark as connected
        String error = mutationsFailed > 0 ? mutationsFailed + " flow(s) failed: " + lastError : null;
        return new FederatedActionResult(config.name(), totalSuccess, totalFailure, ConnectionState.CONNECTED, error);
    }

    private String buildMutation(FederatedActionRequest request, String actionName, boolean includeLimit) {
        return switch (actionName) {
            case "acknowledge" -> buildAcknowledgeMutation(request, includeLimit);
            case "replay" -> buildReplayMutation(request, includeLimit);
            case "resume" -> buildResumeMutation(request, includeLimit);
            case "replayAndAcknowledge" -> buildReplayAndAcknowledgeMutation(request, includeLimit);
            default -> throw new IllegalArgumentException("Unknown action: " + actionName);
        };
    }

    private int[] executeLocalAction(String actionName, DeltaFilesFilter filter, String reason, Integer limit) {
        int success = 0;
        int failure = 0;

        switch (actionName) {
            case "acknowledge" -> {
                var results = deltaFilesService.acknowledge(filter, reason, limit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            case "replay" -> {
                var results = deltaFilesService.replay(filter, null, null, limit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            case "resume" -> {
                var results = deltaFilesService.resume(filter, null, limit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            case "replayAndAcknowledge" -> {
                var results = deltaFilesService.replayAndAcknowledgeMatching(filter, reason, null, null, limit);
                for (var r : results) {
                    if (r.getSuccess()) success++;
                    else failure++;
                }
            }
            default -> throw new IllegalArgumentException("Unknown action: " + actionName);
        }

        return new int[]{success, failure};
    }

    private FederatedActionResult executeMemberActionWithFallback(MemberConfig config, String mutationWithLimit, String mutationWithoutLimit) {
        try {
            // Try with limit first
            int[] counts = executeGraphQLMutation(config, mutationWithLimit);
            return new FederatedActionResult(
                config.name(),
                counts[0],
                counts[1],
                ConnectionState.CONNECTED,
                null
            );
        } catch (LimitNotSupportedException e) {
            // Member doesn't support limit parameter, retry without it
            try {
                int[] counts = executeGraphQLMutation(config, mutationWithoutLimit);
                return new FederatedActionResult(
                    config.name(),
                    counts[0],
                    counts[1],
                    ConnectionState.CONNECTED,
                    null
                );
            } catch (Exception e2) {
                log.error("Failed to execute action on member {}: {}", config.name(), e2.getMessage());
                return new FederatedActionResult(
                    config.name(),
                    0,
                    0,
                    ConnectionState.UNREACHABLE,
                    formatException(e2)
                );
            }
        } catch (Exception e) {
            log.error("Failed to execute action on member {}: {}", config.name(), e.getMessage());
            return new FederatedActionResult(
                config.name(),
                0,
                0,
                ConnectionState.UNREACHABLE,
                formatException(e)
            );
        }
    }

    private FederatedActionResult handleActionError(MemberConfig config, Throwable ex) {
        String error = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        if (ex instanceof TimeoutException || (ex.getCause() != null && ex.getCause() instanceof TimeoutException)) {
            error = "Request timed out";
        }
        return new FederatedActionResult(
            config.name(),
            0,
            0,
            ConnectionState.UNREACHABLE,
            error
        );
    }

    private DeltaFilesFilter toGraphQLFilter(FederatedErrorFilter filter) {
        DeltaFilesFilter gqlFilter = new DeltaFilesFilter();
        gqlFilter.setStage(DeltaFileStage.ERROR);

        if (filter != null) {
            if (filter.getDataSources() != null && !filter.getDataSources().isEmpty()) {
                gqlFilter.setDataSources(filter.getDataSources());
            }
            if (filter.getTransforms() != null && !filter.getTransforms().isEmpty()) {
                gqlFilter.setTransforms(filter.getTransforms());
            }
            if (filter.getDataSinks() != null && !filter.getDataSinks().isEmpty()) {
                gqlFilter.setDataSinks(filter.getDataSinks());
            }
            if (filter.getErrorCause() != null) {
                gqlFilter.setErrorCause(filter.getErrorCause());
            }
            if (filter.getErrorAcknowledged() != null) {
                gqlFilter.setErrorAcknowledged(filter.getErrorAcknowledged());
            }
            if (filter.getModifiedAfter() != null) {
                gqlFilter.setModifiedAfter(filter.getModifiedAfter());
            }
            if (filter.getModifiedBefore() != null) {
                gqlFilter.setModifiedBefore(filter.getModifiedBefore());
            }
        }

        return gqlFilter;
    }

    private String buildErrorCountQuery(FederatedErrorFilter filter) {
        DeltaFilesFilter gqlFilter = toGraphQLFilter(filter);
        return new GraphQLQueryRequest(
                DeltaFilesGraphQLQuery.newRequest()
                        .limit(0)
                        .filter(gqlFilter)
                        .build(),
                new DeltaFilesProjectionRoot<>().totalCount()
        ).serialize();
    }

    private String buildAcknowledgeMutation(FederatedActionRequest request, boolean includeLimit) {
        DeltaFilesFilter filter = toGraphQLFilter(request.getFilter());
        return new GraphQLQueryRequest(
                AcknowledgeMatchingGraphQLQuery.newRequest()
                        .filter(filter)
                        .reason(request.getReason())
                        .limit(includeLimit ? request.getLimit() : null)
                        .build(),
                new AcknowledgeMatchingProjectionRoot<>().did().success().error()
        ).serialize();
    }

    private String buildReplayMutation(FederatedActionRequest request, boolean includeLimit) {
        DeltaFilesFilter filter = toGraphQLFilter(request.getFilter());
        return new GraphQLQueryRequest(
                ReplayMatchingGraphQLQuery.newRequest()
                        .filter(filter)
                        .limit(includeLimit ? request.getLimit() : null)
                        .build(),
                new ReplayMatchingProjectionRoot<>().did().success().error()
        ).serialize();
    }

    private String buildResumeMutation(FederatedActionRequest request, boolean includeLimit) {
        DeltaFilesFilter filter = toGraphQLFilter(request.getFilter());
        return new GraphQLQueryRequest(
                ResumeMatchingGraphQLQuery.newRequest()
                        .filter(filter)
                        .limit(includeLimit ? request.getLimit() : null)
                        .build(),
                new ResumeMatchingProjectionRoot<>().did().success().error()
        ).serialize();
    }

    private String buildReplayAndAcknowledgeMutation(FederatedActionRequest request, boolean includeLimit) {
        DeltaFilesFilter filter = toGraphQLFilter(request.getFilter());
        return new GraphQLQueryRequest(
                ReplayAndAcknowledgeMatchingGraphQLQuery.newRequest()
                        .filter(filter)
                        .reason(request.getReason())
                        .limit(includeLimit ? request.getLimit() : null)
                        .build(),
                new ReplayAndAcknowledgeMatchingProjectionRoot<>().did().replayDid().success().error()
        ).serialize();
    }

    private long executeErrorCountQuery(MemberConfig config, String query) throws Exception {
        String url = config.url() + "/api/v2/graphql";
        String body = objectMapper.writeValueAsString(Map.of("query", query));

        Duration requestTimeout = Duration.ofMillis(deltaFiPropertiesService.getDeltaFiProperties().getMemberRequestTimeout());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");

        addAuthHeader(config, builder);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());
        return jsonNode.path("data").path("deltaFiles").path("totalCount").asLong(0);
    }

    private int[] executeGraphQLMutation(MemberConfig config, String mutation) throws Exception {
        String url = config.url() + "/api/v2/graphql";
        String body = objectMapper.writeValueAsString(Map.of("query", mutation));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(ACTION_TIMEOUT_MS))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json");

        addAuthHeader(config, builder);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());

        // Check for "Unknown field argument 'limit'" error - indicates member doesn't support limit
        JsonNode errors = jsonNode.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            for (JsonNode error : errors) {
                String message = error.path("message").asText("");
                if (message.contains("Unknown field argument") && message.contains("limit")) {
                    throw new LimitNotSupportedException("Member does not support limit parameter");
                }
            }
        }

        JsonNode data = jsonNode.path("data");

        int success = 0;
        int failure = 0;

        // Find the result array in the response (could be under different mutation names)
        Iterator<JsonNode> fields = data.elements();
        while (fields.hasNext()) {
            JsonNode resultArray = fields.next();
            if (resultArray.isArray()) {
                for (JsonNode result : resultArray) {
                    if (result.path("success").asBoolean(false)) {
                        success++;
                    } else {
                        failure++;
                    }
                }
            }
        }

        return new int[]{success, failure};
    }

    private void addAuthHeader(MemberConfig config, HttpRequest.Builder builder) {
        if (config.credentials() != null && "basic".equals(config.credentials().type())) {
            String password = System.getenv(config.credentials().passwordEnvVar());
            if (password != null) {
                String credentials = config.credentials().username() + ":" + password;
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                builder.header("Authorization", "Basic " + encoded);
            }
        }
    }

    private FederatedErrorResponse buildErrorResponse(List<FederatedErrorResult> results) {
        long totalCount = results.stream()
            .filter(r -> r.count() != null)
            .mapToLong(FederatedErrorResult::count)
            .sum();

        int membersSearched = (int) results.stream()
            .filter(r -> r.status() == ConnectionState.CONNECTED)
            .count();

        int membersFailed = (int) results.stream()
            .filter(r -> r.status() != ConnectionState.CONNECTED)
            .count();

        return new FederatedErrorResponse(
            results,
            totalCount,
            membersSearched,
            membersFailed,
            OffsetDateTime.now()
        );
    }

    private FederatedActionResponse buildActionResponse(List<FederatedActionResult> results) {
        int totalSuccess = results.stream()
            .mapToInt(FederatedActionResult::successCount)
            .sum();

        int totalFailure = results.stream()
            .mapToInt(FederatedActionResult::failureCount)
            .sum();

        int membersFailed = (int) results.stream()
            .filter(r -> r.status() != ConnectionState.CONNECTED)
            .count();

        return new FederatedActionResponse(
            results,
            totalSuccess,
            totalFailure,
            membersFailed
        );
    }

    /**
     * Filter member configs by name and/or tag from the filter.
     */
    private List<MemberConfig> filterMemberConfigs(List<MemberConfig> configs, FederatedErrorFilter filter) {
        if (filter == null) {
            return configs;
        }

        List<String> memberNames = filter.getMemberNames();
        List<String> memberTags = filter.getMemberTags();

        if ((memberNames == null || memberNames.isEmpty()) && (memberTags == null || memberTags.isEmpty())) {
            return configs;
        }

        return configs.stream()
            .filter(config -> {
                // If memberNames is specified, config must match one of them
                if (memberNames != null && !memberNames.isEmpty()) {
                    if (!memberNames.contains(config.name())) {
                        return false;
                    }
                }
                // If memberTags is specified, config must have at least one matching tag
                if (memberTags != null && !memberTags.isEmpty()) {
                    if (config.tags() == null || config.tags().stream().noneMatch(memberTags::contains)) {
                        return false;
                    }
                }
                return true;
            })
            .toList();
    }

    /**
     * Check if leader should be included based on member filter.
     */
    private boolean shouldIncludeLeader(FederatedErrorFilter filter) {
        if (filter == null) {
            return true;
        }
        // If memberNames is specified and doesn't include "Leader", exclude leader
        if (filter.getMemberNames() != null && !filter.getMemberNames().isEmpty()) {
            if (!filter.getMemberNames().contains("Leader")) {
                return false;
            }
        }
        // If memberTags is specified, leader is only included if "leader" tag is in the list
        if (filter.getMemberTags() != null && !filter.getMemberTags().isEmpty()) {
            if (!filter.getMemberTags().contains("leader")) {
                return false;
            }
        }
        return true;
    }

    private String formatException(Exception e) {
        String message = e.getMessage();
        if (message == null) return "Unknown error";

        if (message.contains("Connection refused")) return "Connection refused";
        if (message.contains("timed out")) return "Connection timed out";
        if (message.contains("UnknownHostException")) return "Host not found";

        return message.replaceAll("java\\.\\w+\\.\\w+Exception: ", "");
    }

    /**
     * Thrown when a member's GraphQL schema doesn't support the limit parameter.
     */
    private static class LimitNotSupportedException extends Exception {
        LimitNotSupportedException(String message) {
            super(message);
        }
    }
}
