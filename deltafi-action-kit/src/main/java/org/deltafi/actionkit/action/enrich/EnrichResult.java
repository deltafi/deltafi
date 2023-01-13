/**
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
package org.deltafi.actionkit.action.enrich;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.HasIndexedMetadata;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Specialized result class for ENRICH actions
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class EnrichResult extends Result<EnrichResult> implements HasIndexedMetadata, EnrichResultType {
    private final List<Enrichment> enrichments = new ArrayList<>();
    private final Map<String, String> indexedMetadata = new HashMap<>();

    /**
     * @param context Context of the executed action
     */
    public EnrichResult(@NotNull ActionContext context) {
        super(context);
    }

    /**
     * Apply an enrichment to the DeltaFile when processing the result of
     * this action.  Multiple enrichments can be applied by invoking this method
     * multiple times.
     * @param enrichmentName Name of enrichment being applied to the DeltaFile
     * @param value String value of the applied enrichment
     * @param mediaType Media type of the applied enrichment
     */
    @SuppressWarnings("unused")
    public void addEnrichment(@NotNull String enrichmentName, String value, @NotNull String mediaType) {
        enrichments.add(new Enrichment(enrichmentName, value, mediaType));
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.ENRICH;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setEnrich(EnrichEvent.newBuilder().enrichments(enrichments).indexedMetadata(indexedMetadata).build());
        return event;
    }
}