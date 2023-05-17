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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.ReinjectResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.actionkit.action.parameters.ReinjectParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@SuppressWarnings("unused")
public class SplitterLoadAction extends LoadAction<ReinjectParameters> {

    public SplitterLoadAction() {
        super("Splits content into multiple parts");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context,
                               @NotNull ReinjectParameters params,
                               @NotNull LoadInput input) {
        ReinjectResult result = new ReinjectResult(context);

        for (ActionContent content : input.getContentList()) {
            result.addChild(content.getName(),
                    params.getReinjectFlow(),
                    Collections.singletonList(content),
                    input.getMetadata());
        }

        return result;
    }
}
