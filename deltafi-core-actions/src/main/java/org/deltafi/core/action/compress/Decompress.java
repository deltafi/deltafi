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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
@Slf4j
public class Decompress extends TransformAction<DecompressParameters> {
    private record DetectedCompressData(CompressType compressType, CompressorInputStream compressorInputStream) {}

    public Decompress() {
        super("Decompresses each supplied content from .gz, .xz, or .Z");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull DecompressParameters params,
            @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        CompressType compressType = params.getCompressType();
        for (ActionContent actionContent : input.content()) {
            InputStream contentInputStream = actionContent.loadInputStream();
            try {
                if (compressType == null) {
                    DetectedCompressData detectedCompressData = detectCompressType(contentInputStream);
                    decompress(actionContent, detectedCompressData.compressorInputStream, result);
                    compressType = detectedCompressData.compressType;
                } else {
                    decompress(actionContent, createCompressorInputStream(compressType, contentInputStream), result);
                }
            } catch (CompressorException | IOException e) {
                return new ErrorResult(context, "Unable to decompress content", e).logErrorTo(log);
            }
        }
        result.addMetadata("compressType", compressType.getValue());

        return result;
    }

    private DetectedCompressData detectCompressType(InputStream contentInputStream) throws CompressorException {
        // Wrap in a BufferedInputStream (supporting mark/reset) so the compressor type can be detected.
        BufferedInputStream bufferedContentInputStream = new BufferedInputStream(contentInputStream);
        try {
            CompressorInputStream compressorInputStream =
                    CompressorStreamFactory.getSingleton().createCompressorInputStream(bufferedContentInputStream,
                            Set.of(CompressorStreamFactory.GZIP, CompressorStreamFactory.XZ, CompressorStreamFactory.Z));
            if (compressorInputStream instanceof GzipCompressorInputStream) {
                return new DetectedCompressData(CompressType.GZIP, compressorInputStream);
            }
            if (compressorInputStream instanceof XZCompressorInputStream) {
                return new DetectedCompressData(CompressType.XZ, compressorInputStream);
            }
            return new DetectedCompressData(CompressType.Z, compressorInputStream);
        } catch (CompressorException e) {
            try {
                bufferedContentInputStream.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            throw e;
        }
    }

    private CompressorInputStream createCompressorInputStream(CompressType compressType, InputStream contentInputStream)
            throws IOException {
        return switch (compressType) {
            case GZIP -> new GzipCompressorInputStream(contentInputStream);
            case XZ -> new XZCompressorInputStream(contentInputStream);
            case Z -> new ZCompressorInputStream(contentInputStream);
        };
    }

    private void decompress(ActionContent content, CompressorInputStream compressorInputStream,
            TransformResult transformResult) {
        try (compressorInputStream) {
            transformResult.saveContent(compressorInputStream, stripSuffix(content.getName()),
                    MediaType.APPLICATION_OCTET_STREAM);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String stripSuffix(String filename) {
        int suffixIndex = filename.lastIndexOf('.');
        return suffixIndex == -1 ? filename : filename.substring(0, suffixIndex);
    }
}
