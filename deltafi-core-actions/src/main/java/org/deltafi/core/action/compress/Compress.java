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
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class Compress extends TransformAction<CompressParameters> {
    public Compress() {
        // Note .Z (traditional Unix compress) is not supported by org.apache.commons.compress
        super("Compresses each supplied content to .gz, or .xz");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull CompressParameters params,
            @NotNull TransformInput input) {
        List<TransformResult> results = new ArrayList<>();

        for (ActionContent inputContent : input.content()) {
            TransformResult result = new TransformResult(context);
            String fileName = inputContent.getName() + "." + params.getCompressType().getValue();
            result.saveContent(new CompressWriter(inputContent, params.getCompressType()), fileName,
                    params.getMediaType() != null ? params.getMediaType() : params.getCompressType().getMediaType());
            result.addMetadata(input.getMetadata());
            result.addMetadata("compressType", params.getCompressType().getValue());
            results.add(result);
        }

        if (results.isEmpty()) {
            throw new CompressException("No content found");
        }

        if (results.size() == 1) {
            return results.getFirst();
        }

        TransformResults transformResults = new TransformResults(context);
        results.forEach(transformResults::add);
        return transformResults;
    }
}
