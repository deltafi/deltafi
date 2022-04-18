package org.deltafi.core.action;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressActionParameters;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.core.domain.api.types.ActionContext;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.types.FormattedData;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class HttpEgressActionBase<P extends HttpEgressParameters> extends EgressAction<P> {

    @Inject
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
                    log.error("Retrying HTTP POST after error: " + ((ErrorResult) result).getErrorCause());
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
