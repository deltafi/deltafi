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
package org.deltafi.core.action.compress;

import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.content.ActionContentWriter;

import java.io.IOException;
import java.io.OutputStream;

@RequiredArgsConstructor
public class CompressWriter extends ActionContentWriter {
    private static final GzipParameters GZIP_PARAMETERS;
    static {
        GZIP_PARAMETERS = new GzipParameters();
        GZIP_PARAMETERS.setOperatingSystem(3); // 3=Unix
    }

    private final ActionContent actionContent;
    private final Format format;

    @Override
    public void write(OutputStream outputStream) throws IOException {
        CompressorOutputStream compressorOutputStream = switch (format) {
            case GZIP -> new GzipCompressorOutputStream(outputStream, GZIP_PARAMETERS);
            case XZ -> new XZCompressorOutputStream(outputStream);
            default -> throw new UnsupportedOperationException("Compress format not supported: " + format);
        };
        try (compressorOutputStream) {
            writeContent(actionContent, compressorOutputStream);
        }
    }
}
