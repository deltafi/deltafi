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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressActionParameters;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.FormattedData;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Action
public abstract class HttpEgressActionBase<P extends HttpEgressParameters> extends EgressAction<P> {

    @Autowired
    protected HttpService httpPostService;

    public HttpEgressActionBase(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @SuppressWarnings("BusyWait")
    public Result egress(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        int tries = 0;

        while (true) {
            Result result = doEgress(context, params, sourceInfo, formattedData);
            tries++;

            if (result instanceof ErrorResult) {
                if (tries > params.getRetryCount()) {
                    return result;
                } else {
                    log.error("Retrying HTTP POST after error: " + ((ErrorResult) result).getErrorSummary());
                    try {
                        Thread.sleep(params.getRetryDelayMs());
                    } catch (InterruptedException ignored) {}
                }
            } else {
                return result;
            }
        }
    }

    abstract protected Result doEgress(@NotNull ActionContext context, @NotNull P params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData);


    public Map<String, String> buildHeadersMap(String did, SourceInfo sourceInfo, FormattedData formattedData, EgressActionParameters params) {
        Map<String, String> headersMap = new HashMap<>();
        if (formattedData.getMetadata() != null) {
            formattedData.getMetadata().forEach(pair -> headersMap.put(pair.getKey(), pair.getValue()));
        }
        headersMap.put("did", did);
        headersMap.put("ingressFlow", sourceInfo.getFlow());
        headersMap.put("flow", params.getEgressFlow());
        headersMap.put("originalFilename", sourceInfo.getFilename());
        headersMap.put("filename", formattedData.getFilename());

        return headersMap;
    }

}
