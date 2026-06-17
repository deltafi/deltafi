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
// ABOUTME: LookupTableClient that caches results in a self-contained, memory-bounded cache.
// ABOUTME: Honors the per-result ttlSeconds from core; no Caffeine/Spring caching dependency.
package org.deltafi.actionkit.lookup;

import com.netflix.graphql.dgs.client.GraphQLClient;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.lookup.LookupResultCache.CacheKey;
import org.deltafi.common.http.HttpService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * {@link LookupTableClient} that caches lookup query results in process. Each result is held only
 * for the {@code ttlSeconds} the core returns with it — the time until the table's next scheduled
 * refresh — so caching honors per-table refresh settings.
 * Results with no usable TTL are not cached and pass straight through.
 *
 * <p>The cache is self-contained (see {@link LookupResultCache}) and bounded by an estimated memory
 * budget.
 *
 * <p>Concurrent misses for the same key are coalesced (single-flight): when several threads miss the
 * same {@code (table, options)} at once — e.g. right after a shared expiry — only one queries core and
 * the others wait for and share that result, avoiding a cache-stampede burst against core.
 */
@Slf4j
public class CachingLookupTableClient extends LookupTableClient {

    private final LookupResultCache cache;
    private final ConcurrentHashMap<CacheKey, CompletableFuture<LookupResults>> inFlight = new ConcurrentHashMap<>();

    public CachingLookupTableClient(String lookupUrl, GraphQLClient graphQLClient, HttpService httpService,
            long maxBytes, int maxEntries) {
        super(lookupUrl, graphQLClient, httpService);
        this.cache = new LookupResultCache(maxBytes, maxEntries);
    }

    @Override
    public LookupResults lookup(String lookupTableName, LookupOptions lookupOptions) throws Exception {
        CacheKey key = new CacheKey(lookupTableName, lookupOptions);
        LookupResults cached = cache.get(key);
        if (cached != null) {
            log.debug("Lookup cache HIT for table '{}' — not querying core", lookupTableName);
            return cached;
        }
        return loadSingleFlight(key, lookupTableName, lookupOptions);
    }

    /**
     * Fetch the result for a cache miss, ensuring only one thread queries core per key while any
     * concurrent callers for the same key wait for and share that single fetch.
     */
    private LookupResults loadSingleFlight(CacheKey key, String lookupTableName, LookupOptions lookupOptions)
            throws Exception {
        CompletableFuture<LookupResults> ours = new CompletableFuture<>();
        CompletableFuture<LookupResults> inProgress = inFlight.putIfAbsent(key, ours);
        if (inProgress != null) {
            return awaitResult(inProgress); // another thread is already loading this key
        }

        try {
            // Re-check: a concurrent load may have populated the cache between our miss and here.
            LookupResults cached = cache.get(key);
            if (cached != null) {
                log.debug("Lookup cache HIT for table '{}' (populated by a concurrent load)", lookupTableName);
                ours.complete(cached);
                return cached;
            }

            log.debug("Lookup cache MISS for table '{}' — querying core", lookupTableName);
            LookupResults results = super.lookup(lookupTableName, lookupOptions);
            cache.put(key, results, results.ttlSeconds());

            ours.complete(results);
            return results;
        } catch (Throwable t) {
            // Complete on ANY failure — including Error/Throwable — so waiting callers are always released
            // and never block forever on the shared future. Re-throw to the loader unchanged.
            ours.completeExceptionally(t);
            if (t instanceof Error error) {
                throw error;
            }
            throw (Exception) t;
        } finally {
            inFlight.remove(key, ours);
        }
    }

    private static LookupResults awaitResult(CompletableFuture<LookupResults> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause; // surface the original failure to the waiting caller
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    @Override
    public void clearCache() {
        cache.clear();
    }
}
