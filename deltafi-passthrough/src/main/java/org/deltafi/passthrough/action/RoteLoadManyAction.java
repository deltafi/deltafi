/**
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

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.load.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteLoadParameters;
import org.deltafi.passthrough.util.RandSleeper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
public class RoteLoadManyAction extends LoadAction<RoteLoadParameters> {

    public RoteLoadManyAction() {
        super("Load a null value into the configured domains. Pass each content through as received as a child");
    }

    @Override
    public LoadResultType load(@NotNull ActionContext context, @NotNull RoteLoadParameters params, @NotNull LoadInput input) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        LoadManyResult loadManyResult = new LoadManyResult(context);

        input.getContentList().stream()
                .map(content -> buildLoadResult(context, content, params.getDomains()))
                .forEach(loadManyResult::add);

        return loadManyResult;
    }

    LoadResult buildLoadResult(ActionContext context, ActionContent content, List<String> domains) {
        LoadResult loadResult = new LoadResult(context, List.of(content));
        domains.forEach(domain -> loadResult.addDomain(domain, null, MediaType.TEXT_PLAIN));
        return loadResult;
    }

}