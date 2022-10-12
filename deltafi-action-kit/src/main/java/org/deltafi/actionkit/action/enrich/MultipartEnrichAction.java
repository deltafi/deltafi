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
package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.Enrichment;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Base class for a ENRICH action that will handle multi-part content on input and needs to extend
 * ActionParameters for configuration
 *
 * @see SimpleMultipartEnrichAction
 * @see SimpleEnrichAction
 * @see EnrichAction
 */
public abstract class MultipartEnrichAction<P extends ActionParameters> extends EnrichActionBase<P> {
    public MultipartEnrichAction(Class<P> actionParametersClass, String description) {
        super(actionParametersClass, description);
    }

    @Override
    protected final EnrichResultType execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return enrich(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerContent(),
                deltaFile.getLastProtocolLayerMetadataAsMap(),
                deltaFile.domainMap(),
                deltaFile.enrichmentMap());
    }

    /**
     * Implements the enrichment execution function of an enrich action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param sourceInfo The source info for this action execution
     * @param contentList The content to be enriched by this action
     * @param metadata The metadata for this enrich action
     * @param domains A map of domain names with their associated domain values for this action
     * @param enrichment A map of enrichment names with their associated domain values for this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a EnrichResult
     * @see EnrichResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract EnrichResultType enrich(@NotNull ActionContext context,
                                            @NotNull P params,
                                            @NotNull SourceInfo sourceInfo,
                                            @NotNull List<Content> contentList,
                                            @NotNull Map<String, String> metadata,
                                            @NotNull Map<String, Domain> domains,
                                            @NotNull Map<String, Enrichment> enrichment);
}
