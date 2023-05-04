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
package org.deltafi.actionkit.action.content;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.ActionKitException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

@Slf4j
public class ActionContent {
    @Getter
    protected final ContentReference contentReference;
    @Getter
    @Setter
    protected String name;
    protected final ContentStorageService contentStorageService;

    /**
     * Constructs a new {@code ActionContent} instance with the specified name, content reference,
     * and content storage service.
     * @param name the filename of the content
     * @param contentReference the content reference containing the actual content data
     * @param contentStorageService the content storage service used for loading the content
     */
    public ActionContent(String name, ContentReference contentReference, ContentStorageService contentStorageService) {
        this.contentReference = contentReference;
        this.name = name;
        this.contentStorageService = contentStorageService;
    }

    /**
     * Creates a new {@code ActionContent} instance that is a deep copy of this instance.
     * This includes copying the name, content reference, and content storage service.
     *
     * @return a new {@code ActionContent} instance that is a copy of this instance
     */
    public ActionContent copy() {
        return new ActionContent(name, contentReference.copy(), contentStorageService);
    }

    /**
     * Creates a new {@code ActionContent} instance representing a subset of this content
     * with the specified offset and size, using the same name and media type as this instance.
     *
     * @param offset the starting offset of the subcontent
     * @param size   the size of the subcontent
     * @return a new {@code ActionContent} instance representing the specified subcontent
     */
    public ActionContent subcontent(long offset, long size) {
        return subcontent(offset, size, name);
    }

    /**
     * Creates a new {@code ActionContent} instance representing a subset of this content
     * with the specified offset, size, and name, using the same media type as this instance.
     *
     * @param offset the starting offset of the subcontent
     * @param size   the size of the subcontent
     * @param name   the name of the new subcontent
     * @return a new {@code ActionContent} instance representing the specified subcontent
     */
    public ActionContent subcontent(long offset, long size, String name) {
        return subcontent(offset, size, name, contentReference.getMediaType());
    }

    /**
     * Creates a new {@code ActionContent} instance representing a subset of this content
     * with the specified offset, size, name, and media type.
     *
     * @param offset    the starting offset of the subcontent
     * @param size      the size of the subcontent
     * @param name      the name of the new subcontent
     * @param mediaType the media type of the new subcontent
     * @return a new {@code ActionContent} instance representing the specified subcontent
     */
    public ActionContent subcontent(long offset, long size, String name, String mediaType) {
        return new ActionContent(name, contentReference.subreference(offset, size, mediaType), contentStorageService);
    }

    /**
     * Retrieves the size of this {@code ActionContent} instance.
     *
     *  @return the size of the content
     */
    public long getSize() {
        return contentReference.getSize();
    }

    /**
     * Load the content as a byte array.
     *
     * @return the content
     */
    @SuppressWarnings("unused")
    public byte[] loadBytes() {
        byte[] content = null;
        try (InputStream contentInputStream = loadInputStream()) {
            content = contentInputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to close content input stream", e);
        }
        return content;
    }

    /**
     * Load the content as a String.
     *
     * @return the content as a String, or null if an error occurred while loading
     */
    @SuppressWarnings("unused")
    public String loadString() {
        return loadString(Charset.defaultCharset());
    }

    /**
     * Loads the content of this object as a String using the specified charset.
     *
     * @param charset the charset to use for decoding the content bytes
     * @return the content as a String, or null if an error occurred while loading
     */
    @SuppressWarnings("unused")
    public String loadString(Charset charset) {
        byte[] content = null;
        try (InputStream contentInputStream = loadInputStream()) {
            content = contentInputStream.readAllBytes();
        } catch (IOException e) {
            log.warn("Unable to close content input stream", e);
        }

        if (content == null) {
            return null;
        }
        return new String(content, charset);
    }

    /**
     * Load the content as an InputStream.
     *
     * @return an InputStream for the loaded content
     */
    public InputStream loadInputStream() {
        try {
            return contentStorageService.load(contentReference);
        } catch (ObjectStorageException e) {
            throw new ActionKitException("Failed to load content from storage", e);
        }
    }

    /**
     * Retrieves the media type of this {@code ActionContent} instance.
     *
     * @return the media type of the content
     */
    public String getMediaType() {
        return contentReference.getMediaType();
    }

    /**
     * Sets the media type of this {@code ActionContent} instance.
     *
     * @param mediaType the media type to set for the content
     */
    public void setMediaType(String mediaType) {
        contentReference.setMediaType(mediaType);
    }

    /**
     * Prepends the content of another {@code ActionContent} instance to this instance.
     * The content segments of the other instance are inserted at the beginning of this instance's content segments.
     *
     * @param other the {@code ActionContent} instance whose content segments should be prepended to this instance
     */
    @SuppressWarnings("unused")
    public void prepend(ActionContent other) {
        contentReference.getSegments().addAll(0, other.contentReference.getSegments());
    }

    /**
     * Appends the content of another {@code ActionContent} instance to this instance.
     * The content segments of the other instance are added to the end of this instance's content segments.
     *
     * @param other the {@code ActionContent} instance whose content segments should be appended to this instance
     */
    public void append(ActionContent other) {
        contentReference.getSegments().addAll(other.contentReference.getSegments());
    }
}
