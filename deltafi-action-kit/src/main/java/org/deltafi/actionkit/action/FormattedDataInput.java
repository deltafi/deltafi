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
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.FormattedData;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Slf4j
public abstract class FormattedDataInput {
    private ActionContext actionContext;
    private FormattedData formattedData;

    /**
     * Load a content reference from the content storage service as a byte array
     * @param contentReference Reference to content to be loaded
     * @return a byte array for the loaded content
     */
    @SuppressWarnings("unused")
    private byte[] loadContent(ContentReference contentReference) {
        byte[] content = null;
        try (InputStream contentInputStream = loadContentAsInputStream(contentReference)) {
            content = contentInputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to close content input stream", e);
        }
        return content;
    }

    /**
     * Load a content reference from the content storage service as an InputStream
     * @param contentReference Reference to content to be loaded
     * @return an InputStream for the loaded content
     */
    private InputStream loadContentAsInputStream(ContentReference contentReference) {
        try {
            return actionContext.getContentStorageService().load(contentReference);
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
        return loadContentAsInputStream(formattedData.getContentReference());
    }

    /**
     * Load the content associated with the formatted data as a byte array.
     *
     * @return a byte array for the loaded content
     */
    @SuppressWarnings("unused")
    public byte[] loadFormattedDataBytes() {
        return loadContent(formattedData.getContentReference());
    }

    /**
     * Get the media type associated with the formatted data.
     *
     * @return a string representing the media type
     */
    @JsonIgnore
    public String getMediaType() {
        return formattedData.getContentReference().getMediaType();
    }

    /**
     * Get the size of the formatted data.
     *
     * @return a long representing the size of the formatted data in bytes
     */
    @JsonIgnore
    public long getFormattedDataSize() {
        return formattedData.getContentReference().getSize();
    }

    /**
     * Get the filename associated with the formatted data.
     *
     * @return a string representing the filename
     */
    @JsonIgnore
    public String getFilename() {
        return formattedData.getFilename();
    }

    /**
     * Get the metadata associated with the formatted data.
     *
     * @return a map containing key-value pairs representing the metadata
     */
    @JsonIgnore
    public Map<String, String> getMetadata() {
        return formattedData.getMetadata();
    }
}
