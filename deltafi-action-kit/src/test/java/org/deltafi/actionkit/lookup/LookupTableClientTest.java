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
package org.deltafi.actionkit.lookup;

import com.netflix.graphql.dgs.client.DefaultDgsGraphQLResponse;
import com.netflix.graphql.dgs.client.DgsGraphQLClient;
import com.netflix.graphql.dgs.client.DgsGraphQLResponse;
import com.netflix.graphql.dgs.client.Jackson3DgsJsonMapperAdapter;
import org.deltafi.actionkit.generated.types.Result;
import org.deltafi.common.http.HttpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class LookupTableClientTest {

    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1000;

    private static final String CACHEABLE_RESPONSE = """
            {
              "data": {
                "lookup": {
                  "ttlSeconds": 3600,
                  "rows": [
                    [
                      { "column": "city", "value": "Miami" },
                      { "column": "state", "value": "FL" }
                    ],
                    [
                      { "column": "city", "value": "Austin" },
                      { "column": "state", "value": "TX" }
                    ],
                    [
                      { "column": "city", "value": "Las Vegas" },
                      { "column": "state", "value": "NV" }
                    ]
                  ],
                  "totalCount": 25
                }
              }
            }""";

    // No ttlSeconds -> the result must not be cached.
    private static final String NON_CACHEABLE_RESPONSE = """
            {
              "data": {
                "lookup": {
                  "rows": [
                    [
                      { "column": "city", "value": "Miami" },
                      { "column": "state", "value": "FL" }
                    ]
                  ],
                  "totalCount": 1
                }
              }
            }""";

    private DgsGraphQLClient graphQLClient;
    private CachingLookupTableClient lookupTableClient;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        graphQLClient = Mockito.mock(DgsGraphQLClient.class);
        HttpService httpService = Mockito.mock(HttpService.class);
        lookupTableClient = new CachingLookupTableClient("unused", graphQLClient, httpService, MAX_BYTES, MAX_ENTRIES);
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private static DgsGraphQLResponse graphQLResponse(String json) {
        return new DefaultDgsGraphQLResponse(json, Map.of(), Jackson3DgsJsonMapperAdapter.Companion.fromOptions());
    }

    private void stub(String responseJson) {
        Mockito.when(graphQLClient.executeQuery(Mockito.anyString(), Mockito.anyMap()))
                .thenReturn(graphQLResponse(responseJson));
    }

    private static LookupOptions matching(Set<String> states, Set<String> resultColumns) {
        return LookupOptions.builder()
                .matchingColumnValues(Map.of("state", new TreeSet<>(states)))
                .resultColumns(new TreeSet<>(resultColumns))
                .build();
    }

    @Test
    void lookupReturnsMappedRows() throws Exception {
        stub(CACHEABLE_RESPONSE);

        LookupResults results = lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());

        assertThat(results.results()).hasSize(3);
        assertThat(results.results().get(1)).containsEntry("city", "Austin").containsEntry("state", "TX");
        assertThat(results.ttlSeconds()).isEqualTo(3600);
    }

    @Test
    void cachesResultsWithTtl() throws Exception {
        stub(CACHEABLE_RESPONSE);

        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());
        // Identical query is served from the cache; core is not hit again.
        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());

        Mockito.verify(graphQLClient, Mockito.times(1)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    void doesNotCacheWhenNoTtl() throws Exception {
        stub(NON_CACHEABLE_RESPONSE);

        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());
        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());

        // ttlSeconds absent -> every lookup hits core.
        Mockito.verify(graphQLClient, Mockito.times(2)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    void distinctOptionsAreCachedSeparately() throws Exception {
        stub(CACHEABLE_RESPONSE);

        lookupTableClient.lookup("test-lookup-table", matching(Set.of("NV", "TX"), Set.of("city")));
        // Same options -> cache hit.
        lookupTableClient.lookup("test-lookup-table", matching(Set.of("NV", "TX"), Set.of("city")));
        Mockito.verify(graphQLClient, Mockito.times(1)).executeQuery(Mockito.anyString(), Mockito.anyMap());

        // Different matching values -> new query.
        lookupTableClient.lookup("test-lookup-table", matching(Set.of("FL"), Set.of("city")));
        Mockito.verify(graphQLClient, Mockito.times(2)).executeQuery(Mockito.anyString(), Mockito.anyMap());

        // Different result columns -> new query.
        lookupTableClient.lookup("test-lookup-table", matching(Set.of("NV", "TX"), Set.of("city", "state")));
        Mockito.verify(graphQLClient, Mockito.times(3)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    void clearCacheForcesReQuery() throws Exception {
        stub(CACHEABLE_RESPONSE);

        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());
        lookupTableClient.clearCache();
        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());

        Mockito.verify(graphQLClient, Mockito.times(2)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    void coalescesConcurrentMissesForSameKey() throws Exception {
        // Guards single-flight: drop the inFlight coalescing and both concurrent callers query core (calls == 2).
        Gate core = gateCore(() -> graphQLResponse(CACHEABLE_RESPONSE));

        AsyncLookup loader = lookupAsync();   // first caller becomes the sole loader and parks in core
        core.awaitInFlight();
        AsyncLookup waiter = lookupAsync();   // second caller, same key
        awaitParked(waiter);                  // ...joined the in-flight load instead of querying core
        core.release();

        assertThat(loader.get().results()).hasSize(3);
        assertThat(waiter.get().results()).hasSize(3);
        assertThat(core.calls()).isEqualTo(1); // both misses collapsed into one core round-trip
    }

    @Test
    void releasesWaitersWhenLoaderFailsWithError() throws Exception {
        // Guards release-on-any-failure: the load throws an Error (not an Exception). With catch(Exception)
        // instead of catch(Throwable) it escapes uncaught, the shared future is never completed, and waiters
        // hang forever — which surfaces here as get() timing out rather than the expected ExecutionException.
        Gate core = gateCore(() -> { throw new AssertionError("boom"); });

        AsyncLookup loader = lookupAsync();
        core.awaitInFlight();
        AsyncLookup waiter = lookupAsync();
        awaitParked(waiter);
        core.release();

        // Both fail fast; a hang would instead surface as a TimeoutException from get(5s) in AsyncLookup.
        assertThatThrownBy(loader::get).isInstanceOf(ExecutionException.class);
        assertThatThrownBy(waiter::get).isInstanceOf(ExecutionException.class);
    }

    // --- concurrency test harness -------------------------------------------------------------------------

    /** A core stub whose every query records the call, signals it is in-flight, then blocks until released. */
    private static final class Gate {
        private final CountDownLatch inFlight = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        void awaitInFlight() throws InterruptedException {
            assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();
        }
        void release() { released.countDown(); }
        int calls() { return calls.get(); }
    }

    /** Stub core so each query blocks until {@link Gate#release()}, then returns (or throws) {@code onRelease}. */
    private Gate gateCore(ThrowingSupplier<DgsGraphQLResponse> onRelease) {
        Gate gate = new Gate();
        Mockito.when(graphQLClient.executeQuery(Mockito.anyString(), Mockito.anyMap())).thenAnswer(invocation -> {
            gate.calls.incrementAndGet();
            gate.inFlight.countDown();
            assertThat(gate.released.await(5, TimeUnit.SECONDS)).isTrue();
            return onRelease.get();
        });
        return gate;
    }

    /** A lookup running on the pool, exposing its result future and worker thread (to detect parking). */
    private record AsyncLookup(Future<LookupResults> future, AtomicReference<Thread> thread) {
        LookupResults get() throws Exception {
            return future.get(5, TimeUnit.SECONDS);
        }
    }

    /** Start lookup("t") on the pool, capturing its worker thread. */
    private AsyncLookup lookupAsync() {
        AtomicReference<Thread> thread = new AtomicReference<>();
        Future<LookupResults> future = pool.submit(() -> {
            thread.set(Thread.currentThread());
            return lookupTableClient.lookup("t", LookupOptions.defaultLookupOptions());
        });
        return new AsyncLookup(future, thread);
    }

    /** Wait until the lookup's worker thread is parked — i.e. blocked on the shared in-flight load. */
    private static void awaitParked(AsyncLookup lookup) {
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            Thread thread = lookup.thread().get();
            return thread != null && (thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING);
        });
    }

    @Test
    void batchesUpserts() throws Exception {
        DgsGraphQLResponse graphQLResponse = Mockito.mock(DgsGraphQLResponse.class);
        Mockito.when(graphQLResponse.hasErrors()).thenReturn(false);
        Mockito.when(graphQLResponse.extractValueAsObject("upsertLookupTableRows", Result.class))
                .thenReturn(Result.newBuilder().success(true).build());
        Mockito.when(graphQLClient.executeQuery(Mockito.anyString(), Mockito.anyMap())).thenReturn(graphQLResponse);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 20001; i++) {
            rows.add(Map.of("column_a", Integer.toString(i)));
        }
        Result result = lookupTableClient.upsertRows("test-lookup-table", rows);

        assertThat(result.getSuccess()).isTrue();
        Mockito.verify(graphQLClient, Mockito.times(3)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }
}
