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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.MissingMetadataException;
import org.deltafi.common.types.Content;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Slf4j
public abstract class FormattedDataInput {
    private ActionContext actionContext;
    private Content content;
    @Getter
    @Setter
    private Map<String, String> metadata;

    /**
     * Load content from the content storage service as a byte array
     * @param content content to be loaded
     * @return a byte array for the loaded content
     */
    @SuppressWarnings("unused")
    private byte[] loadContent(Content content) {
        byte[] bytes = null;
        try (InputStream contentInputStream = loadContentAsInputStream(content)) {
            bytes = contentInputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to close content input stream", e);
        }
        return bytes;
    }

    /**
     * Load content from the content storage service as an InputStream
     * @param content content to be loaded
     * @return an InputStream for the loaded content
     */
    private InputStream loadContentAsInputStream(Content content) {
        try {
            return actionContext.getContentStorageService().load(content);
        } catch (ObjectStorageException e) {
            throw new ActionKitException("Failed to load content from storage", e);
        }
    }

    /**
     * Load the content associated with the formatted data as an InputStream.
     *
     * @return an InputStream for the loaded content
     */
    public InputStream loadFormattedDataStream() {
        return loadContentAsInputStream(content);
    }

    /**
     * Load the content associated with the formatted data as a byte array.
     *
     * @return a byte array for the loaded content
     */
    @SuppressWarnings("unused")
    public byte[] loadFormattedDataBytes() {
        return loadContent(content);
    }

    /**
     * Get the media type associated with the formatted data.
     *
     * @return a string representing the media type
     */
    @JsonIgnore
    public String getMediaType() {
        return content.getMediaType();
    }

    /**
     * Get the size of the formatted data.
     *
     * @return a long representing the size of the formatted data in bytes
     */
    @JsonIgnore
    public long getFormattedDataSize() {
        return content.getSize();
    }

    /**
     * Get the filename associated with the formatted data.
     *
     * @return a string representing the filename
     */
    @JsonIgnore
    public String getFilename() {
        return content.getName();
    }


    /**
     * Returns the value of the formatted metadata for the given key.
     * @param key the key for the metadata.
     * @return the value of the metadata for the given key.
     * @throws MissingMetadataException if the key is not found in the source metadata map.
     */
    public String metadata(String key) {
        if (metadata.containsKey(key)) {
            return metadata.get(key);
        } else {
            throw new MissingMetadataException(key);
        }
    }

    /**
     * Returns the value of the formatted metadata for the given key or a default value if the key is not found.
     * @param key the key for the metadata.
     * @param defaultValue the default value to return if the key is not found.
     * @return the value of the metadata for the given key or the default value if the key is not found.
     */
    public String metadata(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }
}
