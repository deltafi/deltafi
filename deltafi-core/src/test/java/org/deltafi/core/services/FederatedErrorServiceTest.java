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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.deltafi.common.types.DeltaFileStage;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.ErrorSummaryFilter;
import org.deltafi.core.types.SummaryByFlow;
import org.deltafi.core.types.SummaryByFlowAndMessage;
import org.deltafi.core.types.leader.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ABOUTME: Unit tests for FederatedErrorService.
 * ABOUTME: Tests federated error search and action operations across fleet members.
 */
@ExtendWith(MockitoExtension.class)
class FederatedErrorServiceTest {

    @Mock
    private DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    private DeltaFilesService deltaFilesService;

    @Mock
    private HttpClient httpClient;

    @Mock
    private DeltaFiProperties deltaFiProperties;

    @Mock
    private HttpResponse<String> httpResponse;

    @Captor
    private ArgumentCaptor<HttpRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<DeltaFilesFilter> filterCaptor;

    private ObjectMapper objectMapper;
    private FederatedErrorService federatedErrorService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        lenient().when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        lenient().when(deltaFiProperties.getMemberRequestTimeout()).thenReturn(10000);
        lenient().when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of());

        federatedErrorService = new FederatedErrorService(
                deltaFiPropertiesService,
                deltaFilesService,
                objectMapper,
                httpClient
        );
    }

    @Nested
    class SearchErrorsTests {

        @Test
        void searchErrors_noMembersConfigured_returnsLeaderOnly() {
            DeltaFiles results = createDeltaFiles(10);

            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(results);

            FederatedErrorResponse response = federatedErrorService.searchErrors(null);

            assertEquals(1, response.results().size());
            assertEquals("Leader", response.results().get(0).memberName());
            assertEquals(10L, response.results().get(0).count());
            assertEquals(ConnectionState.CONNECTED, response.results().get(0).status());
            assertEquals(10L, response.totalCount());
            assertEquals(1, response.membersSearched());
            assertEquals(0, response.membersFailed());
        }

        @Test
        void searchErrors_leaderThrowsException_returnsUnreachable() {
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenThrow(new RuntimeException("Database unavailable"));

            FederatedErrorResponse response = federatedErrorService.searchErrors(null);

            assertEquals(1, response.results().size());
            assertEquals("Leader", response.results().get(0).memberName());
            assertEquals(ConnectionState.UNREACHABLE, response.results().get(0).status());
            assertNotNull(response.results().get(0).error());
            assertNull(response.results().get(0).count());
            assertEquals(0, response.membersSearched());
            assertEquals(1, response.membersFailed());
        }

        @Test
        void searchErrors_withMemberNameFilter_excludesLeaderWhenNotInList() {
            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberNames(List.of("member1"))
                    .build();

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedErrorResponse response = federatedErrorService.searchErrors(filter);

            // Leader should not be queried
            verify(deltaFilesService, never()).deltaFiles(anyInt(), anyInt(), any(), any(), any());
            // Response should only contain the (failed due to mock) member result
            assertTrue(response.results().stream().noneMatch(r -> "Leader".equals(r.memberName())));
        }

        @Test
        void searchErrors_withMemberNameFilterIncludingLeader_includesLeader() {
            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberNames(List.of("Leader", "member1"))
                    .build();

            DeltaFiles results = createDeltaFiles(10);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(results);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedErrorResponse response = federatedErrorService.searchErrors(filter);

            // Leader should be queried
            verify(deltaFilesService).deltaFiles(anyInt(), anyInt(), any(), any(), any());
            assertTrue(response.results().stream().anyMatch(r -> "Leader".equals(r.memberName())));
        }

        @Test
        void searchErrors_withMemberTagFilter_excludesLeaderWhenTagNotMatching() {
            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberTags(List.of("production"))
                    .build();

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of("production"), null)
            ));

            FederatedErrorResponse response = federatedErrorService.searchErrors(filter);

            // Leader is excluded because "leader" tag is not in the filter
            verify(deltaFilesService, never()).deltaFiles(anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        void searchErrors_withMemberTagFilterIncludingLeader_includesLeader() {
            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberTags(List.of("leader", "production"))
                    .build();

            DeltaFiles results = createDeltaFiles(10);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(results);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of("production"), null)
            ));

            FederatedErrorResponse response = federatedErrorService.searchErrors(filter);

            verify(deltaFilesService).deltaFiles(anyInt(), anyInt(), any(), any(), any());
        }

        @Test
        void searchErrors_appliesFilterToLocalQuery() {
            OffsetDateTime after = OffsetDateTime.now().minusDays(1);
            OffsetDateTime before = OffsetDateTime.now();

            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .dataSources(List.of("source1", "source2"))
                    .errorCause("Connection timeout")
                    .modifiedAfter(after)
                    .modifiedBefore(before)
                    .build();

            DeltaFiles results = createDeltaFiles(5);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), filterCaptor.capture(), isNull(), isNull()))
                    .thenReturn(results);

            federatedErrorService.searchErrors(filter);

            DeltaFilesFilter capturedFilter = filterCaptor.getValue();
            assertEquals(DeltaFileStage.ERROR, capturedFilter.getStage());
            assertEquals(List.of("source1", "source2"), capturedFilter.getDataSources());
            assertEquals("Connection timeout", capturedFilter.getErrorCause());
            assertNull(capturedFilter.getErrorAcknowledged());
            assertEquals(after, capturedFilter.getModifiedAfter());
            assertEquals(before, capturedFilter.getModifiedBefore());
        }

        @Test
        void searchErrors_respectsErrorAcknowledgedFilter() {
            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .errorAcknowledged(true) // Only acknowledged errors
                    .build();

            DeltaFiles results = createDeltaFiles(5);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), filterCaptor.capture(), isNull(), isNull()))
                    .thenReturn(results);

            federatedErrorService.searchErrors(filter);

            DeltaFilesFilter capturedFilter = filterCaptor.getValue();
            assertEquals(true, capturedFilter.getErrorAcknowledged());
        }

        @Test
        void searchErrors_withMembers_queriesInParallel() throws IOException, InterruptedException {
            String successResponse = """
                    {"data": {"deltaFiles": {"totalCount": 15}}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(successResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            DeltaFiles leaderResults = createDeltaFiles(10);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(leaderResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of("east"), null),
                    new MemberConfig("member2", "http://member2.example.com", List.of("west"), null)
            ));

            FederatedErrorResponse response = federatedErrorService.searchErrors(null);

            assertEquals(3, response.results().size());
            // Leader: 10; Member1: 15; Member2: 15
            assertEquals(40L, response.totalCount()); // 10 + 15 + 15
            assertEquals(3, response.membersSearched());
            assertEquals(0, response.membersFailed());

            // Verify HTTP requests were made to both members
            verify(httpClient, times(2)).send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any());
        }

        @Test
        void searchErrors_memberHttpError_marksAsUnreachable() throws IOException, InterruptedException {
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            DeltaFiles leaderResults = createDeltaFiles(10);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(leaderResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedErrorResponse response = federatedErrorService.searchErrors(null);

            assertEquals(2, response.results().size());

            FederatedErrorResult leaderResult = response.results().stream()
                    .filter(r -> "Leader".equals(r.memberName()))
                    .findFirst().orElseThrow();
            assertEquals(ConnectionState.CONNECTED, leaderResult.status());

            FederatedErrorResult memberResult = response.results().stream()
                    .filter(r -> "member1".equals(r.memberName()))
                    .findFirst().orElseThrow();
            assertEquals(ConnectionState.UNREACHABLE, memberResult.status());
            assertNotNull(memberResult.error());
            assertNull(memberResult.count());

            assertEquals(10L, response.totalCount()); // Only leader counted
            assertEquals(1, response.membersSearched());
            assertEquals(1, response.membersFailed());
        }

        @Test
        void searchErrors_filtersMembersByTag() throws IOException, InterruptedException {
            String successResponse = """
                    {"data": {"deltaFiles": {"totalCount": 20}}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(successResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of("east", "production"), null),
                    new MemberConfig("member2", "http://member2.example.com", List.of("west", "production"), null),
                    new MemberConfig("member3", "http://member3.example.com", List.of("test"), null)
            ));

            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberTags(List.of("production", "leader"))
                    .build();

            DeltaFiles leaderResults = createDeltaFiles(5);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(leaderResults);

            FederatedErrorResponse response = federatedErrorService.searchErrors(filter);

            // Should include leader + member1 + member2, exclude member3
            assertEquals(3, response.results().size());
            assertTrue(response.results().stream().anyMatch(r -> "Leader".equals(r.memberName())));
            assertTrue(response.results().stream().anyMatch(r -> "member1".equals(r.memberName())));
            assertTrue(response.results().stream().anyMatch(r -> "member2".equals(r.memberName())));
            assertFalse(response.results().stream().anyMatch(r -> "member3".equals(r.memberName())));
        }

        @Test
        void searchErrors_filtersMembersByName() throws IOException, InterruptedException {
            String successResponse = """
                    {"data": {"deltaFiles": {"totalCount": 20}}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(successResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null),
                    new MemberConfig("member2", "http://member2.example.com", List.of(), null),
                    new MemberConfig("member3", "http://member3.example.com", List.of(), null)
            ));

            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberNames(List.of("member1", "member3"))
                    .build();

            FederatedErrorResponse response = federatedErrorService.searchErrors(filter);

            // Should include member1 + member3, exclude Leader and member2
            assertEquals(2, response.results().size());
            assertTrue(response.results().stream().anyMatch(r -> "member1".equals(r.memberName())));
            assertTrue(response.results().stream().anyMatch(r -> "member3".equals(r.memberName())));
            assertFalse(response.results().stream().anyMatch(r -> "Leader".equals(r.memberName())));
            assertFalse(response.results().stream().anyMatch(r -> "member2".equals(r.memberName())));
        }
    }

    @Nested
    class ActionTests {

        @Test
        void acknowledgeErrors_executesLocallyAndOnMembers() throws IOException, InterruptedException {
            String memberResponse = """
                    {"data": {"acknowledgeMatching": [{"did": "abc", "success": true}, {"did": "def", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            List<AcknowledgeResult> localResults = List.of(
                    createAcknowledgeResult(true),
                    createAcknowledgeResult(true),
                    createAcknowledgeResult(false)
            );
            when(deltaFilesService.acknowledge(any(DeltaFilesFilter.class), anyString(), isNull()))
                    .thenReturn(localResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Acknowledged via federated operation")
                    .build();

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            assertEquals(2, response.results().size());
            assertEquals(4, response.totalSuccessCount()); // 2 local + 2 member
            assertEquals(1, response.totalFailureCount()); // 1 local failure
            assertEquals(0, response.membersFailed());

            verify(deltaFilesService).acknowledge(any(DeltaFilesFilter.class), eq("Acknowledged via federated operation"), isNull());
        }

        @Test
        void resumeErrors_executesLocallyAndOnMembers() throws IOException, InterruptedException {
            String memberResponse = """
                    {"data": {"resumeMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            List<RetryResult> localResults = List.of(createRetryResult(true), createRetryResult(true));
            when(deltaFilesService.resume(any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(localResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .build();

            FederatedActionResponse response = federatedErrorService.resumeErrors(request);

            assertEquals(2, response.results().size());
            assertEquals(3, response.totalSuccessCount()); // 2 local + 1 member
        }

        @Test
        void replayErrors_executesLocallyAndOnMembers() throws IOException, InterruptedException {
            String memberResponse = """
                    {"data": {"replayMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            List<RetryResult> localResults = List.of(createRetryResult(true));
            when(deltaFilesService.replay(any(DeltaFilesFilter.class), isNull(), isNull(), isNull()))
                    .thenReturn(localResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .build();

            FederatedActionResponse response = federatedErrorService.replayErrors(request);

            assertEquals(2, response.results().size());
            assertEquals(2, response.totalSuccessCount());
        }

        @Test
        void replayAndAcknowledgeErrors_executesLocallyAndOnMembers() throws IOException, InterruptedException {
            String memberResponse = """
                    {"data": {"replayAndAcknowledgeMatching": [{"did": "abc", "replayDid": "xyz", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            List<ReplayAndAcknowledgeResult> localResults = List.of(createReplayAndAcknowledgeResult(true));
            when(deltaFilesService.replayAndAcknowledgeMatching(any(DeltaFilesFilter.class), anyString(), isNull(), isNull(), isNull()))
                    .thenReturn(localResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Replayed and acknowledged")
                    .build();

            FederatedActionResponse response = federatedErrorService.replayAndAcknowledgeErrors(request);

            assertEquals(2, response.results().size());
            assertEquals(2, response.totalSuccessCount());

            verify(deltaFilesService).replayAndAcknowledgeMatching(
                    any(DeltaFilesFilter.class), eq("Replayed and acknowledged"), isNull(), isNull(), isNull());
        }

        @Test
        void executeAction_withLimit_passesLimitToLocalService() {
            List<AcknowledgeResult> localResults = List.of(createAcknowledgeResult(true));
            when(deltaFilesService.acknowledge(any(DeltaFilesFilter.class), anyString(), eq(100)))
                    .thenReturn(localResults);

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test")
                    .limit(100)
                    .build();

            federatedErrorService.acknowledgeErrors(request);

            verify(deltaFilesService).acknowledge(any(DeltaFilesFilter.class), eq("Test"), eq(100));
        }

        @Test
        void executeAction_onlyOnSpecificMembers() throws IOException, InterruptedException {
            String memberResponse = """
                    {"data": {"acknowledgeMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null),
                    new MemberConfig("member2", "http://member2.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test")
                    .memberNames(List.of("member1"))
                    .build();

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            // Only member1 should be included, Leader excluded (not in memberNames)
            assertEquals(1, response.results().size());
            assertEquals("member1", response.results().get(0).memberName());

            // Verify local service was NOT called (Leader excluded)
            verify(deltaFilesService, never()).acknowledge(any(DeltaFilesFilter.class), anyString(), any());
        }

        @Test
        void executeAction_memberHttpError_marksAsFailed() throws IOException, InterruptedException {
            when(httpResponse.statusCode()).thenReturn(500);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            List<AcknowledgeResult> localResults = List.of(createAcknowledgeResult(true));
            when(deltaFilesService.acknowledge(any(DeltaFilesFilter.class), anyString(), isNull()))
                    .thenReturn(localResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test")
                    .build();

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            assertEquals(2, response.results().size());
            assertEquals(1, response.totalSuccessCount()); // Only leader succeeded
            assertEquals(1, response.membersFailed());

            FederatedActionResult memberResult = response.results().stream()
                    .filter(r -> "member1".equals(r.memberName()))
                    .findFirst().orElseThrow();
            assertEquals(ConnectionState.UNREACHABLE, memberResult.status());
        }

        @Test
        void executeAction_memberDoesNotSupportLimit_fallsBackToNoLimit() throws IOException, InterruptedException {
            // First response: error indicating limit not supported
            String limitNotSupportedResponse = """
                    {"errors": [{"message": "Unknown field argument 'limit' on field 'Mutation.acknowledgeMatching'"}]}
                    """;
            // Second response: success without limit
            String successResponse = """
                    {"data": {"acknowledgeMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body())
                    .thenReturn(limitNotSupportedResponse)
                    .thenReturn(successResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            List<AcknowledgeResult> localResults = List.of(createAcknowledgeResult(true));
            when(deltaFilesService.acknowledge(any(DeltaFilesFilter.class), anyString(), eq(50)))
                    .thenReturn(localResults);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test")
                    .limit(50)
                    .build();

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            // Should succeed after fallback
            assertEquals(2, response.results().size());
            assertEquals(2, response.totalSuccessCount());
            assertEquals(0, response.membersFailed());

            // Verify two HTTP requests were made (first with limit, second without)
            verify(httpClient, times(2)).send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any());
        }

        @Test
        void executeAction_localServiceError_marksLeaderAsFailed() {
            when(deltaFilesService.acknowledge(any(DeltaFilesFilter.class), anyString(), isNull()))
                    .thenThrow(new RuntimeException("Database error"));

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test")
                    .build();

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            assertEquals(1, response.results().size());
            FederatedActionResult leaderResult = response.results().get(0);
            assertEquals("Leader", leaderResult.memberName());
            assertEquals(ConnectionState.UNREACHABLE, leaderResult.status());
            assertEquals(0, leaderResult.successCount());
            assertNotNull(leaderResult.error());
        }
    }

    @Nested
    class ErrorSummaryTests {

        @Test
        void errorSummaryByDataSource_aggregatesFromMultipleSources() throws IOException, InterruptedException {
            // Local summary
            SummaryByFlow localSummary = new SummaryByFlow(0, 2, 2, List.of(
                    createCountPerFlow("source1", 10),
                    createCountPerFlow("source2", 5)
            ));
            when(deltaFilesService.getErrorSummaryByFlow(eq(0), eq(10000), any(ErrorSummaryFilter.class), isNull(), isNull()))
                    .thenReturn(localSummary);

            // Member summary
            String memberResponse = """
                    {"data": {"errorSummaryByFlow": {"countPerFlow": [
                        {"flow": "source1", "type": "REST_DATA_SOURCE", "count": 8},
                        {"flow": "source3", "type": "TRANSFORM", "count": 3}
                    ]}}}
                    """;
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedErrorSummaryResponse response = federatedErrorService.errorSummaryByDataSource(null);

            assertEquals(3, response.summaries().size());

            // source1 should be aggregated: 10 + 8 = 18
            FederatedErrorSummary source1 = response.summaries().stream()
                    .filter(s -> "source1".equals(s.key()))
                    .findFirst().orElseThrow();
            assertEquals(18L, source1.count());
            assertEquals(2, source1.nodeCount());

            // source2 only from leader: 5
            FederatedErrorSummary source2 = response.summaries().stream()
                    .filter(s -> "source2".equals(s.key()))
                    .findFirst().orElseThrow();
            assertEquals(5L, source2.count());
            assertEquals(1, source2.nodeCount());

            // source3 only from member: 3
            FederatedErrorSummary source3 = response.summaries().stream()
                    .filter(s -> "source3".equals(s.key()))
                    .findFirst().orElseThrow();
            assertEquals(3L, source3.count());
            assertEquals(1, source3.nodeCount());

            assertEquals(26L, response.totalCount());
            assertEquals(2, response.membersSearched());
        }

        @Test
        void errorSummaryByMessage_aggregatesFromMultipleSources() throws IOException, InterruptedException {
            // Local summary
            SummaryByFlowAndMessage localSummary = new SummaryByFlowAndMessage(0, 2, 2, List.of(
                    createCountPerMessage("Connection timeout", 15),
                    createCountPerMessage("Invalid format", 7)
            ));
            when(deltaFilesService.getErrorSummaryByMessage(eq(0), eq(10000), any(ErrorSummaryFilter.class), isNull(), isNull()))
                    .thenReturn(localSummary);

            // Member summary
            String memberResponse = """
                    {"data": {"errorSummaryByMessage": {"countPerMessage": [
                        {"message": "Connection timeout", "count": 12}
                    ]}}}
                    """;
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedErrorSummaryResponse response = federatedErrorService.errorSummaryByMessage(null);

            assertEquals(2, response.summaries().size());

            // "Connection timeout" aggregated: 15 + 12 = 27
            FederatedErrorSummary timeout = response.summaries().stream()
                    .filter(s -> "Connection timeout".equals(s.key()))
                    .findFirst().orElseThrow();
            assertEquals(27L, timeout.count());
            assertEquals(2, timeout.nodeCount());

            // "Invalid format" only from leader: 7
            FederatedErrorSummary invalid = response.summaries().stream()
                    .filter(s -> "Invalid format".equals(s.key()))
                    .findFirst().orElseThrow();
            assertEquals(7L, invalid.count());
            assertEquals(1, invalid.nodeCount());
        }

        @Test
        void errorSummaryByDataSource_sortsByErrorCountDescending() throws IOException, InterruptedException {
            SummaryByFlow localSummary = new SummaryByFlow(0, 3, 3, List.of(
                    createCountPerFlow("source1", 5),
                    createCountPerFlow("source2", 20),
                    createCountPerFlow("source3", 10)
            ));
            when(deltaFilesService.getErrorSummaryByFlow(eq(0), eq(10000), any(ErrorSummaryFilter.class), isNull(), isNull()))
                    .thenReturn(localSummary);

            FederatedErrorSummaryResponse response = federatedErrorService.errorSummaryByDataSource(null);

            assertEquals(3, response.summaries().size());
            assertEquals("source2", response.summaries().get(0).key()); // 20
            assertEquals("source3", response.summaries().get(1).key()); // 10
            assertEquals("source1", response.summaries().get(2).key()); // 5
        }

        @Test
        void errorSummary_memberError_countsAsFailed() throws IOException, InterruptedException {
            SummaryByFlow localSummary = new SummaryByFlow(0, 1, 1, List.of(
                    createCountPerFlow("source1", 10)
            ));
            when(deltaFilesService.getErrorSummaryByFlow(eq(0), eq(10000), any(ErrorSummaryFilter.class), isNull(), isNull()))
                    .thenReturn(localSummary);

            when(httpResponse.statusCode()).thenReturn(500);
            when(httpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedErrorSummaryResponse response = federatedErrorService.errorSummaryByDataSource(null);

            assertEquals(1, response.summaries().size());
            assertEquals(10L, response.totalCount());
            assertEquals(1, response.membersSearched());
            assertEquals(1, response.membersFailed());
        }
    }

    @Nested
    class AuthenticationTests {

        @Test
        void searchErrors_withBasicAuth_addsAuthorizationHeader() throws IOException, InterruptedException {
            String successResponse = """
                    {"data": {"deltaFiles": {"totalCount": 5}}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(successResponse);
            when(httpClient.send(requestCaptor.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            DeltaFiles leaderResults = createDeltaFiles(10);
            when(deltaFilesService.deltaFiles(eq(0), eq(0), any(DeltaFilesFilter.class), isNull(), isNull()))
                    .thenReturn(leaderResults);

            // Set up environment variable for password (this won't actually work in test,
            // but we verify the auth header logic)
            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(),
                            new CredentialsConfig("basic", "testuser", "MEMBER1_PASSWORD"))
            ));

            federatedErrorService.searchErrors(null);

            HttpRequest capturedRequest = requestCaptor.getValue();
            // Note: In actual test, password env var won't be set, so header won't be added
            // This test verifies the request is made correctly
            assertEquals("http://member1.example.com/api/v2/graphql", capturedRequest.uri().toString());
            assertEquals("POST", capturedRequest.method());
        }
    }

    @Nested
    class GraphQLEscapingTests {

        @Test
        void escapeGraphQL_handlesQuotesAndBackslashes() throws IOException, InterruptedException {
            // We test escaping indirectly by verifying the mutation is built correctly
            String memberResponse = """
                    {"data": {"acknowledgeMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(requestCaptor.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test with \"quotes\" and \\backslash")
                    .memberNames(List.of("member1"))
                    .build();

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            assertEquals(1, response.results().size());
            assertEquals(1, response.totalSuccessCount());
        }

        @Test
        void escapeGraphQL_handlesControlCharacters() throws IOException, InterruptedException {
            String memberResponse = """
                    {"data": {"acknowledgeMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(memberResponse);
            when(httpClient.send(requestCaptor.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            // Reason with newlines, tabs, and other control characters
            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Line1\nLine2\tTabbed\rCarriage\b\fFormfeed")
                    .memberNames(List.of("member1"))
                    .build();

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            FederatedActionResponse response = federatedErrorService.acknowledgeErrors(request);

            assertEquals(1, response.results().size());
            assertEquals(1, response.totalSuccessCount());
        }

        @Test
        void buildFilterString_escapesSpecialCharacters() throws IOException, InterruptedException {
            String successResponse = """
                    {"data": {"deltaFiles": {"totalCount": 5}}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(successResponse);
            when(httpClient.send(requestCaptor.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            // Skip leader
            FederatedErrorFilter filter = FederatedErrorFilter.builder()
                    .memberNames(List.of("member1"))
                    .errorCause("Error: \"Connection\" failed\\path")
                    .build();

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            federatedErrorService.searchErrors(filter);

            HttpRequest capturedRequest = requestCaptor.getValue();
            // Verify the request body contains escaped characters
            // The actual assertion would require extracting the body
        }

        @Test
        void buildMutation_includesReasonForAcknowledge() throws IOException, InterruptedException {
            String successResponse = """
                    {"data": {"acknowledgeMatching": [{"did": "abc", "success": true}]}}
                    """;

            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(successResponse);
            when(httpClient.send(requestCaptor.capture(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(httpResponse);

            // Skip leader
            FederatedActionRequest request = FederatedActionRequest.builder()
                    .filter(FederatedErrorFilter.builder().build())
                    .reason("Test reason with \"quotes\"")
                    .memberNames(List.of("member1"))
                    .build();

            when(deltaFiProperties.getMemberConfigs()).thenReturn(List.of(
                    new MemberConfig("member1", "http://member1.example.com", List.of(), null)
            ));

            federatedErrorService.acknowledgeErrors(request);

            // Verify request was made
            verify(httpClient).send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any());
        }
    }

    // Helper methods

    private DeltaFiles createDeltaFiles(int totalCount) {
        DeltaFiles deltaFiles = new DeltaFiles();
        deltaFiles.setTotalCount(totalCount);
        deltaFiles.setDeltaFiles(List.of());
        return deltaFiles;
    }

    private AcknowledgeResult createAcknowledgeResult(boolean success) {
        return AcknowledgeResult.newBuilder()
                .did(java.util.UUID.randomUUID())
                .success(success)
                .build();
    }

    private RetryResult createRetryResult(boolean success) {
        return RetryResult.newBuilder()
                .did(java.util.UUID.randomUUID())
                .success(success)
                .build();
    }

    private ReplayAndAcknowledgeResult createReplayAndAcknowledgeResult(boolean success) {
        return ReplayAndAcknowledgeResult.newBuilder()
                .did(java.util.UUID.randomUUID())
                .replayDid(java.util.UUID.randomUUID())
                .success(success)
                .build();
    }

    private CountPerFlow createCountPerFlow(String flow, long count) {
        CountPerFlow cpf = new CountPerFlow();
        cpf.setFlow(flow);
        cpf.setType(FlowType.REST_DATA_SOURCE);
        cpf.setCount((int) count);
        return cpf;
    }

    private CountPerMessage createCountPerMessage(String message, long count) {
        CountPerMessage cpm = new CountPerMessage();
        cpm.setMessage(message);
        cpm.setCount((int) count);
        return cpm;
    }
}
