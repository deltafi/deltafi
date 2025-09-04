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
package org.deltafi.core.action.compress;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
@Slf4j
public class Compress extends TransformAction<CompressParameters> {
    private final Clock clock;

    public Compress(Clock clock) {
        // Note .tar.Z and .Z (traditional Unix compress) are not supported by org.apache.commons.compress
        super(ActionOptions.builder()
                .description("Compresses content using ar, gz, tar, tar.gz, tar.xz, xz, or zip.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                If the format is ar, tar, tar.bz2, tar.gz, tar.xz, or zip, all content is
                                compressed to a single content. The name of the content will be set from
                                the name parameter and include the appropriate suffix.
                                
                                If the format is bz2, gz or xz, all content is compressed individually. Each
                                content will keep its name but will include the appropriate suffix.""")
                        .metadataDescriptions(List.of(ActionOptions.KeyedDescription.builder()
                                .key("compressFormat")
                                .description("The format used to compress")
                                .build()))
                        .build())
                .errors("On no input content")
                .build());

        this.clock = clock;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull CompressParameters params,
            @NotNull TransformInput input) {
        if (input.getContent().isEmpty()) {
            return new ErrorResult(context, "No content found");
        }

        return switch (params.getFormat()) {
            case AR, TAR, TAR_BZIP2, TAR_GZIP, TAR_XZ, ZIP -> archive(context, params, input);
            case BZIP2, GZIP, XZ -> compress(context, params, input);
            default -> throw new UnsupportedOperationException("Format not supported: " + params.getFormat());
        };
    }

    private TransformResultType archive(ActionContext context, CompressParameters params, TransformInput input) {
        TransformResult result = new TransformResult(context);
        String fileName = String.join(".", params.getName(), params.getFormat().getValue());
        result.saveContent(new ArchiveWriter(input.content(), params.getFormat(), clock), fileName,
                params.getMediaType() != null ? params.getMediaType() : params.getFormat().getMediaType());
        result.addMetadata("compressFormat", params.getFormat().getValue());
        return result;
    }

    private TransformResultType compress(ActionContext context, CompressParameters params, TransformInput input) {
        TransformResult result = new TransformResult(context);
        for (ActionContent inputContent : input.content()) {
            String fileName = inputContent.getName() + "." + params.getFormat().getValue();
            result.saveContent(new CompressWriter(inputContent, params.getFormat()), fileName,
                    params.getMediaType() != null ? params.getMediaType() : params.getFormat().getMediaType());
        }
        result.addMetadata("compressFormat", params.getFormat().getValue());
        return result;
    }
}
