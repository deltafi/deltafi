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
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
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

    /**
     * Save content to content storage and attach to the result
     * @param content Byte array of content to store.  The entire byte array will be stored in content storage
     * @param mediaType Media type for the content being stored
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    @SuppressWarnings("unused")
    public void saveContent(byte[] content, String mediaType) throws ObjectStorageException {
        contentReference = context.getContentStorageService().save(context.getDid(), content, mediaType);
    }

    /**
     * Save content to content storage and attach to the result
     * @param content InputStream of content to store.  The entire stream will be read into content storage, and the
     *                stream may be closed by underlying processors after execution
     * @param mediaType Media type for the content being stored
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    @SuppressWarnings("unused")
    public void saveContent(InputStream content, @SuppressWarnings("SameParameterValue") String mediaType) throws ObjectStorageException {
        contentReference = context.getContentStorageService().save(context.getDid(), content, mediaType);
    }

    @Override
    protected final ActionEventType actionEventType() {
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
