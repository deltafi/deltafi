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
package org.deltafi.actionkit.action.transform;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.DataAmendedResult;
import org.deltafi.actionkit.action.HasAnnotations;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.TransformEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Specialized result class for TRANSFORM actions
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class TransformResult extends DataAmendedResult implements HasAnnotations, TransformResultType {
    private final Map<String, String> annotations = new HashMap<>();

    /**
     * @param context Context of executing action
     */
    public TransformResult(@NotNull ActionContext context) {
        super(context, ActionEventType.TRANSFORM);
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setTransform(TransformEvent.builder()
                .content(contentList())
                .metadata(metadata)
                .deleteMetadataKeys(deleteMetadataKeys)
                .annotations(annotations)
                .build());
        return event;
    }
}
