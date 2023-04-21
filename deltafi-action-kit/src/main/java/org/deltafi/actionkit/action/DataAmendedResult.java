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
package org.deltafi.actionkit.action;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
public abstract class DataAmendedResult extends Result<DataAmendedResult> {
    protected List<Content> content = new ArrayList<>();
    protected Map<String, String> metadata = new HashMap<>();

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
        metadata.put(key, value);
    }

    /**
     * Add metadata by map
     * @param map Key-value pairs to add to metadata
     */
    @SuppressWarnings("unused")
    public void addMetadata(Map<String, String> map) {
        if (map != null) {
            metadata.putAll(map);
        }
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
     * Save content to content storage and attach to the result
     * @param content Byte array of content to store.  The entire byte array will be stored in content storage
     * @param name the content name
     * @param mediaType Media type for the content being stored
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    public void saveContent(byte[] content, String name, String mediaType) throws ObjectStorageException {
        ContentReference contentReference = context.getContentStorageService().save(context.getDid(), content, mediaType);
        addContent(new Content(name, contentReference));
    }

    /**
     * Save content to content storage and attach to the result
     * @param content InputStream of content to store.  The entire stream will be read into content storage, and the
     *                stream may be closed by underlying processors after execution
     * @param name the content name
     * @param mediaType Media type for the content being stored
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    public void saveContent(InputStream content, String name, @SuppressWarnings("SameParameterValue") String mediaType) throws ObjectStorageException {
        ContentReference contentReference = context.getContentStorageService().save(context.getDid(), content, mediaType);
        addContent(new Content(name, contentReference));
    }

    /**
     * Save multiple pieces of content to content storage and attach to the result
     * @param contentToBytes map of content objects to the bytes that need to be stored for the content
     * @throws ObjectStorageException when the content storage service fails to store content
     */
    public void saveContent(Map<Content, byte[]> contentToBytes) throws ObjectStorageException {
        context.getContentStorageService().saveMany(context.getDid(), contentToBytes);
    }
}
