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
package org.deltafi.core.action.replace;

// ABOUTME: Replaces text in content using literal or regex matching.
// ABOUTME: Supports capture groups for regex replacements.

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.ContentMatchingParameters;
import org.deltafi.core.action.ContentSelectingTransformAction;
import org.deltafi.core.action.ContentSelectionParameters;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReplaceText extends ContentSelectingTransformAction<ReplaceTextParameters> {

    public ReplaceText() {
        super(ActionOptions.builder()
                .description("Replaces text in content using literal or regex matching.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ContentMatchingParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Replaces text in each selected content. By default, replaces all occurrences
                                of the search value. Set replaceFirst to true to replace only the first occurrence.

                                When regex is true, the searchValue is treated as a Java regular expression
                                and the replacement can use $1, $2, etc. to reference capture groups.

                                """ + ContentSelectionParameters.CONTENT_RETENTION_DESCRIPTION)
                        .build())
                .errors("On invalid regex pattern", "On failure to process any content")
                .build());
    }

    @Override
    protected ActionContent transform(ActionContext context, ReplaceTextParameters params, ActionContent content) {
        String text = content.loadString();
        String result;

        if (params.isRegex()) {
            Pattern pattern = Pattern.compile(params.getSearchValue());
            Matcher matcher = pattern.matcher(text);
            result = params.isReplaceFirst()
                    ? matcher.replaceFirst(params.getReplacement())
                    : matcher.replaceAll(params.getReplacement());
        } else {
            if (params.isReplaceFirst()) {
                int index = text.indexOf(params.getSearchValue());
                if (index >= 0) {
                    result = text.substring(0, index) + params.getReplacement() +
                            text.substring(index + params.getSearchValue().length());
                } else {
                    result = text;
                }
            } else {
                result = text.replace(params.getSearchValue(), params.getReplacement());
            }
        }

        return ActionContent.saveContent(context, result, content.getName(), content.getMediaType());
    }
}
