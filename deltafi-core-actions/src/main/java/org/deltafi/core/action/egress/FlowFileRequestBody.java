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

import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.nifi.FlowFileInputStream;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static org.deltafi.common.nifi.ContentType.APPLICATION_FLOWFILE;

@RequiredArgsConstructor
public class FlowFileRequestBody extends RequestBody {

    private final ActionContext context;
    private final EgressInput input;
    private final ExecutorService executorService;

    @Override
    public MediaType contentType() {
        return MediaType.parse(APPLICATION_FLOWFILE);
    }

    @Override
    public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
        try (Source source = Okio.source(FlowFileInputStream.create(input.getContent().loadInputStream(),
                StandardEgressHeaders.buildMap(context, input), input.getContent().getSize(), executorService))) {
            bufferedSink.writeAll(source);
        }
    }

    @Override
    public boolean isOneShot() {
        return true;
    }
}
