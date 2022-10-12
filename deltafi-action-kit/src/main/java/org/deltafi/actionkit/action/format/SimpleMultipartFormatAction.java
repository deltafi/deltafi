/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Base class for a FORMAT action that will process multi-part content, but does not need to extend
 * ActionParameters for configuration
 *
 * @see SimpleFormatAction
 * @see MultipartFormatAction
 * @see FormatAction
 */
@SuppressWarnings("unused")
public abstract class SimpleMultipartFormatAction extends MultipartFormatAction<ActionParameters> {
    public SimpleMultipartFormatAction(String description) {
        super(ActionParameters.class, description);
    }

    @Override
    public final FormatResultType format(@NotNull ActionContext context,
                                         @NotNull ActionParameters params,
                                         @NotNull SourceInfo sourceInfo,
                                         @NotNull List<Content> contentList,
                                         @NotNull Map<String, String> metadata,
                                         @NotNull Map<String, Domain> domains,
                                         @NotNull Map<String, Enrichment> enrichment) {
        return format(context, sourceInfo, contentList, metadata, domains, enrichment);
    }

    /**
     * Implements the format execution function of a format action
     * @param context The action configuration context object for this action execution
     * @param sourceInfo The source info for this action execution
     * @param contentList The list of data objects to be formatted by this action
     * @param metadata The metadata for this format action
     * @param domains A map of domain names with their associated domain values for this action
     * @param enrichment A map of enrichment names with their associated domain values for this action
     * @return A result object containing results for the action execution.
     *         The result can be an ErrorResult, FilterResult, FormatResult, or FormatManyResult
     * @see FormatResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see FormatManyResult
     */
    public abstract FormatResultType format(@NotNull ActionContext context,
                                            @NotNull SourceInfo sourceInfo,
                                            @NotNull List<Content> contentList,
                                            @NotNull Map<String, String> metadata,
                                            @NotNull Map<String, Domain> domains,
                                            @NotNull Map<String, Enrichment> enrichment);
}
