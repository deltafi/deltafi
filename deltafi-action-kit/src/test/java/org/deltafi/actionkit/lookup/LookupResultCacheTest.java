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

import org.deltafi.actionkit.lookup.LookupResultCache.CacheKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class LookupResultCacheTest {

    /** Mutable, deterministic nanosecond clock for the cache under test. */
    private static final class FakeClock implements LongSupplier {
        private long nanos;

        @Override
        public long getAsLong() {
            return nanos;
        }

        void set(long nanos) {
            this.nanos = nanos;
        }
    }

    private static final long SECOND = 1_000_000_000L;

    private static CacheKey key(String table) {
        return new CacheKey(table, LookupOptions.defaultLookupOptions());
    }

    private static LookupResults results(String value) {
        return new LookupResults(1, null, null, null, List.of(Map.of("k", value)));
    }

    @Test
    void servesWithinTtlAndExpiresAfter() {
        FakeClock clock = new FakeClock();
        LookupResultCache cache = new LookupResultCache(0, 0, clock);

        cache.put(key("t"), results("v"), 10);

        clock.set(9 * SECOND);
        assertThat(cache.get(key("t"))).isNotNull();

        clock.set(10 * SECOND);
        assertThat(cache.get(key("t"))).isNull();
    }

    @Test
    void doesNotCacheWithoutPositiveTtl() {
        LookupResultCache cache = new LookupResultCache(0, 0, new FakeClock());

        cache.put(key("null"), results("v"), null);
        cache.put(key("zero"), results("v"), 0);
        cache.put(key("negative"), results("v"), -5);

        assertThat(cache.get(key("null"))).isNull();
        assertThat(cache.get(key("zero"))).isNull();
        assertThat(cache.get(key("negative"))).isNull();
    }

    @Test
    void evictsLeastRecentlyUsedByEntryCount() {
        FakeClock clock = new FakeClock();
        LookupResultCache cache = new LookupResultCache(0, 2, clock);

        clock.set(1000);
        cache.put(key("a"), results("v"), 100);
        clock.set(2000);
        cache.put(key("b"), results("v"), 100);

        // touch "a" so "b" becomes the least-recently-used
        clock.set(2500);
        assertThat(cache.get(key("a"))).isNotNull();

        clock.set(3000);
        cache.put(key("c"), results("v"), 100);

        assertThat(cache.get(key("b"))).isNull();      // evicted
        assertThat(cache.get(key("a"))).isNotNull();
        assertThat(cache.get(key("c"))).isNotNull();
    }

    @Test
    void evictsLeastRecentlyUsedByByteBudget() {
        FakeClock clock = new FakeClock();
        // Each entry estimates to ~1000 bytes; budget holds two but not three.
        String value = "v".repeat(427);
        LookupResultCache cache = new LookupResultCache(2500, 0, clock);

        clock.set(1000);
        cache.put(key("a"), results(value), 100);
        clock.set(2000);
        cache.put(key("b"), results(value), 100);
        clock.set(3000);
        cache.put(key("c"), results(value), 100);

        assertThat(cache.get(key("a"))).isNull();      // oldest, evicted
        assertThat(cache.get(key("b"))).isNotNull();
        assertThat(cache.get(key("c"))).isNotNull();
    }

    @Test
    void oversizedResultIsNotCachedAndDoesNotEvictExisting() {
        FakeClock clock = new FakeClock();
        // Budget comfortably holds the small entry but not the oversized one (~1000+ bytes).
        LookupResultCache cache = new LookupResultCache(600, 0, clock);

        clock.set(1000);
        cache.put(key("small"), results("v"), 100);

        clock.set(2000);
        cache.put(key("huge"), results("v".repeat(427)), 100);

        assertThat(cache.get(key("huge"))).isNull();    // too large to cache
        assertThat(cache.get(key("small"))).isNotNull(); // existing entry untouched
    }

    @Test
    void clearRemovesEverything() {
        LookupResultCache cache = new LookupResultCache(0, 0, new FakeClock());

        cache.put(key("a"), results("v"), 100);
        cache.put(key("b"), results("v"), 100);
        cache.clear();

        assertThat(cache.get(key("a"))).isNull();
        assertThat(cache.get(key("b"))).isNull();
    }
}
