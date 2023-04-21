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

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.ExpectedContentException;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Slf4j
public abstract class ContentListInput {
    private ActionContext actionContext;
    private List<Content> contentList;

    /**
     * Load a content reference from the content storage service as a byte array
     * @param contentReference Reference to content to be loaded
     * @return a byte array for the loaded content
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    @SuppressWarnings("unused")
    private byte[] loadContent(ContentReference contentReference) throws ObjectStorageException {
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
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    private InputStream loadContentAsInputStream(ContentReference contentReference) throws ObjectStorageException {
        return actionContext.getContentStorageService().load(contentReference);
    }

    /** Return the number of files in the contentList
     *
     * @return the number of files
     */
    public int contentListSize() {
        return contentList.size();
    }

    /**
     * Retrieve the Content object at the specified index.
     *
     * @param index the index of the desired Content object
     * @return the Content object at the specified index
     * @throws ArrayIndexOutOfBoundsException if the specified index is out of range
     */
    private Content contentAtIndex(int index) {
        if (contentList.size() > index) {
            return contentList.get(index);
        } else {
            throw new ArrayIndexOutOfBoundsException("Attempted to retrieve content at index " + index + " but only " + contentList.size() + " elements are present");
        }
    }

    /**
     * Load the first content as a ContentStream.
     *
     * @return a ContentStream for the loaded content at index 0
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    public ContentStream loadContentStream() throws ObjectStorageException {
        return loadContentStream(0);
    }

    /**
     * Load the content at the specified index as a ContentStream.
     *
     * @param index the index of the content to be loaded
     * @return a ContentStream for the loaded content
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    public ContentStream loadContentStream(int index) throws ObjectStorageException {
        Content content = contentAtIndex(index);
        return new ContentStream(loadContentAsInputStream(content.getContentReference()), content.getName(), content.getContentReference().getMediaType());
    }

    /**
     * Load the first content as a ContentBytes.
     *
     * @return a ContentBytes for the loaded content at index 0
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    public ContentBytes loadContentBytes() throws ObjectStorageException {
        return loadContentBytes(0);
    }

    /**
     * Load the content at the specified index as a ContentBytes.
     *
     * @param index the index of the content to be loaded
     * @return a ContentBytes for the loaded content
     * @throws ObjectStorageException when the load from the content storage service fails
     */
    public ContentBytes loadContentBytes(int index) throws ObjectStorageException {
        Content content = contentAtIndex(index);
        return new ContentBytes(loadContent(content.getContentReference()), content.getName(), content.getContentReference().getMediaType());
    }

    /**
     * Returns true if the ContentList has any content, false otherwise.
     * @return true if the ContentList has any content, false otherwise.
     */
    public boolean hasContent() {
        return !contentList.isEmpty();
    }

    /**
     * Getter for contentList
     * @return the contentList
     */
    public List<Content> getContentList() {
        return contentList;
    }

    /**
     * Returns the Content object at the given index.
     * @param index the index of the Content object to retrieve.
     * @return the Content object at the given index.
     */
    public Content contentAt(int index) {
        return contentList.get(index);
    }

    /**
     * Returns the first Content object in the ContentList.
     * @return the first Content object in the ContentList.
     * @throws ExpectedContentException if the ContentList is empty.
     */
    public Content firstContent() {
        if (!hasContent()) {
            throw new ExpectedContentException();
        }
        return contentAt(0);
    }
}
