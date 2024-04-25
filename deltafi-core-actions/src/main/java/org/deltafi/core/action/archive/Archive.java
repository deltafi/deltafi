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
package org.deltafi.core.action.archive;

import lombok.extern.slf4j.Slf4j;
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
public class Archive extends TransformAction<ArchiveParameters> {
    private static final String ARCHIVE_FILE_NAME = "archive";

    private final Clock clock;

    public Archive(Clock clock) {
        // Note .tar.Z (traditional Unix compress) is not supported by org.apache.commons.compress
        super("Archives content to .ar, .tar, .tar.gz, .tar.xz, or .zip");

        this.clock = clock;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ArchiveParameters params,
            @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        String fileName = ARCHIVE_FILE_NAME + (params.getAppendSuffix() ? ("." + params.getArchiveType().getValue()) : "");
        result.saveContent(new ArchiveWriter(input.content(), params.getArchiveType(), clock), fileName,
                params.getMediaType() != null ? params.getMediaType() : params.getArchiveType().getMediaType());
        result.addMetadata(input.getMetadata());
        result.addMetadata("archiveType", params.getArchiveType().getValue());
        return result;
    }
}
