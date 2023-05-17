/*
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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SaveManyContent;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.*;

/**
 * Specialization of the Result base class that allows metadata and content to be collected in the result.
 * <p>
 * This class is extended for Load and Transform results
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public abstract class DataAmendedResult extends MetadataAmendedResult {
    protected List<ActionContent> content = new ArrayList<>();

    /**
     * @param context Action context
     */
    public DataAmendedResult(ActionContext context) {
        super(context);
    }

    /**
     * Add a Content object to the list of content in the result
     * @param contentItem Content object to add to the result
     */
    @SuppressWarnings("unused")
    public void addContent(@NotNull ActionContent contentItem) {
        content.add(contentItem);
    }

    /**
     * Add a list of Content objects to the list of content in the result
     * @param contentList Content object to add to the result
     */
    @SuppressWarnings("unused")
    public void addContent(@NotNull List<ActionContent> contentList) {
        content.addAll(contentList);
    }

    /**
     * Save content to content storage and attach to the result
     * @param content String content to store.  The entire string will be stored in content storage
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public void saveContent(String content, String name, String mediaType) {
	saveContent(content.getBytes(), name, mediaType);
    }

    /**
     * Save content to content storage and attach to the result
     * @param bytes Byte array of content to store.  The entire byte array will be stored in content storage
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public void saveContent(byte[] bytes, String name, String mediaType) {
        try {
            Content content = context.getContentStorageService().save(context.getDid(), bytes, name, mediaType);
            addContent(new ActionContent(content, context.getContentStorageService()));
        } catch(ObjectStorageException e) {
            throw new ActionKitException("Failed to store content " + name, e);
        }
    }

    /**
     * Save content to content storage and attach to the result
     * @param stream InputStream of content to store.  The entire stream will be read into content storage, and the
     *                stream may be closed by underlying processors after execution
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    public void saveContent(InputStream stream, String name, @SuppressWarnings("SameParameterValue") String mediaType) {
        try {
            Content content = context.getContentStorageService().save(context.getDid(), stream, name, mediaType);
            addContent(new ActionContent(content, context.getContentStorageService()));
        } catch(ObjectStorageException e) {
            throw new ActionKitException("Failed to store content " + name, e);
        }
    }

    /**
     * Save multiple pieces of content to content storage and attach to the result
     * @param saveManyContentList a list of SaveManyContent objects containing the file name, media type, and bytes that need to be stored for each content
     */
    public void saveContent(List<SaveManyContent> saveManyContentList) {
        try {
            content.addAll(ContentConverter.convert(
                    context.getContentStorageService().saveMany(context.getDid(), saveManyContentList),
                    context.getContentStorageService()));
        } catch(ObjectStorageException e) {
            throw new ActionKitException("Failed to store saveManyContentList", e);
        }
    }

    protected List<org.deltafi.common.types.Content> contentList() {
        return content.stream().map(ContentConverter::convert).toList();
    }
}
