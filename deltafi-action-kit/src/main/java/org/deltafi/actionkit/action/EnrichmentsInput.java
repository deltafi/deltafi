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
package org.deltafi.actionkit.action;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.deltafi.common.types.Enrichment;

import java.util.HashMap;
import java.util.Map;

/**
 * Action input that may include enrichments, domains, content, or metadata
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class EnrichmentsInput extends DomainsInput {
    @Builder.Default
    protected final Map<String, Enrichment> enrichments = new HashMap<>();

    /**
     * Returns the Enrichment object for the given enrichment name.
     * @param enrichmentName the name of the enrichment.
     * @return the Enrichment object for the given enrichment name.
     */
    public Enrichment enrichment(String enrichmentName) {
        return enrichments.get(enrichmentName);
    }
}
