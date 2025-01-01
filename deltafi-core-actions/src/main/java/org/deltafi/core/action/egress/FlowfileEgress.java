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
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.nifi.FlowFileInputStream;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.deltafi.common.nifi.ContentType.APPLICATION_FLOWFILE;

@Component
@Slf4j
public class FlowfileEgress extends HttpEgressBase<HttpEgressParameters> {
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    public FlowfileEgress(HttpService httpService) {
        super(String.format("Egresses content and attributes in a NiFi V1 FlowFile (%s)", APPLICATION_FLOWFILE),
                httpService);
    }

    @Override
    protected InputStream openInputStream(@NotNull ActionContext context, @NotNull EgressInput input)
            throws IOException {
        return FlowFileInputStream.create(input.getContent().loadInputStream(),
                StandardEgressHeaders.buildMap(context, input), input.getContent().getSize(), executorService);
    }

    @Override
    protected String getMediaType(@NotNull EgressInput input) {
        return APPLICATION_FLOWFILE;
    }
}
