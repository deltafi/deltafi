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
package org.deltafi.core.action.hash;

// ABOUTME: Computes cryptographic hashes of content and stores them in metadata.
// ABOUTME: Supports MD5, SHA-1, SHA-256, SHA-384, and SHA-512 algorithms.

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.core.action.ContentMatchingParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class HashContent extends TransformAction<HashContentParameters> {

    public HashContent() {
        super(ActionOptions.builder()
                .description("Computes cryptographic hashes of content and stores them in metadata.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary(ContentMatchingParameters.CONTENT_SELECTION_DESCRIPTION)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("Content is passed through unchanged.")
                        .metadataDescriptions(List.of(
                                ActionOptions.KeyedDescription.builder()
                                        .key("hash (or hash.0, hash.1, etc.)")
                                        .description("The computed hash value in lowercase hexadecimal")
                                        .build()))
                        .build())
                .errors("On unsupported hash algorithm", "On failure to compute hash")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull HashContentParameters params,
            @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(params.getAlgorithm().getAlgorithmName());
        } catch (NoSuchAlgorithmException e) {
            return new ErrorResult(context, "Unsupported hash algorithm: " + params.getAlgorithm(), e);
        }

        int matchedCount = 0;
        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.getContent().get(i);
            result.addContent(content);

            if (!params.contentSelected(i, content)) {
                continue;
            }

            try {
                byte[] hash = digest.digest(content.loadBytes());
                String hashHex = HexFormat.of().formatHex(hash);

                String key = computeMetadataKey(params.getMetadataKey(), matchedCount, input.getContent().size());
                result.addMetadata(key, hashHex);
                matchedCount++;

                digest.reset();
            } catch (Exception e) {
                return new ErrorResult(context, "Failed to compute hash for content at index " + i, e);
            }
        }

        return result;
    }

    private String computeMetadataKey(String baseKey, int matchIndex, int totalContent) {
        // If there's only one content piece total, use the base key without suffix
        if (totalContent == 1) {
            return baseKey;
        }
        // Otherwise, append the matched index
        return baseKey + "." + matchIndex;
    }
}
