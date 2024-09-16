/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.io.Writer;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SaveManyContent;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Result that may include changes to content, annotations, or metadata
 */
@Getter
public abstract class ContentResult<T extends Result<T>> extends MetadataResult<T> {
    @Setter
    protected List<ActionContent> content;

    protected final Map<String, String> annotations = new HashMap<>();

    public ContentResult(@NotNull ActionContext context, @NotNull ActionEventType actionEventType) {
        this(context, actionEventType, new ArrayList<>());
    }

    public ContentResult(@NotNull ActionContext context, @NotNull ActionEventType actionEventType, @NotNull List<ActionContent> content) {
        super(context, actionEventType);
        this.content = content;
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
     * @param content Content object to add to the result
     */
    @SuppressWarnings("unused")
    public void addContent(@NotNull List<ActionContent> content) {
        this.content.addAll(content);
    }

    /**
     * Save content to content storage and attach to the result
     * @param content   String content to store.  The entire string will be stored in content storage
     * @param name      the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public void saveContent(@NotNull String content, @NotNull String name, @NotNull String mediaType) {
        saveContent(content.getBytes(), name, mediaType);
    }

    /**
     * Save content to content storage and attach to the result
     * @param bytes     Byte array of content to store.  The entire byte array will be stored in content storage
     * @param name      the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public void saveContent(@NotNull byte[] bytes, @NotNull String name, @NotNull String mediaType) {
        addContent(ActionContent.saveContent(context, bytes, name, mediaType));
    }

    /**
     * Save content to content storage and attach to the result
     * @param stream InputStream of content to store.  The entire stream will be read into content storage, and the
     * stream may be closed by underlying processors after execution
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    public void saveContent(@NotNull InputStream stream, @NotNull String name, @NotNull String mediaType) {
        addContent(ActionContent.saveContent(context, stream, name, mediaType));
    }

    /**
     * Save content to content storage and attach to the result. This method calls the supplied Writer from a separate
     * thread to stream content to content storage.
     * @param writer a Writer that produces the content to be stored
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    public void saveContent(@NotNull Writer writer, @NotNull String name, @NotNull String mediaType) {
        addContent(ActionContent.saveContent(context, writer, name, mediaType));
    }

    /**
     * Save multiple pieces of content to content storage and attach to the result
     * @param saveManyContentList a list of SaveManyContent objects containing the file name, media type, and bytes that
     * need to be stored for each content
     */
    public void saveContent(@NotNull List<SaveManyContent> saveManyContentList) {
        try {
            content.addAll(ContentConverter.convert(
                    context.getContentStorageService().saveMany(context.getDid(), saveManyContentList),
                    context.getContentStorageService()));
        } catch (ObjectStorageException e) {
            throw new ActionKitException("Failed to store saveManyContentList", e);
        }
    }

    /**
     * @deprecated Use {@link ContentConverter#convert(List)} instead
     */
    @Deprecated
    public List<Content> content() {
        return ContentConverter.convert(content);
    }

    /**
     * Add annotation to this DeltaFile that will be made searchable.
     * Multiple entries can be added by repeatedly calling this method.
     * @param key that will be annotated
     * @param value value for the given key
     */
    @SuppressWarnings("unused")
    public void addAnnotation(@NotNull String key, @NotNull String value) {
        annotations.put(key, value);
    }

    /**
     * Add all the annotations in the given map to this Result. These entries will be searchable.
     * @param metadata map of entries that will be added to the annotations
     */
    @SuppressWarnings("unused")
    public void addAnnotations(@NotNull Map<String, String> metadata) {
        annotations.putAll(metadata);
    }
}
