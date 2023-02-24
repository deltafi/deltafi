/**
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
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.splitter.ContentSplitter;
import org.deltafi.common.splitter.SplitException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.core.parameters.ContentSplitterParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Component
public class ContentSplitterTransformAction extends TransformAction<ContentSplitterParameters> {

    private static final String NAME_TEMPLATE = "${contentName}.${fragmentId}${ext}";
    private static final String CONTENT_NAME = "${contentName}";
    private static final String EXT = "${ext}";
    private static final String FRAGMENT_ID = "${fragmentId}";
    private final ContentSplitter contentSplitter;

    public ContentSplitterTransformAction(ContentSplitter contentSplitter) {
        super("Splits the last Content into multiple pieces of content with sub-references to the original content");
        this.contentSplitter = contentSplitter;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ContentSplitterParameters params, @NotNull TransformInput transformInput) {
        TransformResult result = new TransformResult(context);

        List<ContentReference> contentReferences;
        try {
            contentReferences = contentSplitter.splitContent(transformInput.firstContent(), params.asSplitterParams());
        } catch (SplitException splitException) {
            ErrorResult errorResult = new ErrorResult(context, splitException.getMessage(), splitException);
            errorResult.logErrorTo(log);
            return errorResult;
        }


        int lastIdx = contentReferences.size() - 1;
        IntStream.range(0, contentReferences.size())
                .mapToObj(idx -> buildContent(transformInput.firstContent().getName() , idx, contentReferences.get(idx), idx == lastIdx))
                .forEach(result::addContent);

        return result;
    }

    private Content buildContent(String originalContentName, int idx, ContentReference contentReference, boolean isLast) {
        Content content = new Content();
        content.setName(buildContentName(originalContentName, idx));
        content.setContentReference(contentReference);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("fragmentId", String.valueOf(idx));
        if (idx == 0) {
            metadata.put("firstFragment", "true");
        }

        if (isLast)  {
            metadata.put("lastFragment", "true");
        }

        content.setMetadata(metadata);
        return content;
    }

    String buildContentName(String originalName, int idx) {
        if (originalName == null) {
            return null;
        }

        if (originalName.isBlank()) {
            return String.valueOf(idx);
        }

        String template = NAME_TEMPLATE;

        String filenameReplacement = originalName;
        String extReplacement = "";

        int extIdx = originalName.lastIndexOf(".");
        if (extIdx != -1) {
            filenameReplacement = originalName.substring(0, extIdx);
            extReplacement = originalName.substring(extIdx);
        }

        String childName = template.replace(CONTENT_NAME, filenameReplacement);
        childName = childName.replace(FRAGMENT_ID, String.valueOf(idx));
        childName = childName.replace(EXT, extReplacement);

        return childName;
    }
}
