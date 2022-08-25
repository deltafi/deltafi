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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.load.MultipartLoadAction;
import org.deltafi.actionkit.action.load.SplitResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.Content;
import org.deltafi.core.parameters.SplitterLoadParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
@Action
public class SplitterLoadAction extends MultipartLoadAction<SplitterLoadParameters> {

    public SplitterLoadAction() {
        super(SplitterLoadParameters.class);
    }

    @Override
    public Result load(@NotNull ActionContext context,
                       @NotNull SplitterLoadParameters params,
                       @NotNull SourceInfo sourceInfo,
                       @NotNull List<Content> contentList,
                       @NotNull Map<String, String> metadata) {
        SplitResult result = new SplitResult(context);

        for (Content content : contentList) {
            result.addChild(content.getName(),
                    params.getReinjectFlow(),
                    sourceInfo.getMetadata(),
                    Collections.singletonList(content));
        }

        return result;
    }
}
