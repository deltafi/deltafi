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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@Slf4j
public class Compress extends TransformAction<CompressParameters> {
    private final Clock clock;

    public Compress(Clock clock) {
        // Note .tar.Z and .Z (traditional Unix compress) are not supported by org.apache.commons.compress
        super("Compresses content using ar, gz, tar, tar.gz, tar.xz, xz, or zip.");

        this.clock = clock;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull CompressParameters params,
            @NotNull TransformInput input) {
        if (input.getContent().isEmpty()) {
            return new ErrorResult(context, "No content found");
        }

        return switch (params.getFormat()) {
            case AR, TAR, TAR_GZIP, TAR_XZ, ZIP -> archive(context, params, input);
            case GZIP, XZ -> compress(context, params, input);
            default -> throw new UnsupportedOperationException("Format not supported: " + params.getFormat());
        };
    }

    private TransformResultType archive(ActionContext context, CompressParameters params, TransformInput input) {
        TransformResult result = new TransformResult(context);
        String fileName = String.join(".", params.getName(), params.getFormat().getValue());
        result.saveContent(new ArchiveWriter(input.content(), params.getFormat(), clock), fileName,
                params.getMediaType() != null ? params.getMediaType() : params.getFormat().getMediaType());
        result.addMetadata(input.getMetadata());
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
        result.addMetadata(input.getMetadata());
        result.addMetadata("compressFormat", params.getFormat().getValue());
        return result;
    }
}
