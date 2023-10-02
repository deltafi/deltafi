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
package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized result class for LOAD actions that reinjects one or more DeltaFiles into child flows. Each child added to
 * this result object will be ingressed as a new DeltaFile on the specified flow.
 *
 * DEPRECATED - use org.deltafi.actionkit.action.ReinjectResult
 */
@Deprecated
@Getter
@EqualsAndHashCode(callSuper = true)
public class ReinjectResult extends org.deltafi.actionkit.action.ReinjectResult {
    /**
     * @param context Execution context for the current action
     */
    public ReinjectResult(@NotNull ActionContext context) {
        super(context);
    }
}
