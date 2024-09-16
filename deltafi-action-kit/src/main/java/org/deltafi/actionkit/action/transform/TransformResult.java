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
package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.TransformEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Specialized result class for TRANSFORM actions
 */
public class TransformResult extends ContentResult<TransformResult> implements TransformResultType {
    /**
     * @param context Context of executing action
     */
    public TransformResult(@NotNull ActionContext context) {
        super(context, ActionEventType.TRANSFORM);
    }

    /**
     * @param context Context of executing action
     * @param content List of content objects to be processed with the execution result
     */
    public TransformResult(@NotNull ActionContext context, @NotNull List<ActionContent> content) {
        super(context, ActionEventType.TRANSFORM, content);
    }

    public final TransformEvent toTransformEvent() {
        return TransformEvent.builder()
                .content(ContentConverter.convert(content))
                .annotations(annotations)
                .metadata(metadata)
                .deleteMetadataKeys(deleteMetadataKeys)
                .build();
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setTransform(Collections.singletonList(toTransformEvent()));
        return event;
    }
}
