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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class HttpEgressActionBase<P extends HttpEgressParameters> extends EgressAction<P> {

    @Autowired
    protected HttpService httpPostService;

    public HttpEgressActionBase(String description) {
        super(description);
    }

    @SuppressWarnings("BusyWait")
    public EgressResultType egress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input) {
        int tries = 0;

        while (true) {
            EgressResultType result = doEgress(context, params, input);
            tries++;

            if (result instanceof ErrorResult) {
                if (tries > params.getRetryCount()) {
                    return result;
                } else {
                    log.error("Retrying HTTP POST after error: " + ((ErrorResult) result).getErrorCause() + " (retry " + tries + "/" + params.getRetryCount() + ")");
                    try {
                        Thread.sleep(params.getRetryDelayMs());
                    } catch (InterruptedException ignored) {}
                }
            } else {
                return result;
            }
        }
    }

    abstract protected EgressResultType doEgress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input);

    public Map<String, String> buildHeadersMap(String did, String sourceFilename, String filename, String ingressFlow, String egressFlow, Map<String, String> metadata) {
        Map<String, String> headersMap = new HashMap<>();
        if (metadata != null) {
            headersMap.putAll(metadata);
        }
        headersMap.put("did", did);
        headersMap.put("ingressFlow", ingressFlow);
        headersMap.put("flow", egressFlow);
        headersMap.put("originalFilename", sourceFilename);
        headersMap.put("filename", filename);

        return headersMap;
    }

}
