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
package org.deltafi.core.action.base64;

// ABOUTME: Decodes Base64-encoded content.
// ABOUTME: Supports standard and URL-safe encoding variants.

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.ContentMatchingParameters;
import org.deltafi.core.action.ContentSelectingTransformAction;
import org.deltafi.core.action.ContentSelectionParameters;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class Base64Decode extends ContentSelectingTransformAction<Base64DecodeParameters> {

    public Base64Decode() {
        super(ActionOptions.builder()
                .description("Decodes Base64-encoded content.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ContentMatchingParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Decodes each selected content from Base64. If the content name ends with
                                '.b64', that suffix is removed; otherwise the name is unchanged.

                                """ + ContentSelectionParameters.CONTENT_RETENTION_DESCRIPTION)
                        .build())
                .errors("On invalid Base64 content", "On failure to decode any content")
                .build());
    }

    @Override
    protected ActionContent transform(ActionContext context, Base64DecodeParameters params, ActionContent content) {
        Base64.Decoder decoder = params.isUrlSafe()
                ? Base64.getUrlDecoder()
                : Base64.getMimeDecoder();

        byte[] decoded = decoder.decode(content.loadBytes());
        String newName = content.getName().endsWith(".b64")
                ? content.getName().substring(0, content.getName().length() - 4)
                : content.getName();

        return ActionContent.saveContent(context, decoded, newName, params.getOutputMediaType());
    }
}
