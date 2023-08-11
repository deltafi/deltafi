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

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.DeleteMetadataTransformParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class DetectMediaTypeTransformAction extends TransformAction<ActionParameters> {

    TikaConfig tika = new TikaConfig();

    public DetectMediaTypeTransformAction() throws TikaException, IOException {
        super("Detect and set mediaType for each content, using Tika. In the case of detection errors, the existing mediaType is retained.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull TransformInput transformInput) {
        TransformResult result = new TransformResult(context);
        transformInput.content().stream()
                .map(content -> {
                    ActionContent newContent = content.copy();
                    Metadata metadata = new Metadata();
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, newContent.getName());
                    try {
                        String mediaType = tika.getDetector().detect(
                                TikaInputStream.get(newContent.loadInputStream()), metadata).toString();
                        newContent.setMediaType(mediaType);
                    } catch (IOException ignored) {
                        // do not set the mediaType if there is an exception, but continue processing
                    }
                    return newContent;
                })
                .forEach(result::addContent);
        return result;
    }
}
