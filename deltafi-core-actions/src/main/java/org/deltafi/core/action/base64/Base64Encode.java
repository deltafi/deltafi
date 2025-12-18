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

// ABOUTME: Encodes content using Base64.
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
public class Base64Encode extends ContentSelectingTransformAction<Base64EncodeParameters> {

    public Base64Encode() {
        super(ActionOptions.builder()
                .description("Encodes content using Base64.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ContentMatchingParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Encodes each selected content using Base64 encoding. The encoded content
                                will have the same name as the input content with '.b64' appended.

                                """ + ContentSelectionParameters.CONTENT_RETENTION_DESCRIPTION)
                        .build())
                .errors("On failure to encode any content")
                .build());
    }

    @Override
    protected ActionContent transform(ActionContext context, Base64EncodeParameters params, ActionContent content) {
        Base64.Encoder encoder = params.isUrlSafe()
                ? Base64.getUrlEncoder()
                : Base64.getEncoder();

        byte[] encoded = encoder.encode(content.loadBytes());
        String newName = content.getName() + ".b64";

        return ActionContent.saveContent(context, encoded, newName, params.getOutputMediaType());
    }
}
