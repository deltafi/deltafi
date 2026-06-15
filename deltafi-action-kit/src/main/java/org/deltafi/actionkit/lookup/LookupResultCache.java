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
// ABOUTME: Self-contained, memory-bounded cache for lookup query results with per-entry TTL.
// ABOUTME: No Caffeine/Spring dependency; honors the ttlSeconds core returns per result.
package org.deltafi.actionkit.lookup;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-process cache for {@link LookupResults}, keyed by {@code (tableName, options)}.
 *
 * <p>Each entry carries its own time-to-live, derived from the {@code ttlSeconds} the core returns
 * with each result (how long until the table's next scheduled refresh). Results with no usable TTL
 * are never stored — they pass straight through to the caller.
 *
 * <p>Memory is bounded by an estimated byte budget (the primary control) and an entry count guard.
 * When either limit is exceeded, expired entries are dropped first, then the least-recently-accessed
 * entries are evicted until the cache is back within limits.
 *
 * <p>This class deliberately has no Caffeine or Spring dependency so plugins are not forced to put a
 * particular caching layer on their classpath.
 *
 * <p>Thread safety: backed by a {@link ConcurrentHashMap}; entries are immutable apart from their
 * {@code lastAccessNanos} stamp, which is a benign racy write used only for LRU ordering.
 */
@Slf4j
class LookupResultCache {

    /** Cache key. {@link LookupOptions} is a value record, so equality matches the original query. */
    record CacheKey(String tableName, LookupOptions options) {}

    private static final class CacheEntry {
        private final LookupResults results;
        private final long expiresAtNanos;
        private final long byteCount;
        private volatile long lastAccessNanos;

        CacheEntry(LookupResults results, long expiresAtNanos, long byteCount, long nowNanos) {
            this.results = results;
            this.expiresAtNanos = expiresAtNanos;
            this.byteCount = byteCount;
            this.lastAccessNanos = nowNanos;
        }

        boolean isExpired(long nowNanos) {
            return nowNanos - expiresAtNanos >= 0;
        }
    }

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long maxBytes;
    private final int maxEntries;
    private final LongSupplier nanoTimeSource;

    LookupResultCache(long maxBytes, int maxEntries) {
        this(maxBytes, maxEntries, System::nanoTime);
    }

    /** Package-private constructor allowing tests to supply a deterministic time source. */
    LookupResultCache(long maxBytes, int maxEntries, LongSupplier nanoTimeSource) {
        this.maxBytes = maxBytes;
        this.maxEntries = maxEntries;
        this.nanoTimeSource = nanoTimeSource;
    }

    /** Return the cached result for {@code key} if present and unexpired, otherwise {@code null}. */
    LookupResults get(CacheKey key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        long now = nanoTimeSource.getAsLong();
        if (entry.isExpired(now)) {
            cache.remove(key, entry);
            return null;
        }
        entry.lastAccessNanos = now;
        return entry.results;
    }

    /**
     * Store {@code results} under {@code key} for {@code ttlSeconds}. A {@code null} or non-positive
     * TTL means the result is not cacheable, so nothing is stored.
     */
    void put(CacheKey key, LookupResults results, Integer ttlSeconds) {
        if (ttlSeconds == null || ttlSeconds <= 0) {
            log.debug("Lookup result for table '{}' not cacheable (ttlSeconds={}) — serving uncached",
                    key.tableName(), ttlSeconds);
            return;
        }

        long bytes = estimateBytes(results);
        if (maxBytes > 0 && bytes > maxBytes) {
            // A single result larger than the whole budget can never fit; caching it would only flush
            // every other entry (it sorts newest, so eviction clears the rest first) and then drop it.
            log.debug("Lookup result for table {} (~{} bytes) exceeds the cache budget of {} bytes — not caching",
                    key.tableName(), bytes, maxBytes);
            return;
        }

        long now = nanoTimeSource.getAsLong();
        long expiresAt = now + ttlSeconds * NANOS_PER_SECOND;

        // if expiresAt is less than now we hit an overflow (ttlSeconds * NANOS_PER_SECOND is always positive)
        // treat the expiresAt as the MAX_VALUE in this case
        if (expiresAt < now) {
            expiresAt = Long.MAX_VALUE;
        }
        cache.put(key, new CacheEntry(results, expiresAt, bytes, now));
        log.debug("Cached lookup result for table '{}' for {}s", key.tableName(), ttlSeconds);
        evictIfNeeded(now);
    }

    /** Remove all cached entries. */
    void clear() {
        cache.clear();
    }

    /**
     * Evict expired entries, then least-recently-accessed entries, until the cache is within the
     * configured {@code maxBytes} and {@code maxEntries} limits. A limit of {@code 0} disables it.
     */
    private void evictIfNeeded(long now) {
        cache.values().removeIf(e -> e.isExpired(now));

        if ((maxBytes <= 0 && maxEntries <= 0)) {
            return;
        }

        long totalBytes = cache.values().stream().mapToLong(e -> e.byteCount).sum();
        int totalEntries = cache.size();
        if (underLimit(totalBytes, totalEntries)) {
            return;
        }

        List<Map.Entry<CacheKey, CacheEntry>> candidates = new ArrayList<>(cache.entrySet());
        candidates.sort(Comparator.comparingLong(e -> e.getValue().lastAccessNanos));

        for (Map.Entry<CacheKey, CacheEntry> candidate : candidates) {
            if (underLimit(totalBytes, totalEntries)) {
                break;
            }
            if (cache.remove(candidate.getKey(), candidate.getValue())) {
                totalBytes -= candidate.getValue().byteCount;
                totalEntries--;
                log.debug("Evicted lookup cache entry for table {}: freed ~{} bytes",
                        candidate.getKey().tableName(), candidate.getValue().byteCount);
            }
        }
    }

    private boolean underLimit(long totalBytes, int totalEntries) {
        return (maxBytes <= 0 || totalBytes <= maxBytes) && (maxEntries <= 0 || totalEntries <= maxEntries);
    }


    /**
     * Rough estimate of the heap footprint of a result: two bytes per character of every column name
     * and value, plus a small fixed overhead per row and per entry. Good enough to bound an MB budget
     * without pulling in an object-sizing library.
     */
    private static long estimateBytes(LookupResults results) {
        long bytes = 64; // entry/object overhead
        for (Map<String, String> row : results.results()) {
            bytes += 48; // per-row map overhead
            for (Map.Entry<String, String> cell : row.entrySet()) {
                bytes += 32; // per-cell overhead
                bytes += 2L * cell.getKey().length();
                bytes += 2L * cell.getValue().length();
            }
        }
        return bytes;
    }
}
