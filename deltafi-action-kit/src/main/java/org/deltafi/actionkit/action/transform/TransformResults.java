/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized result class for TRANSFORM actions that splits the DeltaFile into children. Each child added to this
 * result object will continue along the originating flow.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class TransformResults extends Result<TransformResults> implements TransformResultType {
    final List<ChildTransformResult> childResults = new ArrayList<>();

    /**
     * @param context Execution context for the current action
     */
    public TransformResults(@NotNull ActionContext context) {
        super(context, ActionEventType.TRANSFORM);
    }

    /**
     * Add a new child to the results
     *
     * @param childResult The result to write to the new file
     */
    public void add(@NotNull ChildTransformResult childResult) {
        childResults.add(childResult);
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setTransform(childResults.stream()
                .map(ChildTransformResult::toChildTransformEvent)
                .toList());
        return event;
    }
}
