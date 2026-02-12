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
// ABOUTME: Represents a single ingressed item within an IngressResult.
// ABOUTME: Each item becomes a new child DeltaFile with its own content, metadata, and name.
package org.deltafi.actionkit.action.ingress;

import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Specialized result class for INGRESS actions
 */
@Getter
@Setter
public class IngressResultItem extends ContentResult<IngressResultItem> {
    String deltaFileName;

    /**
     * Create an IngressResultItem with a name for the child DeltaFile.
     * @param context execution context of the ingress action
     * @param deltaFileName the name to assign to the ingressed DeltaFile
     */
    public IngressResultItem(@NotNull ActionContext context, @NotNull String deltaFileName) {
        super(context.childContext(), ActionEventType.INGRESS);
        this.deltaFileName = deltaFileName;
    }

    /**
     * Convert this item to an IngressEventItem for the action event.
     * @return an IngressEventItem populated from this item's content, metadata, and annotations
     */
    public final IngressEventItem toIngressEventItem() {
        return IngressEventItem.builder()
                .did(context.getDid())
                .content(ContentConverter.convert(content))
                .metadata(metadata)
                .deltaFileName(deltaFileName)
                .annotations(annotations)
                .build();
    }
}
