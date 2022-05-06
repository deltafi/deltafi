/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.api.types.KeyValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Specialization of the Result base class that allows metadata and content to be collected in the result.
 *
 * This class is extended for Load and Transform results
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public abstract class DataAmendedResult extends Result {
    protected List<Content> content = new ArrayList<>();
    protected List<KeyValue> metadata = new ArrayList<>();

    /**
     * @param context Action context
     */
    public DataAmendedResult(ActionContext context) {
        super(context);
    }

    /**
     * Add metadata by key and value Strings
     * @param key Metadata key to add
     * @param value Metadata value to add
     */
    public void addMetadata(String key, String value) {
        metadata.add(new KeyValue(key, value));
    }

    /**
     * Add metadata by map
     * @param map Key-value pairs to add to metadata
     */
    @SuppressWarnings("unused")
    public void addMetadata(@NotNull Map<String, String> map) {
        map.forEach(this::addMetadata);
    }

    /**
     * Add a Content object to the list of content in the result
     * @param contentItem Content object to add to the result
     */
    @SuppressWarnings("unused")
    public void addContent(@NotNull Content contentItem) {
        content.add(contentItem);
    }

    /**
     * Add a content reference to the list of content in the result
     * @param contentReference A content reference to be added to the result
     */
    @SuppressWarnings("unused")
    public void addContentReference(@NotNull ContentReference contentReference) {
        addContent(Content.newBuilder().contentReference(contentReference).build());
    }
}
