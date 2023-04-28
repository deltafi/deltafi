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

import org.deltafi.actionkit.action.enrich.EnrichAction;
import org.deltafi.actionkit.action.enrich.EnrichInput;
import org.deltafi.actionkit.action.enrich.EnrichResult;
import org.deltafi.actionkit.action.enrich.EnrichResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteEnrichParameters;
import org.deltafi.passthrough.util.RandSleeper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class RoteEnrichAction extends EnrichAction<RoteEnrichParameters> {
    public RoteEnrichAction() {
        super("Populate enrichment with the parameterized key/value pairs");
    }

    public EnrichResultType enrich(@NotNull ActionContext context, @NotNull RoteEnrichParameters params, @NotNull EnrichInput input) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        EnrichResult result = new EnrichResult(context);
        if (null != params.getEnrichments()) {
            params.getEnrichments().forEach((k, v) -> result.addEnrichment(k, v, MediaType.TEXT_PLAIN));
        }

        if (null != params.getIndexedMetadata()) {
            result.addIndexedMetadata(params.getIndexedMetadata());
        }

        return result;
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
