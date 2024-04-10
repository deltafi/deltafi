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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.io.WriterPipedInputStream;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.action.merge.MergeContentWriter;
import org.deltafi.core.parameters.ArchiveContentParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class ArchiveContent extends TransformAction<ArchiveContentParameters> {
    private static final String MERGED_FILE_NAME = "merged";

    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    public ArchiveContent() {
        super("Merges a list of content to a single content using binary concatenation, TAR, ZIP, AR, TAR.GZ, or TAR.XZ");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ArchiveContentParameters params,
                                         @NotNull TransformInput input) {
        String fileName = MERGED_FILE_NAME + (params.getArchiveType() == null ?
                "" : ("." + params.getArchiveType().getValue()));
        String mediaType = params.getArchiveType() == null ?
                MediaType.APPLICATION_OCTET_STREAM : params.getArchiveType().getMediaType();

        try (WriterPipedInputStream writerPipedInputStream = new WriterPipedInputStream()) {
            writerPipedInputStream.runPipeWriter(new MergeContentWriter(input.getContent(), params.getArchiveType()),
                    executorService);
            TransformResult transformResult = new TransformResult(context);
            transformResult.saveContent(writerPipedInputStream, fileName, mediaType);
            return transformResult;
        } catch (IOException e) {
            return new ErrorResult(context, "Unable to write merged content", e);
        }
    }
}
