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
package org.deltafi.core.action.merge;

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.format.FormatAction;
import org.deltafi.actionkit.action.format.FormatInput;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.FormatResultType;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

@Component
public class MergeContentFormatAction extends FormatAction<MergeContentParameters> {
    private static final String MERGED_FILE_NAME = "merged";

    public MergeContentFormatAction() {
        super("Merges a list of content to a single content using binary concatenation, TAR, ZIP, AR, TAR.GZ, or TAR.XZ");
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }

    @Override
    public FormatResultType format(@NotNull ActionContext context, @NotNull MergeContentParameters params,
            @NotNull FormatInput input) {
        String fileName = MERGED_FILE_NAME + (params.getArchiveType() == null ?
                "" : ("." + params.getArchiveType().getValue()));
        String mediaType = params.getArchiveType() == null ?
                MediaType.APPLICATION_OCTET_STREAM : params.getArchiveType().getMediaType();

        try (MergeContentInputStream mergeContentInputStream = new MergeContentInputStream(input.getContent(),
                params.getArchiveType())) {
            FormatResult formatResult = new FormatResult(context, mergeContentInputStream, fileName, mediaType);
            formatResult.addMetadata(input.getMetadata());
            return formatResult;
        } catch (IOException e) {
            return new ErrorResult(context, "Unable to write merged content", e);
        }
    }
}
