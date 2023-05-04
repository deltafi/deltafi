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

import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.FormatResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteParameters;
import org.deltafi.passthrough.util.RandSleeper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoteFormatAction extends FormatAction<RoteParameters> {
    public RoteFormatAction() {
        super("Format the first result created by the load action with no transformation");
    }

    public FormatResultType format(@NotNull ActionContext context, @NotNull RoteParameters parameters, @NotNull FormatInput input) {
        RandSleeper.sleep(parameters.getMinRoteDelayMS(), parameters.getMaxRoteDelayMS());

        return new FormatResult(context, input.getContentList().get(0));
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }

    @Override
    public List<String> getRequiresEnrichments() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
