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
package org.deltafi.actionkit.action.format;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.FormatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Specialized result class for FORMAT actions
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class FormatResult extends Result<FormatResult> implements FormatResultType {
    private final String filename;
    protected ContentReference contentReference;
    protected Map<String, String> metadata = new HashMap<>();

    /**
     * @param context Context of the executed action
     * @param filename File name of the formatted result content
     */
    public FormatResult(@NotNull ActionContext context, @NotNull String filename) {
        super(context);
        this.filename = filename;
    }

    /**
     * Add metadata by key and value
     * @param key Key for a metadata value
     * @param value A metadata value
     */
    @SuppressWarnings("unused")
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    /**
     * Add metadata by key/value map
     * @param map String pairs to add to metadata
     */
    @SuppressWarnings("unused")
    public void addMetadata(Map<String, String> map) {
        if (map != null) {
            metadata.putAll(map);
        }
    }

    /**
     * Add metadata by key/value map, prefixing each key with a fixed string
     * @param map String pairs to add to metadata
     * @param prefix String to prepend to each key before adding to metadata
     */
    @SuppressWarnings("unused")
    public void addMetadata(Map<String, String> map, String prefix) {
        if (map != null) {
            final String usePrefix = prefix != null ? prefix : "";
            map.forEach((key, value) -> metadata.put(usePrefix + key, value));
        }
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.FORMAT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setFormat(FormatEvent.newBuilder()
                .filename(filename)
                .contentReference(contentReference)
                .metadata(metadata)
                .build());
        return event;
    }
}
