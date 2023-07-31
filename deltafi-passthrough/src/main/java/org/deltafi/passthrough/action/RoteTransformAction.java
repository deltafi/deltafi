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
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteParameters;
import org.deltafi.passthrough.util.RandSleeper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class RoteTransformAction extends TransformAction<RoteParameters> {
    public RoteTransformAction() {
        super("NOOP passthrough Action");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull RoteParameters params, @NotNull TransformInput input) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        TransformResult result = new TransformResult(context);
        result.setContent(input.content());
        result.addMetadata(input.getMetadata());
        return result;
    }
}
