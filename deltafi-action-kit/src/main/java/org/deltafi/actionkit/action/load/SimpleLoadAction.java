/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings("unused")
public abstract class SimpleLoadAction extends LoadAction<ActionParameters> {
    public SimpleLoadAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result load(@NotNull ActionContext context,
                             @NotNull ActionParameters params,
                             @NotNull SourceInfo sourceInfo,
                             @NotNull Content content,
                             @NotNull Map<String, String> metadata) {
        return load(context, sourceInfo, content, metadata);
    }

    public abstract Result load(@NotNull ActionContext context,
                                @NotNull SourceInfo sourceInfo,
                                @NotNull Content content,
                                @NotNull Map<String, String> metadata);
}
