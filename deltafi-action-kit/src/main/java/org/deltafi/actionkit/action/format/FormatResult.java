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
package org.deltafi.actionkit.action.format;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.ActionKitException;
import org.deltafi.actionkit.action.MetadataAmendedResult;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

/**
 * Specialized result class for FORMAT actions
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class FormatResult extends MetadataAmendedResult implements FormatResultType {
    private final ActionContent content;

    /**
     * @param context Context of the executed action
     * @param content formatted result content
     */
    public FormatResult(@NotNull ActionContext context, @NotNull ActionContent content) {
        super(context, ActionEventType.FORMAT);
        this.content = content;
    }

    /**
     * @param context Context of the executed action
     * @param content String content to store.  The entire String will be stored in content storage
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public FormatResult(@NotNull ActionContext context, @NotNull String content, @NotNull String name, @NotNull String mediaType) {
        super(context, ActionEventType.FORMAT);
        this.content = saveContent(content.getBytes(), name, mediaType);
    }

    /**
     * @param context Context of the executed action
     * @param content Byte array of content to store.  The entire byte array will be stored in content storage
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public FormatResult(@NotNull ActionContext context, @NotNull byte[] content, @NotNull String name, @NotNull String mediaType) {
        super(context, ActionEventType.FORMAT);
        this.content = saveContent(content, name, mediaType);
    }

    /**
     * @param context Context of the executed action
     * @param content InputStream of content to store.  The entire stream will be read into content storage, and the
     *                stream may be closed by underlying processors after execution
     * @param name the content name
     * @param mediaType Media type for the content being stored
     */
    @SuppressWarnings("unused")
    public FormatResult(@NotNull ActionContext context, @NotNull InputStream content, @NotNull String name, @NotNull String mediaType) {
        super(context, ActionEventType.FORMAT);
        this.content = saveContent(content, name, mediaType);
    }

    private ActionContent saveContent(byte[] bytes, String name, String mediaType) {
        try {
            Content content = context.getContentStorageService().save(context.getDid(), bytes, name, mediaType);
            return new ActionContent(content, context.getContentStorageService());
        } catch(ObjectStorageException e) {
            throw new ActionKitException("Failed to store content " + name, e);
        }
    }

    private ActionContent saveContent(InputStream stream, String name, String mediaType) {
        try {
            Content content = context.getContentStorageService().save(context.getDid(), stream, name, mediaType);
            return new ActionContent(content, context.getContentStorageService());
        } catch(ObjectStorageException e) {
            throw new ActionKitException("Failed to store content " + name, e);
        }
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setFormat(FormatEvent.newBuilder()
                .content(content.getContent())
                .metadata(metadata)
                .build());
        return event;
    }
}
