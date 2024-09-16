/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.delete;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.springframework.stereotype.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Deletes content.
 * <p>
 * Iterates through all content and applies filters which
 * are passed in as parameters, in order to allow (keep) or
 * prohibit (delete) content. There are 3 delete criteria:
 * index, file name, and media type. Criteria are evaluated
 * in a precise order; first deleteAllContent, then the index
 * list, following by file pattern list, and lastly media type
 * list. Within each filter category, only one of the allowed or
 * prohibited lists will be searched. If both are set, only the
 * allowed list will be used. A content that is acceptable in one
 * allowed list, may still be deleted by another criteria.
 */

@Component
public class DeleteContent extends TransformAction<DeleteContentParameters> {

    public DeleteContent() {
        super("Deletes content");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull DeleteContentParameters params, @NotNull TransformInput input) {

        TransformResult result = new TransformResult(context);

        if (params.deleteAllContent) {
            return result;
        }

        for (Integer i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.getContent().get(i);
            if (keepContent(params, i, content)) {
                result.addContent(content);
            }
        }
        return result;
    }

    private boolean keepContent(DeleteContentParameters params, Integer i, ActionContent content) {
        return checkKeepByIndex(params, i)
                && checkKeepByFilename(params, content.getName())
                && checkKeepByMediaType(params, content.getMediaType());
    }

    protected boolean matchesPattern(final String value, final String pattern) {
        // replaces * with .* to make it "regex-able"
        String regexPattern = pattern.replace("*", ".*");
        return value.matches(regexPattern);
    }

    protected boolean checkKeepByIndex(DeleteContentParameters params, Integer i) {
        if (!params.allowedIndexes.isEmpty()) {
            return params.allowedIndexes.contains(i);
        }

        if (!params.prohibitedIndexes.isEmpty()) {
            return !params.prohibitedIndexes.contains(i);
        }

        // If both lists are empty, proceed to next check
        return true;
    }

    protected boolean checkKeepByFilename(DeleteContentParameters params, String name) {
        if (!params.allowedFilePatterns.isEmpty()) {
            return params.allowedFilePatterns.stream()
                    .anyMatch(pattern -> matchesPattern(name, pattern));
        }

        if (!params.prohibitedFilePatterns.isEmpty()) {
            return params.prohibitedFilePatterns.stream()
                    .noneMatch(pattern -> matchesPattern(name, pattern));
        }

        // If both lists are empty, proceed to next check
        return true;
    }

    protected boolean checkKeepByMediaType(DeleteContentParameters params, String mediaType) {
        if (!params.allowedMediaTypes.isEmpty()) {
            return params.allowedMediaTypes.stream()
                    .anyMatch(pattern -> matchesPattern(mediaType, pattern));
        }

        if (!params.prohibitedMediaTypes.isEmpty()) {
            return params.prohibitedMediaTypes.stream()
                    .noneMatch(pattern -> matchesPattern(mediaType, pattern));
        }

        return true;
    }

}

