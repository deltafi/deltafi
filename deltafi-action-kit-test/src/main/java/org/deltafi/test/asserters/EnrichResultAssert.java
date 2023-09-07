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

public class EnrichResultAssert extends AnnotationResultAssert<EnrichResultAssert, EnrichResult> {
    public EnrichResultAssert(EnrichResult enrichResult) {
        super(enrichResult, EnrichResultAssert.class);
    }

    public EnrichResultAssert hasEnrichmentMatching(Predicate<Enrichment> enrichmentMatcher) {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).anyMatch(enrichmentMatcher);
        return myself;
    }

    public EnrichResultAssert hasEnrichment(String name, String value, String mediaType) {
        return hasEnrichment(new Enrichment(name, value, mediaType));
    }

    public EnrichResultAssert hasEnrichment(Enrichment enrichment) {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).contains(enrichment);
        return this;
    }

    public EnrichResultAssert enrichmentIsEqualTo(Enrichment ... enrichment) {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).containsExactly(enrichment);
        return this;
    }

    public EnrichResultAssert enrichmentIsEmpty() {
        isNotNull();
        Assertions.assertThat(actual.getEnrichments()).isEmpty();
        return this;
    }
}
