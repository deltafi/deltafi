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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.TransformEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Each child transform result added in a TransformResults results
 * in the creation of a new child DeltaFile with the given deltaFileName.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unused")
public class ChildTransformResult extends TransformResult {
    private final String deltaFileName;

    /**
     * Create a new ChildTransformResult
     * @param parentContext Parent context of executing action
     */
    public ChildTransformResult(@NotNull ActionContext parentContext) {
        this(parentContext, (String) null);
    }

    /**
     * Create a new ChildTransformResult
     * @param parentContext Parent context of executing action
     * @param deltaFileName The name to use as the DeltaFileName in the child DeltaFile
     */
    public ChildTransformResult(@NotNull final ActionContext parentContext, final String deltaFileName) {
        super(parentContext.childContext());
        this.deltaFileName = deltaFileName;
    }

    /**
     * Create a new ChildTransformResult
     * @param parentContext Parent context of executing action
     * @param content List of content objects to be processed with the execution result
     */
    public ChildTransformResult(@NotNull ActionContext parentContext, @NotNull List<ActionContent> content) {
        this(parentContext.childContext(), null, content);
    }

    /**
     * Create a new ChildTransformResult
     * @param parentContext Parent context of executing action
     * @param deltaFileName The name to use as the DeltaFileName in the child DeltaFile
     * @param content List of content objects to be processed with the execution result
     */
    public ChildTransformResult(@NotNull ActionContext parentContext, String deltaFileName, @NotNull List<ActionContent> content) {
        super(parentContext.childContext(), content);
        this.deltaFileName = deltaFileName;
    }

    public final TransformEvent toChildTransformEvent() {
        TransformEvent event = toTransformEvent();
        event.setName(this.deltaFileName);
        return event;
    }

}
