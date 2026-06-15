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
package org.deltafi.core.lookup;

import org.deltafi.core.lookup.LookupMetadataExtractor.LookupMetadata;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LookupTableRepoTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 9, 12, 0, 0, 0, ZoneOffset.UTC);

    private static LookupMetadata metadata(OffsetDateTime lastRefresh, String refreshDuration) {
        return metadata(lastRefresh, refreshDuration, false);
    }

    private static LookupMetadata metadata(OffsetDateTime lastRefresh, String refreshDuration, boolean pullThrough) {
        return new LookupMetadata(NOW.minusDays(1), NOW.minusHours(2), lastRefresh, refreshDuration, pullThrough, NOW);
    }

    @Test
    void noRefreshDurationIsNotCacheable() {
        assertThat(LookupTableRepo.ttlSeconds(metadata(NOW.minusMinutes(5), null))).isNull();
        assertThat(LookupTableRepo.ttlSeconds(metadata(null, null))).isNull();
    }

    @Test
    void supplierFedTableAnchorsToNextPoll() {
        // lastRefresh 20 minutes ago, hourly refresh -> ~40 minutes until the next poll
        Integer ttl = LookupTableRepo.ttlSeconds(metadata(NOW.minusMinutes(20), "PT1H"));
        assertThat(ttl).isEqualTo(40 * 60);
    }

    @Test
    void supplierFedTableOverdueForPollIsNotCacheable() {
        // lastRefresh 90 minutes ago, hourly refresh -> next poll already 30 minutes overdue
        assertThat(LookupTableRepo.ttlSeconds(metadata(NOW.minusMinutes(90), "PT1H"))).isNull();
    }

    @Test
    void operatorOwnedTableUsesFlatRefreshDuration() {
        // no supplier (lastRefresh null) -> flat sliding TTL equal to refreshDuration
        assertThat(LookupTableRepo.ttlSeconds(metadata(null, "PT1H"))).isEqualTo(60 * 60);
    }

    @Test
    void pullThroughTableIsNotCacheable() {
        // pull-through fetches from the supplier on every lookup, so caching is suppressed even with a refreshDuration
        assertThat(LookupTableRepo.ttlSeconds(metadata(NOW.minusMinutes(20), "PT1H", true))).isNull();
    }
}
