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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@RequiredArgsConstructor
public class InputStreamRequestBody extends RequestBody {

    private final EgressInput input;

    @Override
    public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
        if (input.getContent() == null) {
            // Write zero data when content is null
            return;
        }
        try (Source source = Okio.source(input.getContent().loadInputStream())) {
            bufferedSink.writeAll(source);
        }
    }

    @Nullable
    @Override
    public MediaType contentType() {
        if (input.getContent() == null) {
            return null;
        }
        return MediaType.parse(input.getContent().getMediaType());
    }

    @Override
    public long contentLength() {
        if (input.getContent() == null) {
            return 0;
        }
        return input.getContent().getSize();
    }

    @Override
    public boolean isOneShot() {
        return true;
    }
}
