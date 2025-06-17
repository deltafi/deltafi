/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.egress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;

@Component
@Slf4j
public class DeltaFiEgress extends HttpEgressBase<DeltaFiEgressParameters> {
    public static final String INGRESS_URL_PATH = "/api/v2/deltafile/ingress";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final String localIngressUrl;

    public DeltaFiEgress(OkHttpClient httpClient, @Value("${CORE_URL:Unknown}") String coreUrl) {
        super("Egresses to local or remote DeltaFi.", httpClient);
        this.localIngressUrl = coreUrl + INGRESS_URL_PATH;
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull DeltaFiEgressParameters params, @NotNull EgressInput input) {
        if (!params.isSendLocal() && StringUtils.isEmpty(params.getUrl())) {
            return new ErrorResult(context, "URL cannot be determined",
                    "Must specify url or sendLocal to TRUE");
        }

        if (context.getDataSource().equals(params.getFlow())) {
            return new ErrorResult(context, "Circular egress detected",
                    "Cannot egress to the same flow as the data source");
        }

        if (params.isSendLocal() && params.getUrl() == null) {
            params.setUrl(localIngressUrl);
        }

        return doEgress(context, params, HttpRequestMethod.POST, input);
    }

    @Override
    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull DeltaFiEgressParameters params,
                                               @NotNull EgressInput input) throws JsonProcessingException {
        Map<String, String> metadataMap = new TreeMap<>(input.getMetadata());
        metadataMap.put("originalDid", context.getDid().toString());
        metadataMap.put("originalSystem", context.getSystemName());

        Map<String, String> headersMap = new TreeMap<>(Map.of(
                "Filename", input.getContent().getName(),
                "Metadata", OBJECT_MAPPER.writeValueAsString(metadataMap)));

        if (params.getFlow() != null) {
            headersMap.put("DataSource", params.getFlow());
        }
        if (params.isSendLocal()) {
            headersMap.put(DeltaFiConstants.USER_NAME_HEADER, context.getHostname());
            headersMap.put(DeltaFiConstants.PERMISSIONS_HEADER, "DeltaFileIngress");
        }

        log.debug(headersMap.toString());
        return headersMap;
    }
}
