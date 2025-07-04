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

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.deltafi.common.nifi.ContentType.APPLICATION_FLOWFILE;

@Component
@Slf4j
public class FlowfileEgress extends HttpEgressBase<HttpEgressParameters> {
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    public FlowfileEgress(OkHttpClient httpClient) {
        super(String.format("Egresses content and metadata in a NiFi V1 FlowFile (%s).", APPLICATION_FLOWFILE),
                httpClient);
    }

    @Override
    protected RequestBody prepareRequestBody(ActionContext context, EgressInput input){
        return new FlowFileRequestBody(context, input, executorService);
    }

    @Override
    protected String getMediaType(@NotNull EgressInput input) {
        return APPLICATION_FLOWFILE;
    }
}
