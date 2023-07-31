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

import org.deltafi.actionkit.action.load.LoadAction;
import org.deltafi.actionkit.action.load.LoadInput;
import org.deltafi.actionkit.action.load.LoadResult;
import org.deltafi.actionkit.action.load.LoadResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteLoadParameters;
import org.deltafi.passthrough.util.RandSleeper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;

@Component
public class RoteLoadAction extends LoadAction<RoteLoadParameters> {
    public RoteLoadAction() {
        super("Load a null value into the configured domains. Pass content through as received");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context, @NotNull RoteLoadParameters params, @NotNull LoadInput input) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        LoadResult result = new LoadResult(context, input.content());
        if (null != params.getDomains()) {
            params.getDomains().forEach(d -> result.addDomain(d, null, MediaType.TEXT_PLAIN));
        }
        return result;
    }
}
