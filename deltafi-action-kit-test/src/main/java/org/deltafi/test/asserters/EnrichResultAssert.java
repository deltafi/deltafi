/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.test.asserters;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.common.types.Enrichment;

import java.util.function.Predicate;

/**
 * Assertions for EnrichResults
 */
public class EnrichResultAssert extends AnnotationResultAssert<EnrichResultAssert, EnrichResult> {
    public EnrichResultAssert(EnrichResult enrichResult) {
        super(enrichResult, EnrichResultAssert.class);
    }

    /**
     * Create a new EnrichResultAssert with the given result
     * @param enrichResult to verify
     * @return new EnrichResultAssert
     */
    public static EnrichResultAssert assertThat(EnrichResult enrichResult) {
        return new EnrichResultAssert(enrichResult);
    }

    /**
     * Verify that at least one Enrichment in the results enrichment list
     * satisfies the given predicate
     * @param enrichmentMatcher predicate used to find the matching enrichment
     * @return this
     */
    public EnrichResultAssert hasEnrichmentMatching(Predicate<Enrichment> enrichmentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).anyMatch(enrichmentMatcher);
        return myself;
    }

    /**
     * Verify the enrichment list of the result contains an enrichment with
     * the given name, value and mediaType
     * @param name of the expected enrichment
     * @param value of the expected enrichment
     * @param mediaType of the expected enrichment
     * @return this
     */
    public EnrichResultAssert hasEnrichment(String name, String value, String mediaType) {
        return hasEnrichment(new Enrichment(name, value, mediaType));
    }

    /**
     * Verify the enrichment list of the result contains the given enrichment
     * @param enrichment to search for
     * @return this
     */
    public EnrichResultAssert hasEnrichment(Enrichment enrichment) {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).contains(enrichment);
        return this;
    }

    /**
     * Verify the enrichment list of the result contains exactly all the enrichment objects passed in
     * @param enrichment zero or more enrichment objects that should be in the enrichment list of the result
     * @return this
     */
    public EnrichResultAssert enrichmentIsEqualTo(Enrichment ... enrichment) {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).containsExactly(enrichment);
        return this;
    }

    /**
     * Verify the enrichment list of the result is empty
     * @return this
     */
    public EnrichResultAssert enrichmentIsEmpty() {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).isEmpty();
        return this;
    }
}
